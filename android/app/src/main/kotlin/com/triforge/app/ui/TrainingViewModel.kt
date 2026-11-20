package com.triforge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triforge.app.data.ActivityRepository
import com.triforge.domain.Activity
import com.triforge.domain.SwimGearMarks
import com.triforge.domain.TrainingStats
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TrainingState {
    data object Loading : TrainingState
    data object Failed : TrainingState
    data class Loaded(val activities: List<Activity>) : TrainingState
}

/** The athlete's recent activities, shared by the Today and Activities tabs. */
class TrainingViewModel(private val uid: String, private val repository: ActivityRepository) : ViewModel() {

    val state: StateFlow<TrainingState> = repository
        .activities(uid, since = windowStart())
        .map<List<Activity>, TrainingState> { TrainingState.Loaded(it) }
        .catch { emit(TrainingState.Failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingState.Loading)

    /**
     * Stores what the athlete said about the equipment of a swim. The
     * activities come back with it by themselves, also offline: Firestore
     * applies the write to its cache first. A write that fails leaves
     * things as they were, and the question stays open.
     */
    fun markSwimGear(activityId: String, marks: SwimGearMarks) {
        viewModelScope.launch { runCatching { repository.setSwimGear(uid, activityId, marks) } }
    }

    companion object {
        const val WINDOW_WEEKS = 16L

        /** Monday of the week [WINDOW_WEEKS] weeks back, so whole weeks are loaded. */
        fun windowStart(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()) =
            TrainingStats.weekStart(today).minusWeeks(WINDOW_WEEKS - 1).atStartOfDay(zone).toInstant()
    }
}
