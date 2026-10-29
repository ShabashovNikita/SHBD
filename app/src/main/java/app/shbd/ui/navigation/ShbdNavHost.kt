package app.shbd.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.shbd.AppContainer
import app.shbd.models.EnergyUnit
import app.shbd.services.update.AndroidUpdateChecker
import app.shbd.services.update.AndroidUpdateState
import app.shbd.services.update.UpdateCheckFeature
import app.shbd.ui.about.AboutSettingsScreen
import app.shbd.ui.about.LegalSettingsScreen
import app.shbd.ui.about.SupportSettingsScreen
import app.shbd.ui.coach.CoachScreen
import app.shbd.ui.diary.CaloriePlanScreen
import app.shbd.ui.diary.DiaryDashboardScreen
import app.shbd.ui.diary.blocks.DiaryBlock
import app.shbd.ui.diary.blocks.DiaryBlockActions
import app.shbd.ui.diary.blocks.DiaryBlockScreen
import app.shbd.ui.home.PlanWeekScreen
import app.shbd.ui.onboarding.OnboardingScreen
import app.shbd.ui.progress.BodyMeasurementsScreen
import app.shbd.ui.ration.RationScreen
import app.shbd.ui.settings.AiSettingsScreen
import app.shbd.ui.settings.AppSettingsScreen
import app.shbd.ui.settings.CalculationMethodsScreen
import app.shbd.ui.settings.CoachSettingsScreen
import app.shbd.ui.settings.CustomizeProgressScreen
import app.shbd.ui.settings.GoalPhasesScreen
import app.shbd.ui.settings.DataSettingsScreen
import app.shbd.ui.settings.DayTypesSettingsScreen
import app.shbd.ui.settings.FoodEntrySettingsScreen
import app.shbd.ui.settings.GoalsSettingsScreen
import app.shbd.ui.settings.HealthDataSettingsScreen
import app.shbd.ui.settings.HomeDisplaySettingsScreen
import app.shbd.ui.settings.NutritionTargetsSettingsScreen
import app.shbd.ui.settings.NotificationsSettingsScreen
import app.shbd.ui.settings.OptionalNutrientGoalsScreen
import app.shbd.ui.settings.PersonalSettingsScreen
import app.shbd.ui.settings.SettingsScreen
import app.shbd.ui.settings.SpeechSettingsScreen
import app.shbd.ui.settings.SyncSettingsScreen
import app.shbd.ui.settings.TrackersSettingsScreen
import app.shbd.ui.settings.WaterSettingsScreen
import app.shbd.ui.settings.CaffeineSettingsScreen
import app.shbd.ui.settings.NicotineSettingsScreen
import app.shbd.ui.settings.NotesSettingsScreen
import app.shbd.ui.settings.FastingSettingsScreen

/**
 * Increments each time the app is opened: 1 on cold launch, then +1 on every
 * return from the background (ON_START after a real ON_STOP). Read by the Home
 * gauge + macro bars to replay their fill-from-zero reveal. It lives above the
 * NavHost, so tab switches (which recompose Home) never change it.
 */
val LocalLaunchFillEpoch = compositionLocalOf { 1 }

val LocalEnergyUnit = compositionLocalOf { EnergyUnit.KCAL }

@Composable
fun ShbdNavHost(
    container: AppContainer,
    startOnboarding: Boolean
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // Hide the bar while a food analysis is in flight so the AnalyzingOverlay
    // is the only thing on screen — matches iOS, where the analyzing sheet
    // covers the tab bar.
    val analyzing by container.analyzingFood.collectAsState()
    val showTabs = ShbdRoutes.showsBottomBar(currentRoute) && !analyzing
    val context = LocalContext.current
    val currentVersion = remember(context) { AndroidUpdateChecker.currentVersion(context) }
    var updateAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(currentVersion) {
        if (!UpdateCheckFeature.ENABLED) return@LaunchedEffect
        val state = AndroidUpdateChecker.check(context, currentVersion)
        updateAvailable = state is AndroidUpdateState.Available
        // A newer version is out — fire a one-shot notification (de-duped per version, gated by the
        // "App Updates" toggle) so the user finds out even without opening the About section.
        if (state is AndroidUpdateState.Available &&
            container.prefs.appUpdateNotificationsEnabled.first() &&
            container.notifications.canPostNotifications() &&
            container.prefs.lastNotifiedUpdateVersion.first() != state.latest
        ) {
            container.notifications.showUpdateAvailable()
            container.prefs.setLastNotifiedUpdateVersion(state.latest)
        }
    }

    // A photo was shared into the app (or a launcher shortcut fired) — bring the
    // Diary "Meals" block forward: in SHBD only it hosts HomeScreen, which
    // consumes both inboxes and opens FoodPhotoSession review / the entry sheet.
    // No-op during onboarding (HOME isn't on the stack yet); the inboxes are
    // sticky, so Meals picks them up once opened. RESUMED-only so a stopped
    // duplicate MainActivity cannot mutate this nav controller. popUpTo(HOME)
    // with saveState keeps the displaced tab restorable (audit M1: the yank).
    // In-app gallery does not use sharedImageInbox (Activity FoodPhotoSession).
    val lifecycleOwner = LocalLifecycleOwner.current
    fun NavBackStackEntry?.isMealsBlock(): Boolean =
        this?.destination?.route == ShbdRoutes.DIARY_BLOCK &&
            this.arguments?.getString("block") == DiaryBlock.NUTRITION.id
    val onMealsBlock = backStack.isMealsBlock()
    fun bringMealsForward() {
        val entry = nav.currentBackStackEntry
        val route = entry?.destination?.route
        if (route == null || route == ShbdRoutes.ONBOARDING || entry.isMealsBlock()) return
        nav.navigate(ShbdRoutes.diaryBlock(DiaryBlock.NUTRITION)) {
            popUpTo(ShbdRoutes.HOME) { saveState = true }
            launchSingleTop = true
        }
    }
    val sharedImages by container.sharedImageInbox.collectAsState()
    LaunchedEffect(sharedImages, currentRoute, lifecycleOwner) {
        if (sharedImages.isEmpty()) return@LaunchedEffect
        if (currentRoute == null || onMealsBlock) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (container.sharedImageInbox.value.isEmpty()) return@repeatOnLifecycle
            bringMealsForward()
        }
    }
    val shortcutEntry by container.shortcutEntryInbox.collectAsState()
    LaunchedEffect(shortcutEntry, currentRoute, lifecycleOwner) {
        if (shortcutEntry == null) return@LaunchedEffect
        if (currentRoute == null || onMealsBlock) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (container.shortcutEntryInbox.value == null) return@repeatOnLifecycle
            bringMealsForward()
        }
    }
    // Notification tap destination (Codeberg #27): navigate once, clear the inbox.
    val launchDestination by container.launchDestinationInbox.collectAsState()
    LaunchedEffect(launchDestination, lifecycleOwner) {
        // Progress is no longer a screen: shbd://go/progress opens the Weight block over the Diary.
        val dest = launchDestination ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (container.launchDestinationInbox.value != dest) return@repeatOnLifecycle
            val route = nav.currentBackStackEntry?.destination?.route
            // Onboarding has no HOME on the stack yet; ignore the tap there.
            if (route == null || route == ShbdRoutes.ONBOARDING) return@repeatOnLifecycle
            if (route != ShbdRoutes.HOME && route != dest) {
                nav.popBackStack(ShbdRoutes.HOME, inclusive = false, saveState = true)
            }
            if (container.launchDestinationInbox.value != dest) return@repeatOnLifecycle
            if (dest == ShbdRoutes.PROGRESS) {
                nav.navigate(ShbdRoutes.diaryBlock(DiaryBlock.WEIGHT)) {
                    popUpTo(ShbdRoutes.HOME) { saveState = true }
                    launchSingleTop = true
                }
            } else if (route != dest) {
                nav.navigate(dest) {
                    popUpTo(ShbdRoutes.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            container.launchDestinationInbox.value = null
        }
    }

    // App-open epoch for the Home fill-from-zero reveal. Bumped only on ON_START
    // that follows an ON_STOP (a genuine background -> foreground return), so
    // transient pauses (notification shade, permission dialog) don't retrigger it.
    var launchFillEpoch by remember { mutableIntStateOf(1) }
    var hasStopped by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> hasStopped = true
                Lifecycle.Event.ON_START -> if (hasStopped) { launchFillEpoch++; hasStopped = false }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val energyUnitRaw by container.prefs.energyUnit.collectAsState(initial = "kcal")
    CompositionLocalProvider(
        LocalLaunchFillEpoch provides launchFillEpoch,
        LocalEnergyUnit provides EnergyUnit.fromStorage(energyUnitRaw),
    ) {
    // Codeberg #20: phase 1 hides the coach tab (View-only); phase 2's master
    // AI-off switch hides it too — with AI off the coach has nothing to say.
    val coachTabEnabled by container.prefs.coachTabEnabled.collectAsState(initial = true)
    val aiFeaturesEnabled by container.prefs.aiFeaturesEnabled.collectAsState(initial = true)
    val showCoachTab = coachTabEnabled && aiFeaturesEnabled
    Scaffold(
        bottomBar = {
            if (showTabs) {
                ShbdBottomNavBar(
                    currentRoute = ShbdRoutes.tabFor(currentRoute),
                    showAboutBadge = updateAvailable,
                    showCoachTab = showCoachTab,
                    onTap = { target ->
                        // On a Diary block screen the Diary tab is highlighted but the route differs,
                        // so tapping it falls through to popBackStack(HOME): back to the dashboard.
                        if (target == currentRoute) return@ShbdBottomNavBar
                        // Tapping HOME (the start destination) needs popBackStack
                        // — `navigate(HOME) { popUpTo(HOME); launchSingleTop = true }`
                        // is a no-op because NavController sees HOME at the top of
                        // the stack and skips re-emitting currentBackStackEntry, so
                        // the bar stays selected on the previous tab.
                        if (target == ShbdRoutes.HOME) {
                            nav.popBackStack(ShbdRoutes.HOME, inclusive = false)
                        } else {
                            nav.navigate(target) {
                                popUpTo(ShbdRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = if (startOnboarding) ShbdRoutes.ONBOARDING else ShbdRoutes.HOME
            ) {
                composable(ShbdRoutes.ONBOARDING) {
                    OnboardingScreen(container = container, onComplete = {
                        nav.navigate(ShbdRoutes.HOME) {
                            popUpTo(ShbdRoutes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    })
                }
                composable(ShbdRoutes.HOME) {
                    DiaryDashboardScreen(
                        container = container,
                        onOpenBlock = { block, add ->
                            nav.navigate(ShbdRoutes.diaryBlock(block, add)) { launchSingleTop = true }
                        },
                        onOpenGoals = {
                            nav.navigate(ShbdRoutes.CALORIE_PLAN) { launchSingleTop = true }
                        },
                        onOpenHealthSettings = {
                            nav.navigate(ShbdRoutes.SETTINGS_HEALTH) { launchSingleTop = true }
                        },
                    )
                }
                composable(
                    ShbdRoutes.DIARY_BLOCK,
                    arguments = listOf(
                        navArgument("block") { type = NavType.StringType },
                        navArgument("add") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) { entry ->
                    val block = DiaryBlock.fromId(entry.arguments?.getString("block"))
                    if (block == null) {
                        // Unknown block id: back to the dashboard instead of a blank screen.
                        LaunchedEffect(Unit) { nav.popBackStack(ShbdRoutes.HOME, inclusive = false) }
                        return@composable
                    }
                    // Share the dashboard HomeViewModel (selected day, goal math) and
                    // ProgressViewModel (chart range): scope them to the HOME entry. HOME
                    // is always below a block after onboarding; fall back to this entry.
                    val homeOwner = remember(entry) {
                        runCatching { nav.getBackStackEntry(ShbdRoutes.HOME) }.getOrNull() ?: entry
                    }
                    CompositionLocalProvider(LocalViewModelStoreOwner provides homeOwner) {
                        DiaryBlockScreen(
                            block = block,
                            container = container,
                            openAddOnStart = entry.arguments?.getBoolean("add") == true,
                            actions = DiaryBlockActions(
                                onBack = { nav.popBackStack() },
                                onOpenHealthSettings = {
                                    nav.navigate(ShbdRoutes.SETTINGS_HEALTH) { launchSingleTop = true }
                                },
                                onOpenBodyMeasurements = {
                                    nav.navigate(ShbdRoutes.BODY_MEASUREMENTS) { launchSingleTop = true }
                                },
                                onOpenSettings = {
                                    nav.navigate(ShbdRoutes.SETTINGS) {
                                        popUpTo(ShbdRoutes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onOpenDayTypes = {
                                    nav.navigate(ShbdRoutes.SETTINGS_DAY_TYPES) { launchSingleTop = true }
                                },
                                onOpenFoodSettings = {
                                    nav.navigate(ShbdRoutes.SETTINGS_FOOD) { launchSingleTop = true }
                                },
                                onPlanWeek = {
                                    nav.navigate(ShbdRoutes.PLAN_WEEK) { launchSingleTop = true }
                                },
                                onOpenCaloriePlan = {
                                    nav.navigate(ShbdRoutes.CALORIE_PLAN) { launchSingleTop = true }
                                },
                            ),
                        )
                    }
                }
                composable(ShbdRoutes.CALORIE_PLAN) {
                    CaloriePlanScreen(container = container, onBack = { nav.popBackStack() })
                }
                composable(ShbdRoutes.PLAN_WEEK) {
                    PlanWeekScreen(container = container, nav = nav)
                }
                composable(ShbdRoutes.COACH) {
                    if (showCoachTab) {
                        CoachScreen(container = container)
                    } else {
                        // Deep link or stale back stack with the tab off: pop back to
                        // Home instead of pushing a second HOME entry on top of COACH
                        // (that stack made back bounce off COACH and then exit).
                        LaunchedEffect(Unit) {
                            nav.popBackStack(ShbdRoutes.HOME, inclusive = false)
                        }
                    }
                }
                composable(ShbdRoutes.RATION) { RationScreen(container = container) }
                composable(ShbdRoutes.SETTINGS) { SettingsScreen(container = container, nav = nav) }
                composable(ShbdRoutes.SETTINGS_PERSONAL) {
                    PersonalSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_GOALS) {
                    GoalsSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_DAY_TYPES) {
                    DayTypesSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_APP) {
                    AppSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_TRACKERS) {
                    TrackersSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_AI) {
                    AiSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_DATA) {
                    DataSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_NUTRITION_TARGETS) {
                    NutritionTargetsSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_SPEECH) {
                    SpeechSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_COACH) {
                    CoachSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_HEALTH) {
                    HealthDataSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_ABOUT) {
                    AboutSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_SUPPORT) {
                    SupportSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_LEGAL) {
                    LegalSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ShbdRoutes.SETTINGS_FOOD) {
                    FoodEntrySettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    route = ShbdRoutes.SETTINGS_WATER,
                    arguments = listOf(navArgument("from") {
                        type = NavType.StringType
                        defaultValue = "app"
                    }),
                ) { entry ->
                    WaterSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                        from = entry.arguments?.getString("from") ?: "app",
                    )
                }
                composable(
                    route = ShbdRoutes.SETTINGS_NICOTINE,
                    arguments = listOf(navArgument("from") {
                        type = NavType.StringType
                        defaultValue = "app"
                    }),
                ) { entry ->
                    NicotineSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                        from = entry.arguments?.getString("from") ?: "app",
                    )
                }
                composable(
                    route = ShbdRoutes.SETTINGS_CAFFEINE,
                    arguments = listOf(navArgument("from") {
                        type = NavType.StringType
                        defaultValue = "app"
                    }),
                ) { entry ->
                    CaffeineSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                        from = entry.arguments?.getString("from") ?: "app",
                    )
                }
                composable(
                    route = ShbdRoutes.SETTINGS_NOTES,
                    arguments = listOf(navArgument("from") {
                        type = NavType.StringType
                        defaultValue = "app"
                    }),
                ) { entry ->
                    NotesSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                        from = entry.arguments?.getString("from") ?: "app",
                    )
                }
                composable(
                    route = ShbdRoutes.SETTINGS_FASTING,
                    arguments = listOf(navArgument("from") {
                        type = NavType.StringType
                        defaultValue = "app"
                    }),
                ) { entry ->
                    FastingSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                        from = entry.arguments?.getString("from") ?: "app",
                    )
                }
                composable(
                    route = ShbdRoutes.SETTINGS_NOTIFICATIONS,
                    arguments = listOf(navArgument("from") {
                        type = NavType.StringType
                        defaultValue = "app"
                    }),
                ) { entry ->
                    NotificationsSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                        from = entry.arguments?.getString("from") ?: "app",
                    )
                }
                composable(
                    route = ShbdRoutes.SETTINGS_SYNC,
                    arguments = listOf(navArgument("from") {
                        type = NavType.StringType
                        defaultValue = "data"
                    }),
                ) { entry ->
                    SyncSettingsScreen(
                        container = container,
                        nav = nav,
                        onBack = { nav.popBackStack() },
                        from = entry.arguments?.getString("from") ?: "data",
                    )
                }
                composable(ShbdRoutes.OPTIONAL_NUTRIENT_GOALS) {
                    OptionalNutrientGoalsScreen(container = container, nav = nav, onBack = { nav.popBackStack() })
                }
                composable(ShbdRoutes.HOME_DISPLAY) {
                    HomeDisplaySettingsScreen(container = container, nav = nav, onBack = { nav.popBackStack() })
                }
                composable(ShbdRoutes.CUSTOMIZE_PROGRESS) {
                    CustomizeProgressScreen(container = container, nav = nav, onBack = { nav.popBackStack() })
                }
                composable(ShbdRoutes.GOAL_PHASES) {
                    GoalPhasesScreen(container = container, nav = nav, onBack = { nav.popBackStack() })
                }
                composable(ShbdRoutes.CALCULATION_METHODS) {
                    CalculationMethodsScreen(onBack = { nav.popBackStack() })
                }
                composable(ShbdRoutes.BODY_MEASUREMENTS) {
                    BodyMeasurementsScreen(container = container, onBack = { nav.popBackStack() })
                }
            }
        }
    }
    }
}

internal fun NavHostController.current(): String? = currentBackStackEntry?.destination?.route
