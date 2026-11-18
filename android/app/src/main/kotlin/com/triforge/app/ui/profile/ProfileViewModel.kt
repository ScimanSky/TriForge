package com.triforge.app.ui.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triforge.app.R
import com.triforge.app.data.AuthRepository
import com.triforge.app.data.GarminException
import com.triforge.app.data.GarminRepository
import com.triforge.app.data.GarminStatus
import com.triforge.app.data.HealthConnectAvailability
import com.triforge.app.data.HealthConnectRepository
import com.triforge.app.ui.TrainingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A message for the athlete: a string resource and its format arguments. */
data class Notice(@StringRes val text: Int, val args: List<Any> = emptyList(), val isError: Boolean = false)

data class ProfileUiState(
    val name: String = "",
    val email: String = "",
    val garminConfigured: Boolean = false,
    val garmin: GarminStatus = GarminStatus(),
    val garminAwaitingCode: Boolean = false,
    val garminBusy: Boolean = false,
    val garminNotice: Notice? = null,
    val healthConnect: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    val healthConnectGranted: Boolean = false,
    val healthConnectBusy: Boolean = false,
    val healthConnectNotice: Notice? = null,
)

class ProfileViewModel(
    private val uid: String,
    private val auth: AuthRepository,
    private val garmin: GarminRepository,
    private val healthConnect: HealthConnectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileUiState(
            name = auth.displayName,
            email = auth.email,
            garminConfigured = garmin.configured,
            healthConnect = healthConnect.availability(),
        ),
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    val healthPermissions: Set<String> get() = healthConnect.permissions

    init {
        viewModelScope.launch {
            garmin.status(uid).collect { status -> _state.update { it.copy(garmin = status) } }
        }
        refreshHealthConnect()
    }

    // -- Garmin -----------------------------------------------------------

    fun connectGarmin(email: String, password: String) = garminCall {
        val needsCode = garmin.connect(email.trim(), password)
        _state.update { it.copy(garminAwaitingCode = needsCode) }
        if (!needsCode) syncNow()
    }

    fun submitGarminCode(code: String) = garminCall {
        garmin.submitMfaCode(code.trim())
        _state.update { it.copy(garminAwaitingCode = false) }
        syncNow()
    }

    fun syncGarmin() = garminCall { syncNow() }

    fun disconnectGarmin() = garminCall {
        garmin.disconnect()
        _state.update { it.copy(garminAwaitingCode = false) }
    }

    private suspend fun syncNow() {
        val count = garmin.sync()
        _state.update { it.copy(garminNotice = Notice(R.string.garmin_synced, listOf(count))) }
    }

    private fun garminCall(block: suspend () -> Unit) {
        if (_state.value.garminBusy) return
        _state.update { it.copy(garminBusy = true, garminNotice = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: GarminException) {
                if (error.code == "mfa_expired") _state.update { it.copy(garminAwaitingCode = false) }
                _state.update { it.copy(garminNotice = noticeFor(error)) }
            } catch (error: Exception) {
                _state.update { it.copy(garminNotice = Notice(R.string.garmin_error_generic, isError = true)) }
            } finally {
                _state.update { it.copy(garminBusy = false) }
            }
        }
    }

    private fun noticeFor(error: GarminException): Notice = when (error.code) {
        "garmin_bad_credentials" -> Notice(R.string.garmin_error_credentials, isError = true)
        "garmin_bad_mfa_code" -> Notice(R.string.garmin_error_mfa_code, isError = true)
        "mfa_expired" -> Notice(R.string.garmin_error_mfa_expired, isError = true)
        "garmin_rate_limited" -> Notice(
            R.string.garmin_error_rate_limited,
            // Round up, so the athlete is never told to retry too early.
            listOf(((error.retryAfterSec ?: 900) + 59) / 60),
            isError = true,
        )
        "garmin_unavailable" -> Notice(R.string.garmin_error_unavailable, isError = true)
        "garmin_not_connected" -> Notice(R.string.garmin_error_not_connected, isError = true)
        "network" -> Notice(R.string.garmin_error_network, isError = true)
        else -> Notice(R.string.garmin_error_generic, isError = true)
    }

    // -- Health Connect ---------------------------------------------------

    /** Re-reads availability and permissions, for example after the permission dialog. */
    fun refreshHealthConnect() {
        val availability = healthConnect.availability()
        _state.update { it.copy(healthConnect = availability) }
        if (availability != HealthConnectAvailability.AVAILABLE) return
        viewModelScope.launch {
            val granted = runCatching { healthConnect.hasPermissions() }.getOrDefault(false)
            _state.update { it.copy(healthConnectGranted = granted) }
        }
    }

    fun importFromHealthConnect() {
        if (_state.value.healthConnectBusy) return
        _state.update { it.copy(healthConnectBusy = true, healthConnectNotice = null) }
        viewModelScope.launch {
            val notice = try {
                val count = healthConnect.importWorkouts(uid, TrainingViewModel.windowStart())
                Notice(R.string.hc_imported, listOf(count))
            } catch (error: Exception) {
                Notice(R.string.hc_error, isError = true)
            }
            _state.update { it.copy(healthConnectBusy = false, healthConnectNotice = notice) }
        }
    }

    fun signOut() = auth.signOut()
}
