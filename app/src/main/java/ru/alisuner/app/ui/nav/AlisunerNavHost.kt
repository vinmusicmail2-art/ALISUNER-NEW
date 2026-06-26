package ru.alisuner.app.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.alisuner.app.R
import ru.alisuner.app.ui.screens.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlisunerNavHost() {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStack?.destination
    val topLevelDestinations = listOf(
        NavItem.Tuner,
        NavItem.Metronome,
        NavItem.ChordRecognition,
        NavItem.Learn,
        NavItem.Arranger
    )
    val showBottomBar = currentDestination?.route in topLevelDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.height(84.dp),
                    tonalElevation = 8.dp
                ) {
                    topLevelDestinations.forEach { item ->
                        CompactNavigationItem(
                            selected = currentDestination?.route == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            item = item
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = NavItem.Tuner.route,
            modifier = Modifier.padding(padding),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) }
        ) {
            composable(NavItem.Tuner.route) { TunerScreen() }
            composable(NavItem.Metronome.route) { MetronomeScreen() }
            composable(NavItem.ChordRecognition.route) { ChordRecognitionScreen() }
            composable(NavItem.Learn.route) { LearnScreen(navController = navController) }
            composable(NavItem.Arranger.route) { ArrangerScreen() }
            composable(
                route = "chord_detail/{chordId}",
                arguments = listOf(navArgument("chordId") { type = NavType.StringType })
            ) {
                val chordId = Uri.decode(it.arguments?.getString("chordId") ?: "C")
                ChordDetailScreen(navController = navController, chordId = chordId)
            }
        }
    }
}

@Composable
private fun RowScope.CompactNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    item: NavItem
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = stringResource(item.labelRes)

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private sealed class NavItem(val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val labelRes: Int) {
    data object Tuner : NavItem("tuner", Icons.Default.Tune, R.string.nav_tuner)
    data object Metronome : NavItem("metronome", Icons.Default.Timer, R.string.nav_metronome)
    data object ChordRecognition : NavItem("chords", Icons.Default.MusicNote, R.string.nav_chords)
    data object Learn : NavItem("learn", Icons.Default.School, R.string.nav_learn)
    data object Arranger : NavItem("arranger", Icons.Default.PlayCircle, R.string.nav_arranger)
    data object ChordDetail : NavItem("chord_detail", Icons.Default.MusicNote, R.string.nav_chords)
}
