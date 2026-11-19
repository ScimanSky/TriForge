package com.triforge.app.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.triforge.app.AppContainer
import com.triforge.app.R
import com.triforge.app.data.GoogleSignInResult
import com.triforge.app.ui.activities.ActivitiesScreen
import com.triforge.app.ui.auth.AuthScreen
import com.triforge.app.ui.auth.AuthViewModel
import com.triforge.app.ui.common.CenteredMessage
import com.triforge.app.ui.common.LoadingBox
import com.triforge.app.ui.detail.ActivityDetailScreen
import com.triforge.app.ui.profile.ProfileScreen
import com.triforge.app.ui.profile.ProfileViewModel
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.today.TodayScreen

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    TODAY("today", R.string.nav_today, TriIcons.Home),
    ACTIVITIES("activities", R.string.nav_activities, TriIcons.Activity),
    PROFILE("profile", R.string.nav_profile, TriIcons.Person),
}

private const val ACTIVITY_ID = "id"
private const val ACTIVITY_ROUTE = "activity/{$ACTIVITY_ID}"

@Composable
fun TriForgeRoot(
    container: AppContainer,
    requestGoogleToken: suspend () -> GoogleSignInResult,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!container.firebaseConfigured) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                CenteredMessage(
                    title = stringResource(R.string.setup_title),
                    body = stringResource(R.string.setup_body),
                )
            }
            return@Surface
        }

        val sessionViewModel: SessionViewModel = viewModel(
            factory = viewModelFactory { initializer { SessionViewModel(container.auth) } },
        )
        val session by sessionViewModel.session.collectAsStateWithLifecycle()

        when (val current = session) {
            Session.Loading -> LoadingBox()

            Session.SignedOut -> {
                val authViewModel: AuthViewModel = viewModel(
                    factory = viewModelFactory { initializer { AuthViewModel(container.auth) } },
                )
                AuthScreen(authViewModel, requestGoogleToken)
            }

            is Session.SignedIn -> SignedIn(current.uid, container)
        }
    }
}

/** Switches tab the way a bottom bar should: one copy of each, state kept. */
private fun NavHostController.openTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun SignedIn(uid: String, container: AppContainer) {
    // Keyed by uid, so signing in as someone else never shows the previous
    // athlete's data.
    val trainingViewModel: TrainingViewModel = viewModel(
        key = "training-$uid",
        factory = viewModelFactory { initializer { TrainingViewModel(uid, container.activities) } },
    )
    val profileViewModel: ProfileViewModel = viewModel(
        key = "profile-$uid",
        factory = viewModelFactory {
            initializer {
                ProfileViewModel(uid, container.auth, container.garmin, container.healthConnect, container.profiles)
            }
        },
    )
    val training by trainingViewModel.state.collectAsStateWithLifecycle()
    val profile by profileViewModel.state.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val openActivity: (String) -> Unit = { id -> navController.navigate("activity/${Uri.encode(id)}") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // A single activity is a detail page: no tabs under it.
            if (currentRoute != ACTIVITY_ROUTE) {
                NavigationBar(containerColor = TriColors.navBar, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.openTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TriColors.accentText,
                                selectedTextColor = TriColors.accentText,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.TODAY.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.TODAY.route) {
                TodayScreen(
                    state = training,
                    name = profile.name,
                    onOpenProfile = { navController.openTab(Tab.PROFILE) },
                    onOpenActivity = openActivity,
                    onOpenStats = { navController.openTab(Tab.ACTIVITIES) },
                )
            }
            composable(Tab.ACTIVITIES.route) {
                ActivitiesScreen(state = training, garmin = profile.garmin, onOpenActivity = openActivity)
            }
            composable(Tab.PROFILE.route) { ProfileScreen(profileViewModel) }
            composable(ACTIVITY_ROUTE) { entry ->
                val id = entry.arguments?.getString(ACTIVITY_ID)
                val activities = (training as? TrainingState.Loaded)?.activities.orEmpty()
                if (training is TrainingState.Loading) {
                    LoadingBox()
                } else {
                    ActivityDetailScreen(
                        activity = activities.firstOrNull { it.id == id },
                        history = activities,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
