package com.triforge.app.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.triforge.app.R
import com.triforge.app.data.AuthRepository
import com.triforge.app.data.GoogleSignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val registering: Boolean = false,
    val busy: Boolean = false,
    @StringRes val error: Int? = null,
)

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun toggleMode() = _state.update { it.copy(registering = !it.registering, error = null) }

    fun submit(name: String, email: String, password: String) {
        val registering = _state.value.registering
        val problem = when {
            email.isBlank() || password.isBlank() -> R.string.auth_error_fields
            registering && name.isBlank() -> R.string.auth_error_name
            else -> null
        }
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                if (registering) {
                    auth.register(name.trim(), email.trim(), password)
                } else {
                    auth.signIn(email.trim(), password)
                }
                // On success the session flow switches screens; nothing to do here.
                _state.update { it.copy(busy = false) }
            } catch (error: Exception) {
                _state.update { it.copy(busy = false, error = messageFor(error)) }
            }
        }
    }

    /**
     * Marks the Google account sheet as open. False when another sign-in is
     * already running, so a double tap cannot open two.
     */
    fun beginGoogle(): Boolean {
        if (_state.value.busy) return false
        _state.update { it.copy(busy = true, error = null) }
        return true
    }

    /** Takes what the account sheet returned and finishes the sign-in. */
    fun finishGoogle(result: GoogleSignInResult) {
        when (result) {
            is GoogleSignInResult.Token -> viewModelScope.launch {
                try {
                    auth.signInWithGoogle(result.idToken)
                    _state.update { it.copy(busy = false) }
                } catch (error: Exception) {
                    _state.update { it.copy(busy = false, error = googleMessageFor(error)) }
                }
            }

            GoogleSignInResult.Cancelled -> _state.update { it.copy(busy = false) }
            GoogleSignInResult.Unavailable -> fail(R.string.auth_error_google_unavailable)
            GoogleSignInResult.NotConfigured -> fail(R.string.auth_error_google_not_configured)
            GoogleSignInResult.Failed -> fail(R.string.auth_error_google)
        }
    }

    private fun fail(@StringRes message: Int) = _state.update { it.copy(busy = false, error = message) }

    @StringRes
    private fun googleMessageFor(error: Exception): Int = when (error) {
        is FirebaseNetworkException -> R.string.auth_error_network
        is FirebaseAuthUserCollisionException -> R.string.auth_error_email_in_use
        else -> R.string.auth_error_google
    }

    @StringRes
    private fun messageFor(error: Exception): Int = when (error) {
        // Weak password extends invalid credentials, so it is checked first.
        is FirebaseAuthWeakPasswordException -> R.string.auth_error_weak_password
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException,
        -> R.string.auth_error_credentials

        is FirebaseAuthUserCollisionException -> R.string.auth_error_email_in_use
        is FirebaseNetworkException -> R.string.auth_error_network
        else -> R.string.auth_error_generic
    }
}
