package com.triforge.app

import android.content.Context
import android.content.Intent
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.triforge.app.ai.CoachAgent
import com.triforge.app.ai.CoachSchemas
import com.triforge.app.ai.GeminiCoachAgent
import com.triforge.app.data.AccountRepository
import com.triforge.app.data.ActivityRepository
import com.triforge.app.data.AuthRepository
import com.triforge.app.data.CloudService
import com.triforge.app.data.CoachRepository
import com.triforge.app.data.DataExport
import com.triforge.app.data.GarminRepository
import com.triforge.app.data.HealthConnectRepository
import com.triforge.app.data.ProfileRepository
import com.triforge.app.data.WellnessRepository
import com.triforge.domain.coach.CoachMapper
import com.triforge.domain.coach.CoachRole
import com.triforge.domain.coach.NutritionDay
import com.triforge.domain.coach.SportCoachPrompts
import com.triforge.domain.coach.SportProposal
import com.triforge.domain.coach.TriRegia
import com.triforge.domain.coach.Zones
import com.triforge.domain.Sport
import com.triforge.domain.TrainingStats
import java.time.LocalDate
import kotlinx.coroutines.tasks.await

/**
 * Builds the app's long-lived objects by hand. The graph is small enough that
 * a dependency injection framework would add more than it removes.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /**
     * False when the app was built without google-services.json. Nothing
     * below may be touched in that case: Firebase would throw.
     */
    val firebaseConfigured: Boolean = FirebaseApp.getApps(appContext).isNotEmpty()

    init {
        // Before any Firebase call: AI Logic refuses requests without App Check.
        if (firebaseConfigured) AppCheckSetup.install(appContext, FirebaseApp.getInstance())
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    /**
     * Empties Firestore's copy of the data on the phone, after the account is
     * deleted. The instance cannot be used afterwards: the app restarts.
     */
    suspend fun clearLocalData() {
        firestore.terminate().await()
        firestore.clearPersistence().await()
    }

    /** Starts the app again in a fresh process, as after a cold launch. */
    fun restartApp() {
        val launch = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName) ?: return
        val component = launch.component ?: return
        appContext.startActivity(Intent.makeRestartActivityTask(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Runtime.getRuntime().exit(0)
    }

    val auth by lazy { AuthRepository(FirebaseAuth.getInstance(), firestore) }

    val activities by lazy { ActivityRepository(firestore) }

    val profiles by lazy { ProfileRepository(firestore) }

    private val service by lazy { CloudService(auth, BuildConfig.GARMIN_SERVICE_URL) }

    val garmin by lazy { GarminRepository(firestore, service) }

    val account by lazy { AccountRepository(firestore, auth, service) }

    val dataExport by lazy { DataExport(firestore, auth) }

    val wellness by lazy { WellnessRepository(firestore) }

    val coaching by lazy { CoachRepository(firestore) }

    /**
     * The three sport coaches that write the week together: one agent per
     * sport, each with its own instructions. They never call each other: the
     * app carries their proposals from one to the others.
     */
    val sportCoaches: Map<Sport, CoachAgent<SportProposal>> by lazy {
        TriRegia.SPORTS.associateWith { sport ->
            GeminiCoachAgent(
                CoachRole.COACH,
                BuildConfig.AI_MODEL,
                BuildConfig.AI_LOCATION,
                CoachSchemas.sportWeek(Zones.keys(sport)),
                system = SportCoachPrompts.system(sport),
                handoffs = SportCoachPrompts.handoffs(sport),
            ) { fields ->
                CoachMapper.sportProposalFromFields(fields, sport, TrainingStats.weekStart(LocalDate.now()))
            }
        }
    }

    /** NutriAI, a separate agent: it meets the coaches only on the shared board in Firestore. */
    val nutriAgent: CoachAgent<NutritionDay> by lazy {
        GeminiCoachAgent(CoachRole.NUTRI_AI, BuildConfig.AI_MODEL, BuildConfig.AI_LOCATION, CoachSchemas.nutritionDay) {
            CoachMapper.nutritionDayFromFields(it, LocalDate.now())
        }
    }

    /** In-app updates of test builds. Use only when [firebaseConfigured]. */
    val updater by lazy { AppUpdater() }

    val healthConnect by lazy { HealthConnectRepository(appContext, activities, wellness) }
}
