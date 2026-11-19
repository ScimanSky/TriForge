package com.triforge.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Test builds (sent with App Distribution, not from Play) cannot pass Play
 * Integrity, so they use App Check's debug provider. The secret comes from
 * local.properties and must be registered on the project; it is stored where
 * the debug provider looks for it, so the tester has nothing to do. Without
 * it the provider makes up its own secret and prints it in logcat.
 */
object AppCheckSetup {
    fun install(context: Context, app: FirebaseApp) {
        val secret = BuildConfig.APP_CHECK_DEBUG_TOKEN
        if (secret.isNotEmpty()) {
            context.getSharedPreferences("com.google.firebase.appcheck.debug.store.${app.persistenceKey}", Context.MODE_PRIVATE)
                .edit()
                .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", secret)
                .apply()
        }
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
