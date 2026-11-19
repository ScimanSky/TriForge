package com.triforge.domain

import java.time.Instant
import java.time.LocalDate

/**
 * The consents TriForge asks for. Each has its own switch and its own record,
 * because the GDPR wants consent to be specific: agreeing to one purpose never
 * implies another.
 */
enum class ConsentType(val key: String, val required: Boolean) {
    /** The athlete read the privacy notice and accepts the terms of service. */
    TERMS("terms", required = true),

    /**
     * Explicit consent to process health data (heart rate, HRV, sleep,
     * workouts, how they feel): article 9(2)(a) of the GDPR. The app cannot
     * work without it, so it is required.
     */
    HEALTH("health", required = true),

    /** Sending training and health data to the AI model that writes the coaching. Optional. */
    AI_COACH("aiCoach", required = false),
}

/** One consent as stored: what was answered, to which text, and when. */
data class Consent(
    val granted: Boolean,
    /** Version of the legal texts the answer refers to, see [LegalTexts]. */
    val version: String,
    val at: Instant? = null,
)

/**
 * Versions of the privacy notice and the terms. Raising one asks every
 * athlete to read and accept again on their next launch, which is what a
 * substantial change to the texts requires.
 */
object LegalTexts {
    const val VERSION = "2026-11-19.2"
}

/** The account side of `users/{uid}`: who the athlete is and what they agreed to. */
data class AccountInfo(
    val displayName: String = "",
    val bio: String = "",
    val birthYear: Int? = null,
    val consents: Map<ConsentType, Consent> = emptyMap(),
    val onboardingCompletedAt: Instant? = null,
) {
    fun consent(type: ConsentType): Consent? = consents[type]

    /** True when the athlete agreed to [type] for the texts in force now. */
    fun hasValidConsent(type: ConsentType): Boolean {
        val consent = consents[type] ?: return false
        return consent.granted && consent.version == LegalTexts.VERSION
    }

    /** Required consents that are missing, refused or given to older texts. */
    val missingConsents: Set<ConsentType>
        get() = ConsentType.entries.filter { it.required && !hasValidConsent(it) }.toSet()

    /**
     * The AI model may only see data while this is true. An optional consent
     * given to older texts still counts until the athlete changes it, but the
     * health consent it builds on must be valid.
     */
    val aiCoachAllowed: Boolean
        get() = hasValidConsent(ConsentType.HEALTH) && consents[ConsentType.AI_COACH]?.granted == true
}

/** Minimum age, and what can be told from a year of birth alone. */
object AgePolicy {

    /**
     * TriForge asks for 16. The Italian age of digital consent is 14
     * (art. 2-quinquies of the Privacy Code), and the GDPR default is 16; for
     * health data the stricter one is used.
     */
    const val MIN_AGE = 16

    private const val MAX_AGE = 100

    /**
     * Only the year is asked, which is all the app needs. Someone who turns
     * [MIN_AGE] later this year is let in: at worst they are one year younger,
     * still above the Italian threshold.
     */
    fun isOldEnough(birthYear: Int, today: LocalDate): Boolean = today.year - birthYear >= MIN_AGE

    /** False for years nobody using the app can be born in, which are typing mistakes. */
    fun isPlausible(birthYear: Int, today: LocalDate): Boolean =
        birthYear <= today.year && today.year - birthYear <= MAX_AGE

    fun parseYear(text: String): Int? = text.trim().takeIf { it.length == 4 }?.toIntOrNull()
}

/** The screens of the first launch, in order. */
enum class OnboardingStep { WELCOME, AGE, PRIVACY, PROFILE, SOURCES, DONE }

/** What the app must show before the athlete reaches their data. */
sealed interface AccountGate {
    /** Everything is in place. */
    data object Ready : AccountGate

    /** First launch: the whole welcome flow, from [from]. */
    data class Onboarding(val from: OnboardingStep) : AccountGate

    /**
     * The athlete went through the flow before, but the legal texts changed
     * or a required consent was withdrawn: only the privacy step again.
     */
    data object Reconsent : AccountGate
}

object Onboarding {

    fun gate(account: AccountInfo): AccountGate = when {
        account.onboardingCompletedAt == null -> AccountGate.Onboarding(
            from = when {
                account.birthYear == null -> OnboardingStep.WELCOME
                account.missingConsents.isNotEmpty() -> OnboardingStep.PRIVACY
                else -> OnboardingStep.PROFILE
            },
        )
        account.birthYear == null -> AccountGate.Onboarding(OnboardingStep.AGE)
        account.missingConsents.isNotEmpty() -> AccountGate.Reconsent
        else -> AccountGate.Ready
    }

    fun next(step: OnboardingStep): OnboardingStep =
        OnboardingStep.entries.getOrElse(step.ordinal + 1) { OnboardingStep.DONE }

    fun previous(step: OnboardingStep): OnboardingStep? =
        OnboardingStep.entries.getOrNull(step.ordinal - 1)

    /** Steps shown with a progress indicator: the welcome and the last one are not counted. */
    val countedSteps = listOf(OnboardingStep.AGE, OnboardingStep.PRIVACY, OnboardingStep.PROFILE, OnboardingStep.SOURCES)
}

/** Reads and writes the account fields of the `users/{uid}` document. */
object AccountMapper {

    const val BIO_MAX_LENGTH = 160
    const val NAME_MAX_LENGTH = 60

    /**
     * [fields] must already have Firestore timestamps turned into [Instant],
     * which keeps this module free of Firebase.
     */
    fun fromFields(fields: Map<String, Any?>): AccountInfo {
        val stored = fields["consents"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val consents = ConsentType.entries.mapNotNull { type ->
            val entry = stored[type.key] as? Map<*, *> ?: return@mapNotNull null
            val granted = entry["granted"] as? Boolean ?: return@mapNotNull null
            type to Consent(
                granted = granted,
                version = entry["version"] as? String ?: "",
                at = entry["at"] as? Instant,
            )
        }.toMap()
        return AccountInfo(
            displayName = fields["displayName"] as? String ?: "",
            bio = fields["bio"] as? String ?: "",
            birthYear = (fields["birthYear"] as? Number)?.toInt(),
            consents = consents,
            onboardingCompletedAt = fields["onboardingCompletedAt"] as? Instant,
        )
    }

    /** Trims and shortens what the athlete typed; blank stays blank. */
    fun cleanName(text: String): String = text.trim().replace(Regex("\\s+"), " ").take(NAME_MAX_LENGTH)

    fun cleanBio(text: String): String = text.trim().take(BIO_MAX_LENGTH)
}
