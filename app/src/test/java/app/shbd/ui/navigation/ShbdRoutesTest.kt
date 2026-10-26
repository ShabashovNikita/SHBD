package app.shbd.ui.navigation

import app.shbd.ui.diary.blocks.DiaryBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security regression: only exactly-known plain routes may be navigated via
 * `shbd://go/<dest>`. `nav.navigate()` throws for unknown destinations,
 * so the whitelist is what keeps an unprivileged app from crashing us with
 * a crafted VIEW intent.
 */
class ShbdRoutesTest {
    @Test
    fun knownPlainRoutes_areGoDestinations() {
        assertTrue(ShbdRoutes.isGoDestination(ShbdRoutes.PROGRESS))
        assertTrue(ShbdRoutes.isGoDestination(ShbdRoutes.HOME))
        assertTrue(ShbdRoutes.isGoDestination(ShbdRoutes.COACH))
        assertTrue(ShbdRoutes.isGoDestination(ShbdRoutes.RATION))
        assertTrue(ShbdRoutes.isGoDestination(ShbdRoutes.SETTINGS))
        assertTrue(ShbdRoutes.isGoDestination(ShbdRoutes.SETTINGS_AI))
        assertTrue(ShbdRoutes.isGoDestination(ShbdRoutes.BODY_MEASUREMENTS))
    }

    @Test
    fun unknownDestinations_areNotGoDestinations() {
        assertFalse(ShbdRoutes.isGoDestination("garbage"))
        assertFalse(ShbdRoutes.isGoDestination("progress/evil"))
        assertFalse(ShbdRoutes.isGoDestination("../home"))
        assertFalse(ShbdRoutes.isGoDestination(""))
        assertFalse(ShbdRoutes.isGoDestination("onboarding"))
    }

    @Test
    fun argRoutedScreens_areNotGoDestinations() {
        // Path-only `go` links cannot satisfy ?from={from} patterns; navigating
        // without the arg would throw, so they stay off the whitelist.
        assertFalse(ShbdRoutes.isGoDestination("settings/water"))
        assertFalse(ShbdRoutes.isGoDestination("settings/notifications"))
        assertFalse(ShbdRoutes.isGoDestination("settings/sync"))
        assertFalse(ShbdRoutes.isGoDestination("diary/food"))
    }

    @Test
    fun diaryBlock_buildsRouteForEveryBlock() {
        DiaryBlock.entries.forEach { block ->
            assertEquals("diary/block/${block.id}?add=false", ShbdRoutes.diaryBlock(block))
            assertEquals(block, DiaryBlock.fromId(block.id))
        }
        assertEquals("diary/block/nutrition?add=true", ShbdRoutes.diaryBlock(DiaryBlock.NUTRITION, add = true))
        assertNull(DiaryBlock.fromId("x"))
        assertNull(DiaryBlock.fromId(null))
    }

    @Test
    fun diaryBlock_isNotTabOrGoDestination_butShowsBottomBar() {
        assertFalse(ShbdRoutes.DIARY_BLOCK in ShbdRoutes.bottomTabs)
        assertFalse(ShbdRoutes.isGoDestination("diary/block/weight"))
        assertTrue(ShbdRoutes.showsBottomBar(ShbdRoutes.DIARY_BLOCK))
        assertEquals(ShbdRoutes.HOME, ShbdRoutes.tabFor(ShbdRoutes.DIARY_BLOCK))
        assertFalse(ShbdRoutes.showsBottomBar(ShbdRoutes.SETTINGS_GOALS))
        assertFalse(ShbdRoutes.PROGRESS in ShbdRoutes.bottomTabs)
    }
}
