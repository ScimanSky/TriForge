package com.triforge.app.ui.coach

import com.triforge.app.ui.RecoveryState
import com.triforge.app.ui.TrainingState
import com.triforge.domain.AthleteProfile
import com.triforge.domain.SwimThreshold
import com.triforge.domain.coach.AthleteData
import com.triforge.domain.coach.AthleteContext
import com.triforge.domain.coach.TrainingAvailability
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The athlete's data as the coaches read it, taken from what the app has
 * already loaded for the other tabs: nothing is fetched twice.
 */
class AthleteSource(
    private val training: Flow<TrainingState>,
    private val recovery: Flow<RecoveryState>,
    private val profile: Flow<AthleteProfile>,
    private val birthYear: Flow<Int?>,
    private val availability: Flow<TrainingAvailability>,
) {
    /** The athlete's data, from the moment activities and recovery are loaded and at every change after. */
    val data: Flow<AthleteData> = combine(training, recovery, profile, birthYear, availability) { t, r, p, y, a -> Loaded(t, r, p, y, a) }
        .filter { (t, r) -> t !is TrainingState.Loading && r !is RecoveryState.Loading }
        .map { (t, r, p, y, a) ->
            val zone = ZoneId.systemDefault()
            val loaded = r as? RecoveryState.Loaded
            val today = LocalDate.now(zone)
            val activities = (t as? TrainingState.Loaded)?.activities.orEmpty()
            AthleteData(
                today = today,
                zone = zone,
                profile = p,
                activities = activities,
                loads = loaded?.loads.orEmpty(),
                recovery = loaded?.report,
                checkIn = loaded?.todayCheckIn,
                birthYear = y,
                availability = a,
                cssCheck = SwimThreshold.check(activities, p, today, zone),
            )
        }

    /** Waits until activities and recovery are loaded, then describes them as text. */
    suspend fun describe(): String = AthleteContext.describe(current(), LocalTime.now(ZoneId.systemDefault()))

    suspend fun current(): AthleteData = data.first()
}

private data class Loaded(val training: TrainingState, val recovery: RecoveryState, val profile: AthleteProfile, val birthYear: Int?, val availability: TrainingAvailability)
