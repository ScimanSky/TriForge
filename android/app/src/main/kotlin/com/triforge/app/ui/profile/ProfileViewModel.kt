package com.triforge.app.ui.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triforge.app.R
import com.triforge.app.data.AuthRepository
import com.triforge.app.data.ServiceException
import com.triforge.app.data.GarminRepository
import com.triforge.app.data.GarminStatus
import com.triforge.app.data.HealthConnectAvailability
import com.triforge.app.data.HealthConnectRepository
import com.triforge.app.data.ProfileRepository
import com.triforge.app.ui.TrainingViewModel
import com.triforge.domain.AthleteProfile
import com.triforge.domain.ProfileField
import com.triforge.domain.ProfileForm
import com.triforge.domain.ProfileFormResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
    /** The profile as last stored, for the read-only profile page. */
    val stored: AthleteProfile = AthleteProfile(),
    val form: ProfileForm = ProfileForm(),
    /** True once the athlete has typed something that is not saved yet. */
    val formDirty: Boolean = false,
    val formErrors: Set<ProfileField> = emptySet(),
    val formSaving: Boolean = false,
    val formNotice: Notice? = null,
)

class ProfileViewModel(
    private val uid: String,
    private val auth: AuthRepository,
    private val garmin: GarminRepository,
    private val healthConnect: HealthConnectRepository,
    private val profiles: ProfileRepository,
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
        viewModelScope.launch {
            profiles.profile(uid)
                // Without the stored profile the form simply starts empty.
                .catch { }
                .collect { profile ->
                    // What the athlete is typing wins over what is stored.
                    _state.update {
                        if (it.formDirty) it.copy(stored = profile) else it.copy(stored = profile, form = ProfileForm.of(profile))
                    }
                }
        }
        refreshHealthConnect()
    }

    // -- Athlete profile --------------------------------------------------

    fun editForm(change: (ProfileForm) -> ProfileForm) {
        _state.update {
            it.copy(form = change(it.form), formDirty = true, formErrors = emptySet(), formNotice = null)
        }
    }

    /** Throws away what was typed and not saved, as when leaving the edit page. */
    fun discardForm() {
        _state.update {
            it.copy(form = ProfileForm.of(it.stored), formDirty = false, formErrors = emptySet(), formNotice = null)
        }
    }

    fun saveProfile() {
        val current = _state.value
        if (current.formSaving) return
        when (val result = current.form.parse()) {
            is ProfileFormResult.Invalid -> _state.update {
                it.copy(formErrors = result.fields, formNotice = Notice(R.string.profile_error_fields, isError = true))
            }

            is ProfileFormResult.Valid -> {
                _state.update { it.copy(formSaving = true, formNotice = null) }
                viewModelScope.launch {
                    val notice = try {
                        profiles.save(uid, result.profile)
                        Notice(R.string.profile_saved)
                    } catch (error: Exception) {
                        Notice(R.string.profile_error_save, isError = true)
                    }
                    _state.update {
                        if (notice.isError) {
                            it.copy(formSaving = false, formNotice = notice)
                        } else {
                            // Shown back as stored, so "1.48" becomes "1:48".
                            it.copy(
                                form = ProfileForm.of(result.profile),
                                formSaving = false,
                                formDirty = false,
                                formNotice = notice,
                            )
                        }
                    }
                }
            }
        }
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
            } catch (error: ServiceException) {
                if (error.code == "mfa_expired") _state.update { it.copy(garminAwaitingCode = false) }
                _state.update { it.copy(garminNotice = noticeFor(error)) }
            } catch (error: Exception) {
                _state.update { it.copy(garminNotice = Notice(R.string.garmin_error_generic, isError = true)) }
            } finally {
                _state.update { it.copy(garminBusy = false) }
            }
        }
    }

    private fun noticeFor(error: ServiceException): Notice = when (error.code) {
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
                // Recovery data is a bonus: a failure here keeps the workouts.
                runCatching { healthConnect.importWellness(uid, TrainingViewModel.windowStart()) }
                Notice(R.string.hc_imported, listOf(count))
            } catch (error: Exception) {
                Notice(R.string.hc_error, isError = true)
            }
            _state.update { it.copy(healthConnectBusy = false, healthConnectNotice = notice) }
        }
    }

    fun signOut() = auth.signOut()
}
