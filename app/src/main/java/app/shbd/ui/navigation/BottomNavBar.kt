package app.shbd.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shbd.R

data class BottomTab(val route: String, val icon: ImageVector, @get:StringRes val labelRes: Int)

/** SHBD tabs: Diary | Coach | Meal plan | Profile. */
val BottomTabs = listOf(
    BottomTab(ShbdRoutes.HOME, Icons.Filled.Book, R.string.nav_diary),
    BottomTab(ShbdRoutes.COACH, Icons.Filled.Forum, R.string.nav_coach),
    BottomTab(ShbdRoutes.RATION, Icons.Filled.RestaurantMenu, R.string.nav_ration),
    BottomTab(ShbdRoutes.SETTINGS, Icons.Filled.Person, R.string.nav_profile),
)

/** Codeberg #20 phase 1: drops the coach tab when the user hides it. */
fun bottomTabs(showCoachTab: Boolean = true): List<BottomTab> =
    if (showCoachTab) BottomTabs else BottomTabs.filter { it.route != ShbdRoutes.COACH }

/** Content padding above the standard M3 navigation bar. */
val BottomNavScrollPadding = 80.dp

val BottomNavDockedControlPadding = 72.dp

/** Docked overlays (FAB, chips, snackbar) sit this far above the nav bar. */
val BottomOverlayPadding = BottomNavDockedControlPadding + 16.dp

/**
 * Bottom content padding for screens drawn edge-to-edge under the tab bar (the
 * host Scaffold ignores its inner padding): [BottomNavScrollPadding] plus the
 * system navigation-bar inset the tab bar adds below itself, plus [extra]. Without
 * the inset the last cards end up behind the tab bar on 3-button navigation.
 */
@Composable
fun bottomNavContentPadding(extra: Dp = 0.dp): Dp =
    BottomNavScrollPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + extra

@Composable
fun ShbdBottomNavBar(
    currentRoute: String?,
    showAboutBadge: Boolean = false,
    showCoachTab: Boolean = true,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = bottomTabs(showCoachTab)
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == currentRoute
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = { onTap(tab.route) },
                icon = {
                    if (showAboutBadge && tab.route == ShbdRoutes.SETTINGS) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    modifier = Modifier
                                        .offset(x = (-4).dp, y = 4.dp)
                                        .size(8.dp)
                                        .clip(CircleShape),
                                )
                            },
                        ) {
                            Icon(tab.icon, contentDescription = label)
                        }
                    } else {
                        Icon(tab.icon, contentDescription = label)
                    }
                },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
