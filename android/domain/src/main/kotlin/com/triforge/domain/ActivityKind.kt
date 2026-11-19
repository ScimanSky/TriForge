package com.triforge.domain

/**
 * What a session outside the three disciplines and the gym was, as far as
 * its load and its energy go. The watch records many activity types; these
 * are the groups that train the body differently enough to matter.
 */
enum class OtherKind(val key: String) {
    /** Walking, at any pace. */
    WALK("walk"),

    /** Hiking and mountaineering: hours on the legs, often with climbing. */
    HIKE("hike"),

    /** Yoga, pilates, stretching, breathing: almost no load. */
    MOBILITY("mobility"),

    /** Cardio machines and classes: elliptical, rowing, stairs, HIIT, indoor cardio. */
    CARDIO("cardio"),

    /** Anything else: a team sport, skiing, an activity the watch could not name. */
    GENERIC("other"),
    ;

    companion object {
        fun of(key: Any?): OtherKind? = entries.firstOrNull { it.key == key }
    }
}

/** Reads the sport and the kind of an activity from what its source called it. */
object ActivityTypes {

    private val STRENGTH = setOf("strength_training", "weight_training", "weightlifting", "calisthenics")
    private val MOBILITY = listOf("yoga", "pilates", "stretching", "breathwork", "meditation", "mobility")
    private val HIKE = listOf("hiking", "mountaineering", "rucking", "snow_shoe")
    private val CARDIO = listOf("cardio", "hiit", "elliptical", "stair", "rowing", "jump_rope", "fitness_equipment", "boxing")

    /** Whether the source's type is a gym session, whatever its `sport` field says. */
    fun isStrength(type: String?): Boolean = type?.lowercase() in STRENGTH

    /**
     * The kind of a session that is not swim, bike, run or gym, from the
     * source's type key. Hiking is checked before walking: "hiking" is not a
     * walk, though both end the same way.
     */
    fun otherKind(type: String?): OtherKind {
        val key = type?.lowercase().orEmpty()
        return when {
            MOBILITY.any { it in key } -> OtherKind.MOBILITY
            HIKE.any { it in key } -> OtherKind.HIKE
            "walk" in key -> OtherKind.WALK
            CARDIO.any { it in key } -> OtherKind.CARDIO
            else -> OtherKind.GENERIC
        }
    }
}
