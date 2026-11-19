package com.triforge.app.ui.coach

import com.triforge.app.ui.RecoveryState
import com.triforge.app.ui.TrainingState
import com.triforge.domain.AthleteProfile
import com.triforge.domain.coach.AthleteData
import com.triforge.domain.coach.AthleteContext
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * The athlete's data as the coaches read it, taken from what the app has
 * already loaded for the other tabs: nothing is fetched twice.
 */
class AthleteSource(
    private val training: Flow<TrainingState>,
    private val recovery: Flow<RecoveryState>,
    private val profile: Flow<AthleteProfile>,
    private val birthYear: Flow<Int?>,
) {
    /** Waits until activities and recovery are loaded, then describes them as text. */
    suspend fun describe(): String = AthleteContext.describe(current())

    suspend fun current(): AthleteData {
        val zone = ZoneId.systemDefault()
        return combine(training, recovery, profile, birthYear) { t, r, p, y -> Loaded(t, r, p, y) }
            .first { (t, r) -> t !is TrainingState.Loading && r !is RecoveryState.Loading }
            .let { (t, r, p, y) ->
                val loaded = r as? RecoveryState.Loaded
                AthleteData(
                    today = LocalDate.now(zone),
                    zone = zone,
                    profile = p,
                    activities = (t as? TrainingState.Loaded)?.activities.orEmpty(),
                    loads = loaded?.loads.orEmpty(),
                    recovery = loaded?.report,
                    checkIn = loaded?.todayCheckIn,
                    birthYear = y,
                )
            }
    }
}

private data class Loaded(val training: TrainingState, val recovery: RecoveryState, val profile: AthleteProfile, val birthYear: Int?)
