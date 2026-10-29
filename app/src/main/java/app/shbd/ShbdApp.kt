package app.shbd

import android.app.Application
import android.util.Log
import app.shbd.data.AnalysisQueueStore
import app.shbd.data.BodyFatRepository
import app.shbd.data.BodyMeasurementRepository
import app.shbd.data.ChatRepository
import app.shbd.data.FastingRepository
import app.shbd.data.FoodRepository
import app.shbd.data.KeyStore
import app.shbd.data.ManualActiveRepository
import app.shbd.data.ManualDayMetricsRepository
import app.shbd.data.CaffeineRepository
import app.shbd.data.NicotineRepository
import app.shbd.data.NotesRepository
import app.shbd.data.PreferencesStore
import app.shbd.data.ProfileRepository
import app.shbd.data.RecipeRepository
import app.shbd.data.WaterRepository
import app.shbd.data.WeatherRepository
import app.shbd.data.WeightRepository
import app.shbd.models.AIProvider
import app.shbd.models.CurrentMealCatalog
import app.shbd.models.CurrentMealSchedule
import app.shbd.models.UserProfile
import app.shbd.services.AdaptiveGoalResult
import app.shbd.services.AdaptiveGoalsService
import app.shbd.services.FastingAutoPlanner
import app.shbd.services.FastingGoalPlanner
import app.shbd.services.FastingReminderPlanner
import app.shbd.services.FillGapsEngine
import app.shbd.services.FoodImageStore
import app.shbd.services.FoodPhotoSession
import app.shbd.services.GoalJournalService
import app.shbd.services.LauncherShortcuts
import app.shbd.services.NotificationService
import app.shbd.services.PerfLog
import app.shbd.services.ShortcutEntryAction
import app.shbd.services.TestDataSeeder
import app.shbd.services.WaterReminderPlanner
import app.shbd.services.WidgetSnapshotWriter
import app.shbd.services.ai.ChatService
import app.shbd.services.ai.FoodAnalysisService
import app.shbd.services.grounding.FoodDatabaseSearch
import app.shbd.services.grounding.GroundedFoodEntryService
import app.shbd.services.grounding.GroundedEntryFeature
import app.shbd.services.grounding.SwissFoodIndex
import app.shbd.services.grounding.UsdaFoodIndex
import app.shbd.services.health.HealthConnectManager
import app.shbd.services.health.HealthConnectReadSync
import app.shbd.services.health.HealthSyncWorker
import app.shbd.services.health.HomeActivityReader
import app.shbd.sync.SyncRepository
import app.shbd.services.ondevice.AiAvailability
import app.shbd.services.ondevice.ModelCatalog
import app.shbd.services.ondevice.ModelDownloadManager
import app.shbd.services.ondevice.OnDeviceCapability
import app.shbd.services.ondevice.OnDeviceLlmGateway
import app.shbd.services.ondevice.aiAvailability
import app.shbd.services.ondevice.remoteConfigured
import app.shbd.services.speech.SpeechService
import app.shbd.services.weather.OpenMeteoClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Application-scoped singleton wiring. Manual DI (no Hilt) — repositories and
 * services are instantiated once and handed to ViewModels via [container].
 */
class ShbdApp : Application() {
    lateinit var container: AppContainer
        private set

    /**
     * Process-lifetime scope for work that must outlive a disposed Compose tree.
     * Startup work (migrations, observers, reminder re-arming) must never take
     * the process down: an uncaught exception here would otherwise crash on
     * every launch until the user clears app data — and their diary with it
     * (upstream fud-ai c278c64d).
     */
    val applicationScope: CoroutineScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
                Log.e("ShbdApp", "Background task failed", throwable)
            }
        )

    private val appScope get() = applicationScope

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifications.createChannels()
        LauncherShortcuts.publish(this)
        appScope.launch { container.prefs.migrateHomeDisplayLayoutIfNeeded() }
        appScope.launch { container.prefs.migrateCaffeineDailyLimitIfNeeded() }
        appScope.launch {
            container.prefs.ensureAiProfilesMigrated(container.keyStore, this@ShbdApp)
            if (container.prefs.migrateToEmbeddedAi()) {
                container.onDeviceModelDownloadManager.deleteRetiredModels()
            }
            container.prefs.cleanUpAiProfiles(container.keyStore)
            container.prefs.seedDefaultAiProfiles(
                localName = getString(R.string.ai_profile_default_local),
                remoteName = getString(R.string.ai_profile_default_remote),
            )
        }
        appScope.launch { container.prefs.disableMealPlanningOnce() }
        container.prefs.mealCatalog
            .onEach {
                CurrentMealCatalog.value = it
                CurrentMealSchedule.value = it.toLegacySchedule()
            }
            .launchIn(appScope)
        // Widget snapshot writes the same DataStore file Home is reading on
        // first paint — wait until the first frame is out before observing.
        container.widgetSnapshotWriter.observe()
            .onStart { delay(WIDGET_OBSERVE_DELAY_MS) }
            .launchIn(appScope)
        // Goal journal (#60): gap-fill + record today's resolved targets on
        // every plan edit and at app start (first profile emission). Writes
        // only while the day-type plan is enabled.
        container.goalJournalService.observe()
            .launchIn(appScope)
        scheduleDeferredStartupWork()
    }

    /**
     * Prune / weather / reminder re-arm / midnight widget alarm. None of this
     * is needed for the first Home frame; running it in onCreate contended
     * with splash DataStore reads on a full diary.
     */
    private fun scheduleDeferredStartupWork() {
        appScope.launch {
            delay(DEFERRED_STARTUP_DELAY_MS)
            container.notifications.scheduleWidgetMidnightRefresh()
            if (container.prefs.weatherSource.first() == WeatherRepository.SOURCE_OPEN_METEO) {
                container.weatherRepository.refreshOpenMeteo()
            }
            container.foodRepository.pruneOrphanedImages()
            if (container.prefs.healthBackgroundSyncEnabled.first() &&
                container.health.isAvailable() &&
                container.health.isBackgroundReadAvailable() &&
                container.health.hasBackgroundRead()
            ) {
                HealthSyncWorker.schedule(this@ShbdApp)
            } else if (container.prefs.healthBackgroundSyncEnabled.first()) {
                container.prefs.setHealthBackgroundSyncEnabled(false)
                HealthSyncWorker.cancel(this@ShbdApp)
            }
            if (container.prefs.notificationsEnabled.first() &&
                container.notifications.canPostNotifications()
            ) {
                if (container.prefs.streakReminderEnabled.first()) {
                    container.notifications.scheduleStreakReminder(
                        container.prefs.streakReminderHour.first(),
                        container.prefs.streakReminderMinute.first()
                    )
                } else {
                    container.notifications.cancelStreakReminder()
                }
                if (container.prefs.dailySummaryEnabled.first()) {
                    container.notifications.scheduleDailySummary(
                        container.prefs.dailySummaryHour.first(),
                        container.prefs.dailySummaryMinute.first()
                    )
                } else {
                    container.notifications.cancelDailySummary()
                }
                if (container.prefs.weightReminderEnabled.first()) {
                    container.notifications.scheduleWeightReminder(
                        container.prefs.weightReminderHour.first(),
                        container.prefs.weightReminderMinute.first(),
                    )
                } else {
                    container.notifications.cancelWeightReminder()
                }
                val profile = container.profileRepository.current()
                if (container.prefs.bodyFatReminderEnabled.first() && profile?.bodyFatPercentage != null) {
                    container.notifications.scheduleBodyFatReminder(
                        container.prefs.bodyFatReminderHour.first(),
                        container.prefs.bodyFatReminderMinute.first(),
                    )
                } else {
                    container.notifications.cancelBodyFatReminder()
                }
                if (container.prefs.waterTrackingEnabled.first() && container.prefs.waterReminderEnabled.first()) {
                    WaterReminderPlanner.rearm(container)
                } else {
                    container.notifications.cancelWaterReminder()
                }
            }
            // Fasting goal alarm: cheap idempotent re-arm from the same session
            // fields (reboots drop alarms; app start re-arms like the water chain).
            FastingGoalPlanner.rearm(container)
            // Optional daily start-fast nudge (off by default) — same arm-or-cancel.
            FastingReminderPlanner.rearmStartReminder(container)
            // Auto-cycle transition alarms (off by default).
            FastingAutoPlanner.rearm(container)
            // Catch up any auto transition whose boundary passed while the app
            // was closed (missed alarm) so the cycle stays on schedule.
            FastingAutoPlanner.heal(container)
            // Re-publish launcher shortcuts with the real fasting pref (the
            // onCreate publish ran before DataStore was readable).
            LauncherShortcuts.publish(this@ShbdApp, container.prefs.fastingEnabled.first())
        }
    }

    private companion object {
        const val WIDGET_OBSERVE_DELAY_MS = 2_000L
        const val DEFERRED_STARTUP_DELAY_MS = 2_500L
    }
}

class AppContainer(app: ShbdApp) {
    val appContext = app.applicationContext
    val prefs = PreferencesStore(app)
    val keyStore: KeyStore by lazy(LazyThreadSafetyMode.NONE) {
        // Measure-only this pass: eager init is forced by the four keyStore
        // ctor consumers below (the lazy never wins). If the cold-start ×5
        // capture shows ≥50 ms, provider-threading through those ctors is a
        // separate follow-up.
        PerfLog.measure("coldStart", "keyStoreInit") { KeyStore(app) }
    }
    val imageStore = FoodImageStore(app)
    /** Analysis queue + prompt history (Codeberg #53): run failed/staged prompts later. */
    val analysisQueue = AnalysisQueueStore(app)
    val notifications = NotificationService(app)
    val health = HealthConnectManager(app)
    val homeActivityReader = HomeActivityReader(health, prefs)

    val syncRepository = SyncRepository(prefs, keyStore, appContext = appContext)
    val profileRepository = ProfileRepository(prefs)
    val goalJournalService = GoalJournalService(prefs, profileRepository)
    val foodRepository = FoodRepository(prefs, health, imageStore, syncRepository)
    val recipeRepository = RecipeRepository(prefs, foodRepository, syncRepository)
    val weightRepository = WeightRepository(prefs, profileRepository, health, syncRepository)
    val bodyFatRepository = BodyFatRepository(prefs, profileRepository, health, syncRepository)
    val bodyMeasurementRepository = BodyMeasurementRepository(prefs, syncRepository)
    val chatRepository = ChatRepository(prefs)
    val waterRepository = WaterRepository(prefs, health, syncRepository).apply {
        // Re-arm the adaptive reminder chain after every water entry so the next
        // reminder reflects the new pace immediately (issue #3). `app.container`
        // is lateinit but always assigned before any entry can be added.
        onEntriesChanged = { WaterReminderPlanner.rearm(app.container) }
    }
    val manualActiveRepository = ManualActiveRepository(prefs)
    val manualDayMetricsRepository = ManualDayMetricsRepository(prefs)
    val nicotineRepository = NicotineRepository(prefs, syncRepository)
    val caffeineRepository = CaffeineRepository(prefs, syncRepository)
    val notesRepository = NotesRepository(prefs, syncRepository)
    val fastingRepository = FastingRepository(prefs).apply {
        // Re-arm the one-shot goal alarm after every session change so the
        // armed fire time always matches the running fast + goal (start → arm,
        // stop/cancel → cancel). `app.container` is lateinit but always
        // assigned before any session change can happen.
        onSessionChanged = {
            FastingGoalPlanner.rearm(app.container)
            FastingReminderPlanner.rearmStartReminder(app.container)
            FastingAutoPlanner.rearm(app.container)
            FastingAutoPlanner.heal(app.container)
        }
    }

    // Weather input for the dynamic water goal (issue #3 Phase 5): shared
    // weather-app broadcast cache + Open-Meteo city forecast, with the manual
    // °C as fallback. Uses the shared OkHttp client (no extra dependencies).
    val openMeteo = OpenMeteoClient(FoodAnalysisService.defaultClient)
    val weatherRepository = WeatherRepository(prefs, openMeteo)

    val onDeviceLlmGateway = OnDeviceLlmGateway(appContext, prefs)
    val onDeviceModelDownloadManager = ModelDownloadManager(appContext)
    /** The device can run the embedded model (ABI + RAM floor); fixed for the process. */
    val onDeviceSupported: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        OnDeviceCapability.isSupported(appContext)
    }
    /** Live [AiAvailability] of the active model: switch, active profile + its key, embedded model file. */
    val aiAvailability: Flow<AiAvailability>
        get() = combine(
            prefs.aiFeaturesEnabled,
            prefs.activeAiProfile,
            keyStore.changes,
            onDeviceModelDownloadManager.state(ModelCatalog.default.modelId),
        ) { enabled, profile, _, state ->
            val provider = profile?.provider ?: AIProvider.GEMINI
            val configured = profile != null && remoteConfigured(provider, keyStore.profileApiKey(profile.id), profile.baseUrl)
            aiAvailability(enabled, provider, configured, state)
        }
    val foodAnalysis = FoodAnalysisService(
        prefs,
        keyStore,
        onDeviceGateway = onDeviceLlmGateway,
        onDeviceModelDownloaded = { modelId ->
            onDeviceModelDownloadManager.isDownloaded(ModelCatalog.forModelId(modelId))
        },
    )
    /** AI fill-missing scan / banner / mass-fill session (Batch 2). */
    val fillGapsEngine = FillGapsEngine(foodRepository, prefs, foodAnalysis, appContext)
    /**
     * Offline food-database indexes + the gated grounded orchestrator.
     * USDA (CC0) and Swiss (federal open data) SQLite assets ship in all build
     * types and power the Add Food "Search food" sheet. [groundedFoodEntry]
     * stays gated behind [GroundedEntryFeature.ENABLED] (false in shipping builds).
     */
    val usdaFoodIndex: UsdaFoodIndex by lazy(LazyThreadSafetyMode.NONE) {
        check(UsdaFoodIndex.assetAvailable(appContext)) {
            "USDA SQLite missing from APK assets"
        }
        UsdaFoodIndex(appContext)
    }
    val swissFoodIndex: SwissFoodIndex by lazy(LazyThreadSafetyMode.NONE) {
        check(SwissFoodIndex.assetAvailable(appContext)) {
            "Swiss food SQLite missing from APK assets"
        }
        SwissFoodIndex(appContext)
    }
    val foodDatabaseSearch: FoodDatabaseSearch by lazy(LazyThreadSafetyMode.NONE) {
        FoodDatabaseSearch(prefs, usdaFoodIndex, swissFoodIndex)
    }
    val groundedFoodEntry: GroundedFoodEntryService by lazy(LazyThreadSafetyMode.NONE) {
        check(GroundedEntryFeature.ENABLED) {
            "GroundedFoodEntryService is disabled (GroundedEntryFeature.ENABLED=false)"
        }
        GroundedFoodEntryService(
            foodAnalysis = foodAnalysis,
            foodRepository = foodRepository,
            prefs = prefs,
            usdaIndex = usdaFoodIndex,
        )
    }
    val chatService = ChatService(prefs, keyStore, foodAnalysis, fastingRepository = fastingRepository, onDeviceGateway = onDeviceLlmGateway)
    val speechService = SpeechService(prefs, keyStore)

    val widgetSnapshotWriter = WidgetSnapshotWriter(app, prefs, foodRepository, profileRepository, homeActivityReader, waterRepository, weatherRepository, caffeineRepository)
    val testDataSeeder = TestDataSeeder(this)

    private val healthConnectReadSync = HealthConnectReadSync(
        prefs = prefs,
        health = health,
        foodRepository = foodRepository,
        weightRepository = weightRepository,
        bodyFatRepository = bodyFatRepository,
        waterRepository = waterRepository,
    )
    private val adaptiveGoals = AdaptiveGoalsService(
        prefs = prefs,
        health = health,
        profileRepository = profileRepository,
        foodRepository = foodRepository,
        weightRepository = weightRepository,
        strings = appContext::getString,
    )

    /**
     * App-scoped flag set by [HomeViewModel] while a food analysis request is
     * in flight. The bottom nav reads this so the bar can hide during the
     * AnalyzingOverlay (matches iOS, where the analyzing sheet covers the
     * tab bar).
     */
    val analyzingFood: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /**
     * Photos from the system share sheet only (ACTION_SEND / ACTION_SEND_MULTIPLE).
     * [MainActivity] fills it; a resumed Home merges into [foodPhotoSession].
     * Do **not** write in-app gallery picks here — that caused NavHost/Home races
     * and “gallery returns to bare Home” bugs.
     */
    val sharedImageInbox: MutableStateFlow<List<ByteArray>> = MutableStateFlow(emptyList())

    /**
     * In-app camera / gallery staging + multi-photo review sheet state.
     * Activity-registered Photo Picker writes here; share-ins merge via Home.
     */
    val foodPhotoSession: FoodPhotoSession = FoodPhotoSession()

    /**
     * Launcher shortcut destination for Home (Camera / Voice / Barcode).
     * [MainActivity] fills it; Home opens the matching entry UI and clears it
     * only when that UI dismisses. Sticky like [sharedImageInbox] so delivery
     * survives onboarding and Compose remounts (theme / palette refresh).
     */
    val shortcutEntryInbox: MutableStateFlow<ShortcutEntryAction?> = MutableStateFlow(null)

    /**
     * Notification open destination (Codeberg #27): set by [MainActivity] when a
     * `shbd://go/<dest>` tap lands; the nav host navigates and clears it.
     * Single-slot is enough — the newest tap wins.
     */
    val launchDestinationInbox: MutableStateFlow<String?> = MutableStateFlow(null)

    /**
     * Plan week canvas → Home handoff (meal planning mode): the day whose chip
     * was tapped. Home consumes it by selecting that day, then clears it.
     */
    val planWeekReturnDay: MutableStateFlow<java.time.LocalDate?> = MutableStateFlow(null)

    /** Debug-only: HomeViewModel runs flippidity benches through the real UI path. */
    val perfBenchInbox: MutableStateFlow<app.shbd.services.PerfBenchRequest?> =
        MutableStateFlow(null)

    /** Debug-only: HomeViewModel seeds the meal-builder sheet (Codeberg #84 repro). */
    val progressiveSeedInbox: MutableStateFlow<Int?> = MutableStateFlow(null)

    /** Debug-only: barcode injected by `debug_barcode`, consumed by HomeViewModel. */
    val barcodeLookupInbox: MutableStateFlow<String?> = MutableStateFlow(null)

    /** Debug-only: extend the saved-to-favorites snackbar to Indefinite. */
    @Volatile var debugPersistentSnackbars: Boolean = false

    /** See [HealthConnectReadSync.sync]. */
    suspend fun syncHealthConnectReads() = healthConnectReadSync.sync()

    /** See [AdaptiveGoalsService.measuredEnergyTdeeIfEnabled]. */
    suspend fun measuredEnergyTdeeIfEnabled(profile: UserProfile) =
        adaptiveGoals.measuredEnergyTdeeIfEnabled(profile)

    /** See [AdaptiveGoalsService.refreshIfNeeded]. */
    suspend fun refreshAdaptiveGoalsIfNeeded(force: Boolean = false): AdaptiveGoalResult? =
        adaptiveGoals.refreshIfNeeded(force)
}
