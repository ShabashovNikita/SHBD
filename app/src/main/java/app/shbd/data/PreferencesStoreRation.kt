package app.shbd.data

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
