package app.shbd.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.shbd.AppContainer
import app.shbd.data.EmbeddedAi
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
import kotlin.math.roundToInt

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
    val aiProvider: AIProvider = AIProvider.ON_DEVICE,
    val aiModel: String = AIProvider.ON_DEVICE.defaultModel,
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

    /**
     * Plan Ready calories: the slider pick, else the maintenance norm minus the
     * deficit (plus the surplus) of the pace picked on Goal Speed — not raised to
     * the BMR floor, so the plan and its ETA match the pace step.
     */
    val planKcal: Int get() {
        val profile = buildProfile()
        val range = planCalorieRange(goal, profile.tdee.roundToInt())
        return customCalories?.coerceIn(range.first, range.last) ?: defaultPlanKcal(profile)
    }

    /** [buildProfile] carrying [planKcal]; macros re-split from it. Saved at completion. */
    fun buildPlanProfile(): UserProfile = planProfileFor(buildProfile(), planKcal)
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
        updatePlanInput { it.copy(weightKg = kg, goalWeightKg = defaultGoalWeightKg(kg, it.goal)) }
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
    fun setActivity(v: ActivityLevel) = updatePlanInput { it.copy(activity = v) }
    fun setGoal(v: WeightGoal) {
        updatePlanInput { it.copy(goal = v, goalWeightKg = defaultGoalWeightKg(it.weightKg, v)) }
    }
    fun setGoalWeight(v: Double) = updatePlanInput { it.copy(goalWeightKg = v) }
    fun setWeeklyChange(v: Double) = updatePlanInput { it.copy(weeklyChangeKg = v) }

    /** Applies a change the plan depends on; a slider pick made earlier is dropped so
     *  Plan Ready follows the pace again. Unchanged values keep the pick. */
    private fun updatePlanInput(change: (OnboardingState) -> OnboardingState) {
        val old = _ui.value
        val new = change(old)
        if (new == old) return
        planEdited = false
        _ui.value = new.copy(customCalories = null, customProtein = null, customCarbs = null, customFat = null)
    }
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

    /** Building Plan step: only the animation. The plan follows the pace picked on Goal
     *  Speed (see [OnboardingState.planKcal]); an AI estimate would override it, so none
     *  runs here — Recalculate in Profile → Goals stays the AI path. */
    fun buildPlanWithAI(onDone: () -> Unit) {
        onDone()
    }

    fun next() {
        if (_ui.value.step == OnboardingStep.DISCLAIMERS) return
        advanceStep()
    }

    /**
     * Onboarding has no AI step: the active profile always becomes the embedded
     * on-device model. Nothing is downloaded here — without the model file the
     * plan is computed by formula.
     */
    private suspend fun applyDefaultAi() {
        activeProfileIdFor(AIProvider.ON_DEVICE)
        container.prefs.editActiveProfile { EmbeddedAi.profile(it) }
        _ui.value = _ui.value.copy(aiProvider = AIProvider.ON_DEVICE, aiModel = EmbeddedAi.modelId)
    }

    private fun advanceStep() {
        if (_ui.value.step == OnboardingStep.DISCLAIMERS) return
        val nextStep = OnboardingStep.values().getOrNull(_ui.value.step.ordinal + 1) ?: return
        _ui.value = _ui.value.copy(step = nextStep)
    }

    fun back() {
        val prevStep = previousOnboardingStep(_ui.value.step) ?: return
        _ui.value = _ui.value.copy(step = prevStep)
    }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(submitting = true)
            val state = _ui.value
            val profile = state.buildPlanProfile()
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

/**
 * Step "Back" opens. BUILDING_PLAN advances on its own, so going back from
 * PLAN_READY skips it — otherwise "Back" would bounce straight to the plan.
 */
internal fun previousOnboardingStep(step: OnboardingStep): OnboardingStep? {
    val prev = OnboardingStep.entries.getOrNull(step.ordinal - 1) ?: return null
    return if (prev == OnboardingStep.BUILDING_PLAN) previousOnboardingStep(prev) else prev
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

/** The pace plan: maintenance norm plus the pace adjustment of [profile], within [planCalorieRange]. */
internal fun defaultPlanKcal(profile: UserProfile): Int {
    val norm = profile.tdee.roundToInt()
    val range = planCalorieRange(profile.goal, norm)
    return (norm + profile.calorieAdjustment).coerceIn(range.first, range.last)
}

/** [base] carrying [planKcal] as its calories; left as is when that is already its own goal. */
internal fun planProfileFor(base: UserProfile, planKcal: Int): UserProfile =
    if (planKcal == base.dailyCalories && base.customCalories == null) base
    else base.copy(customCalories = planKcal)

/**
 * Plan screen opened from the diary: [profile] at the latest logged weight with
 * its own calories and macros cleared, so the norm follows the current weight
 * and the macros re-split from the picked plan — as in onboarding.
 */
internal fun planBaseProfile(profile: UserProfile, latestWeightKg: Double?): UserProfile {
    val weightKg = latestWeightKg ?: profile.weightKg
    return profile.copy(
        weightKg = weightKg,
        // Lose / gain always has a desired weight to edit on the plan screen.
        goalWeightKg = if (profile.goal == WeightGoal.MAINTAIN) profile.goalWeightKg
        else profile.goalWeightKg ?: defaultGoalWeightKg(weightKg, profile.goal),
        customCalories = null,
        customProtein = null,
        customCarbs = null,
        customFat = null,
    )
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

/** Daily deficit of the fastest offered pace (1 kg a week); a bigger one shows the doctor note. */
internal val PLAN_WARNING_DEFICIT_KCAL: Int =
    NutritionConstants.dailyCalorieAdjustmentForWeeklyRateKg(1.0)

/** Plan Ready warns only when losing with a deficit bigger than 1 kg a week. */
internal fun planNeedsDoctorWarning(goal: WeightGoal, normKcal: Int, planKcal: Int): Boolean =
    goal == WeightGoal.LOSE && normKcal - planKcal > PLAN_WARNING_DEFICIT_KCAL

/** Recommended minimum in that warning: the 1 kg-a-week plan (norm − 1 100), never below zero. */
internal fun planMinRecommendedKcal(normKcal: Int): Int =
    (normKcal - PLAN_WARNING_DEFICIT_KCAL).coerceAtLeast(0)
