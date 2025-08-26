package com.golfcoach.tracker.data

import android.content.Context
import com.golfcoach.tracker.model.*
import kotlinx.serialization.json.Json
import java.io.File

class FileRepository(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val planFile get() = File(context.filesDir, "current_plan.json")
    private val resultsFile get() = File(context.filesDir, "current_results.json")

    fun savePlan(plan: WeeklyPlan) {
        planFile.writeText(json.encodeToString(WeeklyPlan.serializer(), plan))
        // initialize empty results scaffold for convenience
        val dayResults = plan.days.map { day ->
            DayResults(day.name, day.drills.map { DrillResults(it.id, emptyList()) })
        }
        val results = WeeklyResults(plan, dayResults)
        resultsFile.writeText(json.encodeToString(WeeklyResults.serializer(), results))
    }

    fun loadPlanOrNull(): WeeklyPlan? {
        if (!planFile.exists()) return null
        return try {
            json.decodeFromString(WeeklyPlan.serializer(), planFile.readText())
        } catch (e: Exception) { null }
    }

    fun loadResultsOrNull(): WeeklyResults? {
        if (!resultsFile.exists()) return null
        return try {
            json.decodeFromString(WeeklyResults.serializer(), resultsFile.readText())
        } catch (e: Exception) { null }
    }

    fun saveResults(results: WeeklyResults) {
        resultsFile.writeText(json.encodeToString(WeeklyResults.serializer(), results))
    }

    fun resetWeek() {
        planFile.delete()
        resultsFile.delete()
    }

    fun exportResultsJson(): String? {
        val res = loadResultsOrNull() ?: return null
        return Json { prettyPrint = true }.encodeToString(WeeklyResults.serializer(), res)
    }
}
