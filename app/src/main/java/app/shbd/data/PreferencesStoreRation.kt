package app.shbd.data

import app.shbd.models.RationPlan
import app.shbd.models.RationSurveyState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// -- Ration survey ------------------------------------------------------
// Missing or undecodable blob → a fresh survey (intro, not completed); the
// setter copies an undecodable blob aside before replacing it.
internal val PreferencesStore.rationSurveyStateImpl: Flow<RationSurveyState>
    get() = objectPref(Keys.RATION_SURVEY, RationSurveyState.serializer())
        .map { (it ?: RationSurveyState()).normalized() }

internal suspend fun PreferencesStore.setRationSurveyStateImpl(state: RationSurveyState) =
    setObjectPref(Keys.RATION_SURVEY, RationSurveyState.serializer(), state.normalized())

// -- Ration plan --------------------------------------------------------
// Last built weekly menu. Survey edits never touch it; a new build replaces
// it; null removes it. Undecodable → null (no plan).
internal val PreferencesStore.rationPlanImpl: Flow<RationPlan?>
    get() = objectPref(Keys.RATION_PLAN, RationPlan.serializer())

internal suspend fun PreferencesStore.setRationPlanImpl(plan: RationPlan?) =
    setObjectPrefOrRemove(Keys.RATION_PLAN, RationPlan.serializer(), plan)
