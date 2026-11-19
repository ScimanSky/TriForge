package com.triforge.app.ai

import com.triforge.domain.coach.SportCoachPrompts
import com.triforge.domain.coach.TriRegia
import com.triforge.domain.coach.Zones
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * The schemas are built when the app opens, and the library throws on one
 * that names an optional field it does not define: the app then closes
 * before its first screen. Every agent's schemas are built here the way
 * [com.triforge.app.AppContainer] builds them.
 */
class CoachSchemasTest {

    @Test
    fun `NutriAI's schemas can be built, without a handoff`() {
        assertNotNull(CoachSchemas.chatReply(CoachSchemas.nutritionDay, handoffs = emptyList()))
    }

    @Test
    fun `each sport coach's schemas can be built, with its handoffs`() {
        TriRegia.SPORTS.forEach { sport ->
            val week = CoachSchemas.sportWeek(Zones.keys(sport))

            assertNotNull(CoachSchemas.chatReply(week, SportCoachPrompts.handoffs(sport)))
        }
    }

    @Test
    fun `NutriAI's schemas for one dish can be built`() {
        assertNotNull(CoachSchemas.dishAlternatives)
        assertNotNull(CoachSchemas.dishEstimate)
    }
}
