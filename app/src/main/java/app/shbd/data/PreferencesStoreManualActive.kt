package app.shbd.data

import kotlinx.coroutines.flow.Flow
import app.shbd.models.ManualActiveEntry

internal val PreferencesStore.manualActiveEntriesImpl: Flow<List<ManualActiveEntry>>
    get() = listPref(Keys.MANUAL_ACTIVE_ENTRIES, ManualActiveEntry.serializer())

internal suspend fun PreferencesStore.setManualActiveEntriesImpl(entries: List<ManualActiveEntry>) =
    setListPref(Keys.MANUAL_ACTIVE_ENTRIES, ManualActiveEntry.serializer(), entries)
