package com.triforge.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triforge.app.R
import com.triforge.app.data.ProfileRepository
import com.triforge.domain.AthleteProfile
import com.triforge.domain.coach.TrainingAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeekSetupState(
    val loaded: Boolean = false,
    val profile: AthleteProfile = AthleteProfile(),
    val availability: TrainingAvailability = TrainingAvailability(),
    val saving: Boolean = false,
    val notice: Notice? = null,
)

/** The "Giorni, orari e gara" page (board 8): the race, the hours and the days the regia starts from. */
class WeekSetupViewModel(
    private val uid: String,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WeekSetupState())
    val state: StateFlow<WeekSetupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(profiles.profile(uid), profiles.availability(uid)) { profile, availability -> profile to availability }
                .catch { _state.update { it.copy(loaded = true) } }
                .collect { (profile, availability) ->
                    _state.update { it.copy(loaded = true, profile = profile, availability = availability) }
                }
        }
    }

    fun save(profile: AthleteProfile, availability: TrainingAvailability, onDone: () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, notice = null) }
        viewModelScope.launch {
            val ok = try {
                profiles.saveWeek(uid, profile, availability)
                true
            } catch (error: Exception) {
                false
            }
            _state.update {
                it.copy(saving = false, notice = if (ok) null else Notice(R.string.profile_error_save, isError = true))
            }
            if (ok) onDone()
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }
}
