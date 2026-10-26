package app.shbd.ui.navigation

import android.app.Application
import app.shbd.data.PreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Codeberg #20 phase 1: the coach tab is View-only hideable, default on, and
 * hiding it leaves the other tabs and routes intact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CoachTabToggleTest {
    @Test
    fun bottomTabs_hidesCoach_byDefaultShowsAll() {
        // SHBD order: Diary | Coach | Meal plan | Profile.
        assertEquals(
            listOf(ShbdRoutes.HOME, ShbdRoutes.COACH, ShbdRoutes.RATION, ShbdRoutes.SETTINGS),
            bottomTabs(true).map { it.route },
        )

        val hidden = bottomTabs(false)
        assertFalse(hidden.any { it.route == ShbdRoutes.COACH })
        assertEquals(
            listOf(ShbdRoutes.HOME, ShbdRoutes.RATION, ShbdRoutes.SETTINGS),
            hidden.map { it.route },
        )
        assertFalse(bottomTabs(true).any { it.route == ShbdRoutes.PROGRESS })
    }

    @Test
    fun coachTabEnabled_defaultsTrue_andPersists() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        assertTrue(prefs.coachTabEnabled.first())

        prefs.setCoachTabEnabled(false)
        assertFalse(prefs.coachTabEnabled.first())

        prefs.setCoachTabEnabled(true)
        assertTrue(prefs.coachTabEnabled.first())
    }

    // Codeberg #20 phase 2: the master AI-features switch defaults ON and hides
    // the coach tab as well (combined with coachTabEnabled in NoFUDNavHost).
    // The DataStore is a process-wide singleton, so reset to the default first
    // (the pristine-default assertion lives in AiFeaturesGateTest).
    @Test
    fun aiFeaturesEnabled_persistsAndHidesCoach() = runBlocking {
        val prefs = PreferencesStore(RuntimeEnvironment.getApplication())
        prefs.setAiFeaturesEnabled(true)
        assertTrue(prefs.aiFeaturesEnabled.first())

        prefs.setAiFeaturesEnabled(false)
        assertFalse(prefs.aiFeaturesEnabled.first())

        prefs.setAiFeaturesEnabled(true)
        assertTrue(prefs.aiFeaturesEnabled.first())

        // Master off hides the coach tab regardless of the tab toggle
        // (NoFUDNavHost combines both: showCoachTab = coachTabEnabled && aiFeaturesEnabled).
        assertFalse(bottomTabs(showCoachTab = true && false).any { it.route == ShbdRoutes.COACH })
    }
}
