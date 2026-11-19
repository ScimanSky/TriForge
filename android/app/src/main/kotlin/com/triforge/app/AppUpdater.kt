package com.triforge.app

import android.os.SystemClock
import android.util.Log
import com.google.firebase.appdistribution.FirebaseAppDistribution

/**
 * Looks for a newer test build on Firebase App Distribution when the app
 * comes to the foreground, and offers to install it.
 *
 * The SDK shows its own dialogs: the first time it asks the tester to sign
 * in with the Google account that receives the builds, then, when a newer
 * build exists, it downloads it and hands it to Android's installer. Android
 * always asks for a confirmation before installing; no app can skip that.
 */
class AppUpdater(private val now: () -> Long = SystemClock::elapsedRealtime) {

    private val gate = UpdateCheckGate(MIN_INTERVAL_MS)

    /** Call from the main thread, with Firebase configured. */
    fun checkOnOpen() {
        if (!gate.tryStart(now())) return
        try {
            FirebaseAppDistribution.getInstance()
                .updateIfNewReleaseAvailable()
                // Completes when there is nothing newer, when the tester says
                // no, or on an error. An update that installs restarts the app.
                .addOnCompleteListener { gate.finish(now()) }
        } catch (error: RuntimeException) {
            Log.w(TAG, "update check not started: ${error.javaClass.simpleName}")
            gate.finish(now())
        }
    }

    private companion object {
        const val TAG = "AppUpdater"

        /** Coming back to the app within this time does not ask again. */
        const val MIN_INTERVAL_MS = 10 * 60 * 1000L
    }
}

/**
 * Lets one update check run at a time, and not more often than
 * [minIntervalMs]. Without it, every return to the app (from the sign-in
 * page, from the installer, from another app) would start another check and
 * show the same dialog again.
 */
class UpdateCheckGate(private val minIntervalMs: Long) {

    private var running = false
    private var lastFinishedAt: Long? = null

    /** True when a check may start now; the caller must then call [finish]. */
    fun tryStart(now: Long): Boolean {
        if (running) return false
        val last = lastFinishedAt
        if (last != null && now - last < minIntervalMs) return false
        running = true
        return true
    }

    fun finish(now: Long) {
        running = false
        lastFinishedAt = now
    }
}
