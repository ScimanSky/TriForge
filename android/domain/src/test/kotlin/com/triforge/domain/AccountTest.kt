package com.triforge.domain

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountTest {

    private val now = Instant.parse("2026-11-19T10:00:00Z")
    private val today = LocalDate.of(2026, 11, 19)

    private fun consent(granted: Boolean = true, version: String = LegalTexts.VERSION) =
        Consent(granted = granted, version = version, at = now)

    private val complete = AccountInfo(
        displayName = "Ada Rossi",
        birthYear = 1990,
        consents = mapOf(ConsentType.TERMS to consent(), ConsentType.HEALTH to consent()),
        onboardingCompletedAt = now,
    )

    @Test
    fun `an athlete with everything in place goes straight to the app`() {
        assertEquals(AccountGate.Ready, Onboarding.gate(complete))
    }

    @Test
    fun `a new account starts from the welcome screen`() {
        assertEquals(AccountGate.Onboarding(OnboardingStep.WELCOME), Onboarding.gate(AccountInfo()))
    }

    @Test
    fun `an interrupted first launch resumes where it stopped`() {
        val afterAge = AccountInfo(birthYear = 1990)
        val afterPrivacy = complete.copy(onboardingCompletedAt = null)

        assertEquals(AccountGate.Onboarding(OnboardingStep.PRIVACY), Onboarding.gate(afterAge))
        assertEquals(AccountGate.Onboarding(OnboardingStep.PROFILE), Onboarding.gate(afterPrivacy))
    }

    @Test
    fun `new legal texts ask only for the consents again`() {
        val old = complete.copy(
            consents = mapOf(ConsentType.TERMS to consent(version = "2026-01-01"), ConsentType.HEALTH to consent()),
        )

        assertEquals(setOf(ConsentType.TERMS), old.missingConsents)
        assertEquals(AccountGate.Reconsent, Onboarding.gate(old))
    }

    @Test
    fun `a withdrawn health consent blocks the app`() {
        val withdrawn = complete.copy(consents = complete.consents + (ConsentType.HEALTH to consent(granted = false)))

        assertEquals(AccountGate.Reconsent, Onboarding.gate(withdrawn))
    }

    @Test
    fun `the optional AI consent never blocks the app`() {
        val refused = complete.copy(consents = complete.consents + (ConsentType.AI_COACH to consent(granted = false)))

        assertEquals(AccountGate.Ready, Onboarding.gate(refused))
        assertFalse(refused.aiCoachAllowed)
    }

    @Test
    fun `the AI coach needs both its own consent and the health consent`() {
        val ai = ConsentType.AI_COACH to consent()
        val withAi = complete.copy(consents = complete.consents + ai)
        val withoutHealth = withAi.copy(consents = withAi.consents + (ConsentType.HEALTH to consent(granted = false)))

        assertTrue(withAi.aiCoachAllowed)
        assertFalse(withoutHealth.aiCoachAllowed)
    }

    @Test
    fun `age is checked on the year alone`() {
        assertTrue(AgePolicy.isOldEnough(2010, today))
        assertFalse(AgePolicy.isOldEnough(2011, today))
        assertTrue(AgePolicy.isPlausible(1950, today))
        assertFalse(AgePolicy.isPlausible(1900, today))
        assertFalse(AgePolicy.isPlausible(2030, today))
        assertEquals(1990, AgePolicy.parseYear(" 1990 "))
        assertNull(AgePolicy.parseYear("90"))
        assertNull(AgePolicy.parseYear("19x0"))
    }

    @Test
    fun `stored fields are read back`() {
        val fields = mapOf(
            "displayName" to "Ada Rossi",
            "bio" to "Age grouper",
            "birthYear" to 1990L,
            "onboardingCompletedAt" to now,
            "consents" to mapOf(
                "terms" to mapOf("granted" to true, "version" to LegalTexts.VERSION, "at" to now),
                "health" to mapOf("granted" to true, "version" to LegalTexts.VERSION, "at" to now),
                "aiCoach" to mapOf("granted" to false, "version" to LegalTexts.VERSION, "at" to now),
                "unknown" to mapOf("granted" to true),
            ),
        )

        val account = AccountMapper.fromFields(fields)

        assertEquals("Ada Rossi", account.displayName)
        assertEquals(1990, account.birthYear)
        assertEquals(now, account.onboardingCompletedAt)
        assertEquals(3, account.consents.size)
        assertFalse(account.consent(ConsentType.AI_COACH)!!.granted)
        assertEquals(AccountGate.Ready, Onboarding.gate(account))
    }

    @Test
    fun `broken fields are ignored rather than trusted`() {
        val account = AccountMapper.fromFields(
            mapOf("consents" to mapOf("health" to mapOf("granted" to "yes")), "birthYear" to "1990"),
        )

        assertTrue(account.consents.isEmpty())
        assertNull(account.birthYear)
    }

    @Test
    fun `names and bios are tidied`() {
        assertEquals("Ada Rossi", AccountMapper.cleanName("  Ada   Rossi "))
        assertEquals(AccountMapper.BIO_MAX_LENGTH, AccountMapper.cleanBio("x".repeat(500)).length)
    }

    @Test
    fun `steps follow each other`() {
        assertEquals(OnboardingStep.AGE, Onboarding.next(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.DONE, Onboarding.next(OnboardingStep.DONE))
        assertNull(Onboarding.previous(OnboardingStep.WELCOME))
    }
}
