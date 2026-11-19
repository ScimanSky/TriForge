package com.triforge.app.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

sealed interface GoogleSignInResult {
    /** The athlete picked an account. The token goes to Firebase Auth. */
    data class Token(val idToken: String) : GoogleSignInResult

    /** The athlete closed the account sheet. Not an error. */
    data object Cancelled : GoogleSignInResult

    /** No Google account on the device, or Google Play services cannot show one. */
    data object Unavailable : GoogleSignInResult

    /** The build has no OAuth client: Google is not enabled for the Firebase project. */
    data object NotConfigured : GoogleSignInResult

    data object Failed : GoogleSignInResult
}

/** Shows Google's account sheet and returns the ID token of the chosen account. */
class GoogleSignIn {

    /** Needs the activity, not the application: the sheet is drawn over it. */
    suspend fun requestIdToken(activity: Activity): GoogleSignInResult {
        val clientId = webClientId(activity) ?: return GoogleSignInResult.NotConfigured
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleSignInResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleSignInResult.Failed
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleSignInResult.Unavailable
        } catch (_: GetCredentialException) {
            GoogleSignInResult.Failed
        } catch (_: GoogleIdTokenParsingException) {
            GoogleSignInResult.Failed
        }
    }

    /**
     * The web client id that the google-services plugin generates from
     * google-services.json. Looked up by name because the resource exists
     * only when the Firebase project has Google sign-in enabled, and the app
     * must still compile without it (CI, a fresh checkout).
     */
    @SuppressLint("DiscouragedApi")
    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }
}
