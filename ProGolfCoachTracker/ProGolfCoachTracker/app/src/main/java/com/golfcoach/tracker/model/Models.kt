package com.golfcoach.tracker.model

import kotlinx.serialization.Serializable

@Serializable
data class WeeklyPlan(
    val weekStartDate: String, // ISO yyyy-MM-dd (local)
    val weekLabel: String? = null,
    val days: List<DayPlan>
)

@Serializable
data class DayPlan(
    val name: String, // e.g., "Monday"
    val drills: List<DrillPlan>
)

@Serializable
data class DrillPlan(
    val id: String,
    val name: String,
    val club: String? = null, // e.g., "7i", "Driver"
    val targetDesc: String? = null, // instructions/target
    val scoringType: ScoringType = ScoringType.PROXIMITY_CM,
    val shotsPlanned: Int = 10,
    val notes: String? = null
)

@Serializable
enum class ScoringType {
    PROXIMITY_CM, // numeric (centimetres from hole/target)
    MAKE_MISS,    // boolean success
    TIME_SEC      // numeric (seconds) for speed/pressure
}

@Serializable
data class WeeklyResults(
    val plan: WeeklyPlan,
    val days: List<DayResults>
)

@Serializable
data class DayResults(
    val name: String,
    val drills: List<DrillResults>
)

@Serializable
data class DrillResults(
    val drillId: String,
    val shots: List<ShotEntry>
)

@Serializable
data class ShotEntry(
    val index: Int, // 1-based shot number
    val valueNumber: Double? = null, // proximity cm OR time sec
    val valueBool: Boolean? = null, // make = true / miss = false
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null
)
