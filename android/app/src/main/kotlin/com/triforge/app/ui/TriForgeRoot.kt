package com.triforge.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.triforge.app.AppContainer
import com.triforge.app.R
import com.triforge.app.ui.activities.ActivitiesScreen
import com.triforge.app.ui.auth.AuthScreen
import com.triforge.app.ui.auth.AuthViewModel
import com.triforge.app.ui.common.CenteredMessage
import com.triforge.app.ui.profile.ProfileScreen
import com.triforge.app.ui.profile.ProfileViewModel
import com.triforge.app.ui.today.TodayScreen

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    TODAY("today", R.string.nav_today, Icons.Filled.Home),
    ACTIVITIES("activities", R.string.nav_activities, Icons.AutoMirrored.Filled.List),
    PROFILE("profile", R.string.nav_profile, Icons.Filled.Person),
}

@Composable
fun TriForgeRoot(container: AppContainer) {
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
            Session.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            Session.SignedOut -> {
                val authViewModel: AuthViewModel = viewModel(
                    factory = viewModelFactory { initializer { AuthViewModel(container.auth) } },
                )
                AuthScreen(authViewModel)
            }

            is Session.SignedIn -> SignedIn(current.uid, container)
        }
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
            initializer { ProfileViewModel(uid, container.auth, container.garmin, container.healthConnect) }
        },
    )
    val training by trainingViewModel.state.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.TODAY.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.TODAY.route) { TodayScreen(training) }
            composable(Tab.ACTIVITIES.route) { ActivitiesScreen(training) }
            composable(Tab.PROFILE.route) { ProfileScreen(profileViewModel) }
        }
    }
}
