package com.damagdpixl.svita.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.Charcoal
import com.damagdpixl.svita.core.designsystem.Marigold
import com.damagdpixl.svita.core.designsystem.monoUpper
import com.damagdpixl.svita.ui.screens.CalendarScreen
import com.damagdpixl.svita.ui.screens.OutfitsScreen
import com.damagdpixl.svita.ui.screens.PackingScreen
import com.damagdpixl.svita.ui.screens.SettingsScreen
import com.damagdpixl.svita.ui.wardrobe.ImportScreen
import com.damagdpixl.svita.ui.wardrobe.ItemDetailScreen
import com.damagdpixl.svita.ui.wardrobe.ItemEditorScreen
import com.damagdpixl.svita.ui.wardrobe.WardrobeScreen

/** All navigation routes in one place — the single source of truth. */
object Routes {
    const val WARDROBE = "wardrobe"
    const val OUTFITS = "outfits"
    const val CALENDAR = "calendar"
    const val PACKING = "packing"
    const val SETTINGS = "settings"

    const val ITEM_DETAIL = "wardrobe/item/{itemId}"
    const val ITEM_EDITOR = "wardrobe/edit/{itemId}"

    /** Bulk gallery import (photos -> one item each, pHash-lite dedupe). */
    const val IMPORT = "wardrobe/import"

    fun itemDetail(itemId: Long): String = "wardrobe/item/$itemId"

    fun itemEditor(itemId: Long?): String = "wardrobe/edit/${itemId ?: 0}"
}

/** A bottom-bar destination of the app shell. */
data class TopDestination(
    val route: String,
    val labelRes: Int,
)

val TopDestinations: List<TopDestination> = listOf(
    TopDestination(route = Routes.WARDROBE, labelRes = R.string.nav_wardrobe),
    TopDestination(route = Routes.OUTFITS, labelRes = R.string.nav_outfits),
    TopDestination(route = Routes.CALENDAR, labelRes = R.string.nav_calendar),
    TopDestination(route = Routes.PACKING, labelRes = R.string.nav_packing),
    TopDestination(route = Routes.SETTINGS, labelRes = R.string.nav_settings),
)

/**
 * Navigation shell: five destinations on a flat cream/charcoal bottom bar with
 * mono uppercase labels and a marigold marker on the selected tab.
 */
@Composable
fun SvitaApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                modifier = Modifier.testTag("bottom_bar"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                .testTag("tab_${destination.route}"),
                        ) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .padding(2.dp)
                                        .background(Marigold, RoundedCornerShape(6.dp)),
                                )
                            }
                            Text(
                                text = stringResource(destination.labelRes).monoUpper(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) {
                                    Charcoal
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WARDROBE,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.WARDROBE) {
                WardrobeScreen(
                    onOpenItem = { itemId -> navController.navigate(Routes.itemDetail(itemId)) },
                    onAddItem = { navController.navigate(Routes.itemEditor(null)) },
                    onOpenImport = { navController.navigate(Routes.IMPORT) },
                )
            }
            composable(Routes.ITEM_DETAIL) { entry ->
                val itemId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L
                ItemDetailScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Routes.itemEditor(id)) },
                )
            }
            composable(Routes.ITEM_EDITOR) { entry ->
                val itemId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L
                ItemEditorScreen(
                    itemId = itemId.takeIf { it > 0 },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.IMPORT) {
                ImportScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.OUTFITS) { OutfitsScreen() }
            composable(Routes.CALENDAR) { CalendarScreen() }
            composable(Routes.PACKING) { PackingScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
