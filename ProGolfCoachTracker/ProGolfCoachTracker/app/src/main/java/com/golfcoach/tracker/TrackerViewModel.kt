package com.golfcoach.tracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.golfcoach.tracker.data.FileRepository
import com.golfcoach.tracker.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

data class UiState(
    val plan: WeeklyPlan? = null,
    val results: WeeklyResults? = null
) {
    fun getShots(dayName: String, drillId: String): List<ShotEntry> {
        val d = results?.days?.firstOrNull { it.name == dayName }?.drills?.firstOrNull { it.drillId == drillId }
        return d?.shots ?: emptyList()
    }
}

class TrackerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FileRepository(app.applicationContext)
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        // Load existing plan/results (if any)
        val plan = repo.loadPlanOrNull()
        val results = repo.loadResultsOrNull()
        _uiState.value = UiState(plan, results)
    }

    fun importPlanFromStream(input: InputStream) {
        viewModelScope.launch {
            val text = input.bufferedReader().use { it.readText() }
            val plan = Json { ignoreUnknownKeys = true }.decodeFromString(WeeklyPlan.serializer(), text)
            repo.savePlan(plan)
            _uiState.value = UiState(plan, repo.loadResultsOrNull())
        }
    }

    fun addShot(dayName: String, drillId: String, shot: ShotEntry) {
        viewModelScope.launch {
            val current = repo.loadResultsOrNull() ?: return@launch
            val updatedDays = current.days.map { d ->
                if (d.name != dayName) d else {
                    val updatedDrills = d.drills.map { dr ->
                        if (dr.drillId != drillId) dr else dr.copy(shots = dr.shots + shot)
                    }
                    d.copy(drills = updatedDrills)
                }
            }
            val updated = current.copy(days = updatedDays)
            repo.saveResults(updated)
            _uiState.value = _uiState.value.copy(results = updated)
        }
    }

    fun exportResultsToStream(os: OutputStream) {
        val json = repo.exportResultsJson() ?: return
        os.bufferedWriter().use { it.write(json) }
    }

    fun exportResultsAsString(): String? = repo.exportResultsJson()

    fun resetWeek() {
        repo.resetWeek()
        _uiState.value = UiState()
    }
}
