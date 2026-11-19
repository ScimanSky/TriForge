package com.triforge.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {

    /** The signed-in athlete's id, or null when signed out. */
    val userId: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    val displayName: String
        get() = auth.currentUser?.displayName.orEmpty()

    val email: String
        get() = auth.currentUser?.email.orEmpty()

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun register(name: String, email: String, password: String) {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user ?: return
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
        firestore.collection("users").document(user.uid)
            .set(
                mapOf("displayName" to name, "createdAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            .await()
    }

    /**
     * Signs in with a Google ID token, creating the account the first time.
     * Sign-in and registration are the same step with Google.
     */
    suspend fun signInWithGoogle(idToken: String) {
        val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        val user = result.user ?: return
        if (result.additionalUserInfo?.isNewUser != true) return
        firestore.collection("users").document(user.uid)
            .set(
                mapOf(
                    "displayName" to user.displayName.orEmpty(),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    /** True when the athlete signs in with email and password, so a reset email makes sense. */
    val usesPassword: Boolean
        get() = auth.currentUser?.providerData.orEmpty().any { it.providerId == "password" }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** Keeps the name on the login in step with the one on the profile. */
    suspend fun updateDisplayName(name: String) {
        val user = auth.currentUser ?: return
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
    }

    /**
     * Deletes the login from the phone. Only a fallback: the service deletes
     * it with the data. Firebase refuses when the last sign-in is too old.
     */
    suspend fun deleteLogin() {
        auth.currentUser?.delete()?.await()
    }

    fun signOut() = auth.signOut()

    /** A fresh ID token for calls to the Garmin service. */
    suspend fun idToken(): String? = auth.currentUser?.getIdToken(false)?.await()?.token
}
