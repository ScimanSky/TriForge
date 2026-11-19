package com.triforge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triforge.app.data.ActivityRepository
import com.triforge.app.data.ProfileRepository
import com.triforge.app.data.WellnessRepository
import com.triforge.domain.AthleteProfile
import com.triforge.domain.CheckIn
import com.triforge.domain.RecoveryBalance
import com.triforge.domain.RecoveryReport
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RecoveryState {
    data object Loading : RecoveryState
    data object Failed : RecoveryState
    data class Loaded(val report: RecoveryReport, val todayCheckIn: CheckIn?) : RecoveryState
}

/**
 * The load-recovery balance, recomputed whenever activities, recovery data,
 * check-ins or the profile thresholds change.
 */
class RecoveryViewModel(
    private val uid: String,
    activities: ActivityRepository,
    private val wellness: WellnessRepository,
    profiles: ProfileRepository,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val since = LocalDate.now(zone).minusDays(WELLNESS_DAYS)

    val state: StateFlow<RecoveryState> = combine(
        activities.activities(uid, TrainingViewModel.windowStart()),
        wellness.wellness(uid, since),
        wellness.checkIns(uid, since),
        profiles.profile(uid).onStart { emit(AthleteProfile()) },
    ) { activityList, days, checkIns, profile ->
        val today = LocalDate.now(zone)
        val report = RecoveryBalance.compute(activityList, days, checkIns, profile, today, zone)
        RecoveryState.Loaded(report, checkIns.firstOrNull { it.date == today }) as RecoveryState
    }
        .catch { emit(RecoveryState.Failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecoveryState.Loading)

    fun saveCheckIn(checkIn: CheckIn) {
        viewModelScope.launch { runCatching { wellness.saveCheckIn(uid, checkIn) } }
    }

    private companion object {
        /** 60-day baselines plus 42 days of history plus the persistence warm-up. */
        const val WELLNESS_DAYS = 130L
    }
}
