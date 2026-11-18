package com.triforge.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.triforge.app.data.ActivityRepository
import com.triforge.app.data.AuthRepository
import com.triforge.app.data.GarminRepository
import com.triforge.app.data.HealthConnectRepository

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

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    val auth by lazy { AuthRepository(FirebaseAuth.getInstance(), firestore) }

    val activities by lazy { ActivityRepository(firestore) }

    val garmin by lazy { GarminRepository(firestore, auth, BuildConfig.GARMIN_SERVICE_URL) }

    val healthConnect by lazy { HealthConnectRepository(appContext, activities) }
}
