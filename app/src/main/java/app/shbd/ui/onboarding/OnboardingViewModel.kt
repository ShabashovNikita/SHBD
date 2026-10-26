package app.shbd.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.shbd.AppContainer
import app.shbd.BuildConfig
import app.shbd.services.ondevice.OnDeviceCapability
import app.shbd.models.ActivityLevel
import app.shbd.models.AIProvider
import app.shbd.models.AiProfile
import app.shbd.models.DietMode
import app.shbd.models.Gender
import app.shbd.models.InstantSerializer
import app.shbd.models.KetoCarbMode
import app.shbd.models.NutritionConstants
import app.shbd.models.LocalDateSerializer
import app.shbd.models.UserProfile
import app.shbd.models.WeightGoal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** No diet-mode, notifications, Home-look or AI-provider steps: diet mode and notifications
 *  keep their defaults, Home look lives in Profile → Customize Home, and the AI model is
 *  chosen by [OnboardingViewModel.applyDefaultAi]. A saved draft that names a removed step
 *  fails to decode and onboarding restarts from WELCOME. */
@Serializable
enum class OnboardingStep {
    WELCOME, GENDER, BIRTHDAY, HEIGHT, WEIGHT, BODY_FAT,
    ACTIVITY, GOAL, GOAL_WEIGHT, GOAL_SPEED,
    HEALTH_CONNECT,
    BUILDING_PLAN, PLAN_READY, DISCLAIMERS
}

/** Snapshot of in-progress onboarding answers, persisted to DataStore so the flow can resume
 *  after Android kills the backgrounded process instead of restarting from
 *  [OnboardingStep.WELCOME]. Excludes transient UI-only fields (submitting flag). */
@Serializable
data class OnboardingDraft(
    val step: OnboardingStep,
    val gender: Gender,
    @Serializable(with = LocalDateSerializer::class)
    val birthday: LocalDate,
    val heightCm: Int,
    val weightKg: Double,
    val bodyFatPercentage: Double? = null,
    @Serializable(with = InstantSerializer::class)
    val bodyFatUpdatedAt: Instant? = null,
    val goalBodyFatPercentage: Double? = null,
    val activity: ActivityLevel,
    val goal: WeightGoal,
    val dietMode: DietMode,
    val ketoCarbMode: KetoCarbMode,
    val ketoCarbManualTarget: Int? = null,
    val goalWeightKg: Double,
    val weeklyChangeKg: Double,
    val notificationsEnabled: Boolean,
    val healthConnectEnabled: Boolean,
    val aiProvider: AIProvider,
    val aiModel: String,
    val customCalories: Int? = null,
    val customProtein: Int? = null,
    val customCarbs: Int? = null,
    val customFat: Int? = null,
)

private fun OnboardingState.toDraft() = OnboardingDraft(
    step = step, gender = gender, birthday = birthday, heightCm = heightCm, weightKg = weightKg,
    bodyFatPercentage = bodyFatPercentage, bodyFatUpdatedAt = bodyFatUpdatedAt,
    goalBodyFatPercentage = goalBodyFatPercentage,
    activity = activity, goal = goal, dietMode = dietMode, ketoCarbMode = ketoCarbMode,
    ketoCarbManualTarget = ketoCarbManualTarget, goalWeightKg = goalWeightKg, weeklyChangeKg = weeklyChangeKg,
    notificationsEnabled = notificationsEnabled, healthConnectEnabled = healthConnectEnabled,
    aiProvider = aiProvider, aiModel = aiModel,
    customCalories = customCalories, customProtein = customProtein, customCarbs = customCarbs, customFat = customFat,
)

private fun OnboardingState.applyDraft(draft: OnboardingDraft) = copy(
    step = draft.step, gender = draft.gender, birthday = draft.birthday, heightCm = draft.heightCm,
    weightKg = draft.weightKg, bodyFatPercentage = draft.bodyFatPercentage,
    bodyFatUpdatedAt = draft.bodyFatUpdatedAt,
    goalBodyFatPercentage = draft.goalBodyFatPercentage,
    activity = draft.activity, goal = draft.goal, dietMode = draft.dietMode, ketoCarbMode = draft.ketoCarbMode,
    ketoCarbManualTarget = draft.ketoCarbManualTarget, goalWeightKg = draft.goalWeightKg,
    weeklyChangeKg = draft.weeklyChangeKg,
    notificationsEnabled = draft.notificationsEnabled, healthConnectEnabled = draft.healthConnectEnabled,
    aiProvider = draft.aiProvider, aiModel = draft.aiModel,
    customCalories = draft.customCalories, customProtein = draft.customProtein,
    customCarbs = draft.customCarbs, customFat = draft.customFat,
)

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val gender: Gender = Gender.MALE,
    val birthday: LocalDate = LocalDate.now().minusYears(25),
    val heightCm: Int = 175,
    val weightKg: Double = DEFAULT_ONBOARDING_WEIGHT_KG,
    val bodyFatPercentage: Double? = null,
    /** When the body-fat reading was entered; mirrors UserProfile.bodyFatUpdatedAt so the
     *  90-day Katch-McArdle recency gate survives onboarding resume. */
    val bodyFatUpdatedAt: Instant? = null,
    /** Optional target body-fat fraction. Only meaningful when bodyFatPercentage
     *  is non-null (i.e. user picked "Yes I know my body fat" + opted into
     *  setting a goal). Display-only — does NOT participate in BMR/TDEE/macro math. */
    val goalBodyFatPercentage: Double? = null,
    /** Defaults: sedentary activity and "lose weight" — the most common SHBD start. */
    val activity: ActivityLevel = ActivityLevel.SEDENTARY,
    val goal: WeightGoal = WeightGoal.LOSE,
    val dietMode: DietMode = DietMode.STANDARD,
    val ketoCarbMode: KetoCarbMode = KetoCarbMode.ADAPTIVE,
    val ketoCarbManualTarget: Int? = null,
    val goalWeightKg: Double = DEFAULT_ONBOARDING_GOAL_WEIGHT_KG,
    /** 0.25 (slow), 0.5 (moderate), 1.0 (fast) kg/week */
    val weeklyChangeKg: Double = 0.5,
    /** Onboarding always asks in cm / kg; the unit system is changed later in
     *  Profile → Personal → Units, never here. */
    val heightMetric: Boolean = true,
    val weightMetric: Boolean = true,
    val notificationsEnabled: Boolean = false,
    val healthConnectEnabled: Boolean = false,
    /** Default AI model picked by [OnboardingViewModel.applyDefaultAi]; not user-editable here. */
    val aiProvider: AIProvider = AIProvider.GEMINI,
    val aiModel: String = AIProvider.GEMINI.defaultModel,
    val submitting: Boolean = false,
    /** Manual overrides applied on the Plan Ready step. Null = use formula default. */
    val customCalories: Int? = null,
    val customProtein: Int? = null,
    val customCarbs: Int? = null,
    val customFat: Int? = null
) {
    /** DISCLAIMERS is the final step (safety + accuracy notices after the plan). */
    val isLastStep: Boolean get() = step == OnboardingStep.DISCLAIMERS

    /** Every remaining step can be left with its defaults. */
    val canAdvance: Boolean get() = !submitting

    fun buildProfile(): UserProfile = UserProfile(
        gender = gender,
        birthday = birthday.atStartOfDay(ZoneId.systemDefault()).toInstant(),
        heightCm = heightCm.toDouble(),
        weightKg = weightKg,
        activityLevel = activity,
        goal = goal,
        dietMode = dietMode,
        ketoCarbMode = ketoCarbMode,
        ketoCarbManualTarget = ketoCarbManualTarget,
        bodyFatPercentage = bodyFatPercentage,
        bodyFatUpdatedAt = bodyFatUpdatedAt,
        goalBodyFatPercentage = if (bodyFatPercentage != null) goalBodyFatPercentage else null,
        weeklyChangeKg = if (goal == WeightGoal.MAINTAIN) null else weeklyChangeKg,
        goalWeightKg = if (goal == WeightGoal.MAINTAIN) null else goalWeightKg,
        customCalories = customCalories,
        customProtein = customProtein,
        customCarbs = customCarbs,
        customFat = customFat
    )
}

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(OnboardingState())
    val ui: StateFlow<OnboardingState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val draft = container.prefs.onboardingDraft.first()
            if (draft != null) _ui.value = _ui.value.applyDraft(draft)
            applyDefaultAi()
        }
        // Persist the in-progress draft so onboarding can resume where the user left off if the
        // OS reclaims memory and kills the process while the app is backgrounded.
        viewModelScope.launch {
            ui.drop(1).debounce(400).collect { state ->
                container.prefs.setOnboardingDraft(state.toDraft())
            }
        }
    }

    fun setGender(v: Gender) { _ui.value = _ui.value.copy(gender = v) }
    fun setBirthday(v: LocalDate) { _ui.value = _ui.value.copy(birthday = v) }
    fun setHeight(cm: Int) { _ui.value = _ui.value.copy(heightCm = cm) }
    fun setWeight(kg: Double) {
        _ui.value = _ui.value.copy(weightKg = kg, goalWeightKg = defaultGoalWeightKg(kg, _ui.value.goal))
    }
    fun setBodyFat(pct: Double?) {
        // Clear the goal alongside the current value so a stale goal doesn't
        // linger when the user backs out of "Yes I know my body fat".
        _ui.value = _ui.value.copy(
            bodyFatPercentage = pct,
            bodyFatUpdatedAt = if (pct == null) null else Instant.now(),
            goalBodyFatPercentage = if (pct == null) null else _ui.value.goalBodyFatPercentage
        )
    }
    fun setGoalBodyFat(pct: Double?) { _ui.value = _ui.value.copy(goalBodyFatPercentage = pct) }
    fun setActivity(v: ActivityLevel) { _ui.value = _ui.value.copy(activity = v) }
    fun setGoal(v: WeightGoal) {
        _ui.value = _ui.value.copy(goal = v, goalWeightKg = defaultGoalWeightKg(_ui.value.weightKg, v))
    }
    fun setGoalWeight(v: Double) { _ui.value = _ui.value.copy(goalWeightKg = v) }
    fun setWeeklyChange(v: Double) { _ui.value = _ui.value.copy(weeklyChangeKg = v) }
    fun setHealthConnectEnabled(v: Boolean) {
        _ui.value = _ui.value.copy(healthConnectEnabled = v)
    }
    /**
     * Active profile id for onboarding writes, creating a default profile named after
     * [provider] first when the store is still empty (fresh install before migration
     * had a chance to seed one).
     */
    private suspend fun activeProfileIdFor(provider: AIProvider): UUID {
        container.prefs.activeAiProfile.first()?.let { return it.id }
        val created = AiProfile(
            id = UUID.randomUUID(),
            name = container.appContext.getString(provider.displayNameRes),
            provider = provider,
            model = provider.defaultModel,
            createdAt = Instant.now(),
        )
        container.prefs.mutateProfiles { it.add(created) }
        container.prefs.setActiveAiProfileId(created.id.toString())
        return created.id
    }


    /**
     * Plan step: the user only picks the daily calories. Macros are not editable;
     * pinned macros (e.g. from the AI plan) are cleared so protein / fat / carbs
     * re-split the new calorie total by formula.
     */
    fun setCustomCalories(v: Int?) {
        planEdited = true
        _ui.value = _ui.value.copy(customCalories = v, customProtein = null, customCarbs = null, customFat = null)
    }

    /** The user hand-tuned the plan — Adaptive Goals then stays off at completion
     *  so its first weekly run can't overwrite their numbers. */
    private var planEdited = false

    /** Building Plan step: compute calorie + macro targets with AI (forecast is null for a new
     *  user). On success seeds the custom targets the Plan Ready screen shows; on failure leaves
     *  them null so the formula values are used. Calls [onDone] either way. */
    fun buildPlanWithAI(onDone: () -> Unit) {
        viewModelScope.launch {
            val state = _ui.value
            val result = runCatching {
                // A cold on-device engine load (60-90 s+ of uninterruptible
                // native init + generation) would hold the "Setting up" screen
                // at 100% with no feedback. Formula targets are the documented
                // fallback anyway, so only run the AI plan calc when the
                // on-device engine is already warm.
                if (state.aiProvider == AIProvider.ON_DEVICE &&
                    !container.onDeviceLlmGateway.isLoaded
                ) {
                    null
                } else {
                    container.foodAnalysis.calculateGoals(state.buildProfile(), forecast = null, heightMetric = state.heightMetric, weightMetric = state.weightMetric)
                }
            }.getOrNull()
            if (result != null) {
                _ui.value = _ui.value.copy(
                    customCalories = result.calories,
                    customProtein = result.protein,
                    customFat = result.fat,
                    customCarbs = result.carbs,
                )
            }
            onDone()
        }
    }

    fun next() {
        if (_ui.value.step == OnboardingStep.DISCLAIMERS) return
        advanceStep()
    }

    /**
     * Onboarding has no AI step: the app provides a model by default. Order:
     * 1. the active profile already has a key (or needs none) — keep it;
     * 2. debug build with a baked GEMINI_API_KEY (secrets.properties) — Gemini with that key;
     * 3. the phone can run on-device AI — on-device model;
     * 4. otherwise Gemini without a key; the user adds one in Profile → AI.
     */
    private suspend fun applyDefaultAi() {
        val active = container.prefs.activeAiProfile.first()
        if (active != null) {
            val hasKey = !container.keyStore.profileApiKey(active.id).isNullOrBlank()
            if (!active.provider.requiresApiKey || hasKey) {
                _ui.value = _ui.value.copy(aiProvider = active.provider, aiModel = active.model ?: active.provider.defaultModel)
                return
            }
        }
        val debugKey = BuildConfig.GEMINI_API_KEY.trim()
        val provider = when {
            debugKey.isNotEmpty() -> AIProvider.GEMINI
            OnDeviceCapability.isSupported(container.appContext) -> AIProvider.ON_DEVICE
            else -> AIProvider.GEMINI
        }
        val profileId = activeProfileIdFor(provider)
        container.prefs.editActiveProfile { it.copy(provider = provider, model = provider.defaultModel) }
        if (provider == AIProvider.GEMINI && debugKey.isNotEmpty()) {
            container.keyStore.setProfileApiKey(profileId, debugKey)
        }
        _ui.value = _ui.value.copy(aiProvider = provider, aiModel = provider.defaultModel)
    }

    private fun advanceStep() {
        if (_ui.value.step == OnboardingStep.DISCLAIMERS) return
        val nextStep = OnboardingStep.values().getOrNull(_ui.value.step.ordinal + 1) ?: return
        _ui.value = _ui.value.copy(step = nextStep)
    }

    fun back() {
        val prevStep = OnboardingStep.values().getOrNull(_ui.value.step.ordinal - 1) ?: return
        _ui.value = _ui.value.copy(step = prevStep)
    }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(submitting = true)
            val state = _ui.value
            val profile = state.buildProfile()
            container.profileRepository.saveFromOnboarding(profile)
            container.weightRepository.seedInitialWeightIfEmpty(profile.weightKg)
            // Only seed body fat when the user actually entered one in onboarding
            // (the "Yes I know my body fat %" branch); the "No" branch leaves
            // bodyFatPercentage null and the store stays empty.
            profile.bodyFatPercentage?.let {
                container.bodyFatRepository.seedInitialBodyFatIfEmpty(
                    it,
                    at = profile.bodyFatUpdatedAt ?: Instant.now()
                )
            }
            container.prefs.setNotificationsEnabled(state.notificationsEnabled)
            container.prefs.setHealthConnectEnabled(state.healthConnectEnabled)
            // The AI profile was already set by applyDefaultAi() when onboarding opened.
            // New installs start with Energy Burn on, and Adaptive Goals on unless the
            // user hand-tuned their plan (adaptive would overwrite it). Existing users are
            // untouched — these prefs are only written here and by the Settings toggles.
            // Onboarding just calculated goals, so stamp the weekly adaptive check as done;
            // the first auto-run lands next week.
            if (!planEdited) container.prefs.setAdaptiveGoalsEnabled(true)
            container.prefs.setHealthEnergyGoalsEnabled(true)
            container.prefs.setAdaptiveGoalsLastCheckDay(LocalDate.now().toString())
            container.prefs.setOnboardingCompleted(true)
            container.prefs.setOnboardingDraft(null)
            onDone()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            OnboardingViewModel(container) as T
    }
}

/** Starting weight on the wheel; the tenths column starts on 4 (middle of 0–9). */
internal const val DEFAULT_ONBOARDING_WEIGHT_KG = 70.4

/** Default desired weight for the default "lose" goal: [DEFAULT_ONBOARDING_WEIGHT_KG] − 5 kg. */
internal const val DEFAULT_ONBOARDING_GOAL_WEIGHT_KG = 65.4

/** Tenths digit every default weight starts on, so the tenths wheel opens centred. */
private const val DEFAULT_WEIGHT_TENTHS = 0.4

/**
 * Default desired weight after picking [goal]: 5 kg below (lose) or above (gain)
 * the whole kilograms of [weightKg], always with ",4" in the tenths column.
 */
internal fun defaultGoalWeightKg(weightKg: Double, goal: WeightGoal): Double {
    val whole = kotlin.math.floor(weightKg)
    val base = when (goal) {
        WeightGoal.LOSE -> whole - 5
        WeightGoal.GAIN -> whole + 5
        WeightGoal.MAINTAIN -> return weightKg
    }
    return (base + DEFAULT_WEIGHT_TENTHS).coerceIn(30.0, 250.0)
}

/** Plan slider step, kcal. */
internal const val PLAN_SLIDER_STEP_KCAL = 10

/** Largest daily surplus offered on the plan step when gaining weight, kcal. */
internal const val PLAN_MAX_SURPLUS_KCAL = 1000

/**
 * Calories the plan step lets the user pick. Lose / maintain: from 0 up to the
 * maintenance norm [normKcal] — never above it. Gain: from the norm up to the
 * norm plus [PLAN_MAX_SURPLUS_KCAL].
 */
internal fun planCalorieRange(goal: WeightGoal, normKcal: Int): IntRange {
    val norm = normKcal.coerceAtLeast(0)
    return if (goal == WeightGoal.GAIN) norm..(norm + PLAN_MAX_SURPLUS_KCAL) else 0..norm
}

/**
 * Days until [goalWeightKg] at [planKcal] a day against the maintenance norm
 * [normKcal]: weight gap × 7 700 kcal/kg ÷ daily deficit (lose) or surplus (gain),
 * rounded up. 0 when the goal is already reached; null when there is no goal
 * (maintain) or the plan does not move the weight toward it.
 */
internal fun planDaysToGoal(
    weightKg: Double,
    goalWeightKg: Double,
    goal: WeightGoal,
    normKcal: Int,
    planKcal: Int,
): Int? {
    if (goal == WeightGoal.MAINTAIN) return null
    val gapKg = if (goal == WeightGoal.LOSE) weightKg - goalWeightKg else goalWeightKg - weightKg
    if (gapKg <= 0.05) return 0
    val dailyKcal = if (goal == WeightGoal.LOSE) normKcal - planKcal else planKcal - normKcal
    if (dailyKcal <= 0) return null
    return kotlin.math.ceil(gapKg * NutritionConstants.KCAL_PER_KG_BODY_MASS / dailyKcal).toInt()
}
