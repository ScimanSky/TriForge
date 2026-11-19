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
import androidx.compose.runtime.remember
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
import com.triforge.app.ui.coach.AthleteSource
import com.triforge.app.ui.coach.CoachScreen
import com.triforge.app.ui.coach.CoachViewModel
import com.triforge.app.ui.coach.NutriScreen
import com.triforge.app.ui.coach.NutriViewModel
import com.triforge.app.ui.detail.ActivityDetailScreen
import com.triforge.app.ui.profile.EditProfileScreen
import com.triforge.app.ui.profile.ProfileScreen
import com.triforge.app.ui.profile.ProfileViewModel
import com.triforge.app.ui.recovery.CheckInScreen
import com.triforge.app.ui.recovery.RecoveryScreen
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.today.TodayScreen
import com.triforge.app.data.AvatarImage
import com.triforge.app.ui.account.AccountViewModel
import com.triforge.app.ui.account.DeviceIo
import com.triforge.app.ui.legal.LegalDoc
import com.triforge.app.ui.legal.LegalScreen
import com.triforge.app.ui.onboarding.OnboardingScreen
import com.triforge.app.ui.settings.AboutScreen
import com.triforge.app.ui.settings.PrivacyScreen
import com.triforge.app.ui.settings.SettingsScreen
import com.triforge.app.ui.settings.SourcesScreen
import com.triforge.domain.AccountGate
import android.content.Context
import androidx.compose.ui.platform.LocalContext

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    TODAY("today", R.string.nav_today, TriIcons.Home),
    ACTIVITIES("activities", R.string.nav_activities, TriIcons.Activity),
    COACH("coach", R.string.nav_coach, TriIcons.Coach),
    NUTRI("nutriai", R.string.nav_nutri, TriIcons.Nutrition),
    PROFILE("profile", R.string.nav_profile, TriIcons.Person),
}

private const val ACTIVITY_ID = "id"
private const val ACTIVITY_ROUTE = "activity/{$ACTIVITY_ID}"
private const val RECOVERY_ROUTE = "recovery"
private const val CHECK_IN_ROUTE = "check-in"
private const val EDIT_PROFILE_ROUTE = "profile/edit"
private const val SETTINGS_ROUTE = "settings"
private const val SOURCES_ROUTE = "settings/sources"
private const val PRIVACY_ROUTE = "settings/privacy"
private const val ABOUT_ROUTE = "settings/about"
private const val LEGAL_DOC = "doc"
private const val LEGAL_ROUTE = "legal/{$LEGAL_DOC}"

/** Only the tabs show the bottom bar; every other page is a detail page. */
private val TAB_ROUTES = Tab.entries.map { it.route }.toSet()

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

/** The phone-side work of the account screens, done with the app's context. */
private class AppDeviceIo(private val context: Context, private val container: AppContainer) : DeviceIo {
    override suspend fun avatarFrom(uri: Uri): ByteArray = AvatarImage.fromUri(context.contentResolver, uri)

    override fun openOutput(uri: Uri) = context.contentResolver.openOutputStream(uri)

    override suspend fun afterAccountDeleted() {
        // Nothing of the deleted account may stay on the phone, not even in the cache.
        runCatching { container.clearLocalData() }
        container.auth.signOut()
        container.restartApp()
    }
}

@Composable
private fun SignedIn(uid: String, container: AppContainer) {
    val context = LocalContext.current.applicationContext
    val accountViewModel: AccountViewModel = viewModel(
        key = "account-$uid",
        factory = viewModelFactory {
            initializer {
                AccountViewModel(uid, container.account, container.auth, container.dataExport, AppDeviceIo(context, container))
            }
        },
    )
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
    val recoveryViewModel: RecoveryViewModel = viewModel(
        key = "recovery-$uid",
        factory = viewModelFactory {
            initializer { RecoveryViewModel(uid, container.activities, container.wellness, container.profiles) }
        },
    )
    // The two coaches: separate view models driving separate agents. Both
    // read the athlete's data from the view models above.
    val athleteSource = remember(uid) {
        AthleteSource(trainingViewModel.state, recoveryViewModel.state, container.profiles.profile(uid))
    }
    val coachViewModel: CoachViewModel = viewModel(
        key = "coach-$uid",
        factory = viewModelFactory { initializer { CoachViewModel(uid, container.coachAgent, container.coaching, athleteSource) } },
    )
    val nutriViewModel: NutriViewModel = viewModel(
        key = "nutri-$uid",
        factory = viewModelFactory { initializer { NutriViewModel(uid, container.nutriAgent, container.coaching, athleteSource) } },
    )
    val training by trainingViewModel.state.collectAsStateWithLifecycle()
    val recovery by recoveryViewModel.state.collectAsStateWithLifecycle()
    val profile by profileViewModel.state.collectAsStateWithLifecycle()
    val account by accountViewModel.state.collectAsStateWithLifecycle()

    // Nothing of the athlete's data is shown before the consents are in place.
    if (!account.loaded) {
        LoadingBox()
        return
    }
    val gate = account.gate
    if (gate != AccountGate.Ready) {
        OnboardingScreen(gate = gate, account = accountViewModel, profile = profileViewModel)
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val openActivity: (String) -> Unit = { id -> navController.navigate("activity/${Uri.encode(id)}") }
    val back: () -> Unit = { navController.popBackStack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute in TAB_ROUTES) {
                NavigationBar(containerColor = TriColors.navBar, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.openTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label), maxLines = 1) },
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
                    recovery = recovery,
                    name = account.account.displayName.ifBlank { profile.name },
                    avatar = account.avatar,
                    onOpenProfile = { navController.openTab(Tab.PROFILE) },
                    onConnectSource = { navController.navigate(SOURCES_ROUTE) },
                    onOpenActivity = openActivity,
                    onOpenStats = { navController.openTab(Tab.ACTIVITIES) },
                    onOpenRecovery = { navController.navigate(RECOVERY_ROUTE) },
                    onCheckIn = { navController.navigate(CHECK_IN_ROUTE) },
                )
            }
            composable(Tab.ACTIVITIES.route) {
                ActivitiesScreen(state = training, garmin = profile.garmin, onOpenActivity = openActivity)
            }
            composable(Tab.COACH.route) {
                CoachScreen(
                    viewModel = coachViewModel,
                    aiAllowed = account.account.aiCoachAllowed,
                    profile = profile.stored,
                    onEnableAi = { accountViewModel.setAiCoach(true) },
                )
            }
            composable(Tab.NUTRI.route) {
                NutriScreen(
                    viewModel = nutriViewModel,
                    aiAllowed = account.account.aiCoachAllowed,
                    onEnableAi = { accountViewModel.setAiCoach(true) },
                )
            }
            composable(Tab.PROFILE.route) {
                ProfileScreen(
                    account = accountViewModel,
                    profile = profileViewModel,
                    training = training,
                    onEdit = { navController.navigate(EDIT_PROFILE_ROUTE) },
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                    onOpenSources = { navController.navigate(SOURCES_ROUTE) },
                )
            }
            composable(EDIT_PROFILE_ROUTE) {
                EditProfileScreen(account = accountViewModel, profile = profileViewModel, onBack = back)
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    account = accountViewModel,
                    profile = profileViewModel,
                    onBack = back,
                    onEditProfile = { navController.navigate(EDIT_PROFILE_ROUTE) },
                    onOpenSources = { navController.navigate(SOURCES_ROUTE) },
                    onOpenPrivacy = { navController.navigate(PRIVACY_ROUTE) },
                    onOpenPrivacyPolicy = { navController.navigate("legal/${LegalDoc.PRIVACY.route}") },
                    onOpenTerms = { navController.navigate("legal/${LegalDoc.TERMS.route}") },
                    onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
                )
            }
            composable(SOURCES_ROUTE) { SourcesScreen(viewModel = profileViewModel, onBack = back) }
            composable(PRIVACY_ROUTE) {
                PrivacyScreen(
                    account = accountViewModel,
                    onBack = back,
                    onOpenPrivacyPolicy = { navController.navigate("legal/${LegalDoc.PRIVACY.route}") },
                    onOpenTerms = { navController.navigate("legal/${LegalDoc.TERMS.route}") },
                )
            }
            composable(ABOUT_ROUTE) { AboutScreen(onBack = back) }
            composable(LEGAL_ROUTE) { entry ->
                val doc = LegalDoc.entries.firstOrNull { it.route == entry.arguments?.getString(LEGAL_DOC) } ?: LegalDoc.PRIVACY
                LegalScreen(doc = doc, onBack = back)
            }
            composable(RECOVERY_ROUTE) {
                RecoveryScreen(
                    state = recovery,
                    onBack = { navController.popBackStack() },
                    onCheckIn = { navController.navigate(CHECK_IN_ROUTE) },
                )
            }
            composable(CHECK_IN_ROUTE) {
                CheckInScreen(
                    initial = (recovery as? RecoveryState.Loaded)?.todayCheckIn,
                    onSave = recoveryViewModel::saveCheckIn,
                    onBack = { navController.popBackStack() },
                )
            }
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
