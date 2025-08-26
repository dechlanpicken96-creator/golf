package com.golfcoach.tracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.golfcoach.tracker.model.*
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    private val vm: TrackerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                contentResolver.openInputStream(uri)?.use { `is` ->
                    vm.importPlanFromStream(`is`)
                }
            }
        }

        val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { os ->
                    vm.exportResultsToStream(os)
                }
            }
        }

        setContent {
            MaterialTheme {
                AppScaffold(
                    viewModel = vm,
                    onImportPlan = { importLauncher.launch(arrayOf("application/json")) },
                    onExportResults = { exportLauncher.launch("golf_week_results.json") },
                    onShareResults = {
                        vm.exportResultsAsString()?.let { json ->
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            startActivity(Intent.createChooser(sendIntent, "Share week results"))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AppScaffold(
    viewModel: TrackerViewModel,
    onImportPlan: () -> Unit,
    onExportResults: () -> Unit,
    onShareResults: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Plan", "Track", "Export")
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Pro Golf Coach Tracker", fontWeight = FontWeight.Bold) }
                )
                TabRow(selectedTabIndex = tab) {
                    tabs.forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> PlanScreen(state, onImportPlan)
                1 -> TrackScreen(viewModel, state)
                2 -> ExportScreen(state, onExportResults, onShareResults, onReset = { viewModel.resetWeek() })
            }
        }
    }
}

@Composable
fun PlanScreen(state: UiState, onImportPlan: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.plan == null) {
            Text("No weekly plan loaded.", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onImportPlan) { Text("Import Weekly Plan (.json)") }
            Text("The plan file is a JSON you’ll import each week. It defines your days & drills.", style = MaterialTheme.typography.bodyMedium)
        } else {
            val p = state.plan
            Text(p!!.weekLabel ?: "Week starting ${p.weekStartDate}", style = MaterialTheme.typography.titleMedium)
            p.days.forEach { day ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(day.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        day.drills.forEachIndexed { idx, d ->
                            Text("• ${d.name}${d.club?.let { " (${it})" } ?: ""} — ${d.shotsPlanned} shots — ${d.scoringType}")
                            d.targetDesc?.let { Text("  Target: $it", style = MaterialTheme.typography.bodySmall) }
                            d.notes?.let { Text("  Notes: $it", style = MaterialTheme.typography.bodySmall) }
                            if (idx < day.drills.size - 1) Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackScreen(viewModel: TrackerViewModel, state: UiState) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.plan == null) {
            Text("Import a weekly plan to start tracking.", style = MaterialTheme.typography.titleMedium)
            return@Column
        }

        // Select Day
        var dayIndex by remember { mutableStateOf(0) }
        ExposedDropdownMenuBoxSample(
            label = "Day",
            options = state.plan.days.map { it.name },
            selectedIndex = dayIndex,
            onSelected = { dayIndex = it }
        )

        val day = state.plan.days[dayIndex]

        // Select Drill
        var drillIndex by remember { mutableStateOf(0) }
        ExposedDropdownMenuBoxSample(
            label = "Drill",
            options = day.drills.map { it.name + (it.club?.let { c -> " ($c)" } ?: "") },
            selectedIndex = drillIndex,
            onSelected = { drillIndex = it }
        )

        val drill = day.drills[drillIndex]
        val shots = state.getShots(day.name, drill.id)

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${drill.name}${drill.club?.let { " (${it})" } ?: ""}", fontWeight = FontWeight.Bold)
                Text(drill.targetDesc ?: "Enter shots for this drill.")
                when (drill.scoringType) {
                    ScoringType.PROXIMITY_CM -> ProximityEntry(shots.size + 1) { value, note ->
                        viewModel.addShot(day.name, drill.id, ShotEntry(index = shots.size + 1, valueNumber = value, note = note))
                    }
                    ScoringType.MAKE_MISS -> MakeMissEntry(shots.size + 1) { make, note ->
                        viewModel.addShot(day.name, drill.id, ShotEntry(index = shots.size + 1, valueBool = make, note = note))
                    }
                    ScoringType.TIME_SEC -> TimeEntry(shots.size + 1) { value, note ->
                        viewModel.addShot(day.name, drill.id, ShotEntry(index = shots.size + 1, valueNumber = value, note = note))
                    }
                }
                if (shots.isNotEmpty()) {
                    Divider()
                    Text("Shots Entered: ${shots.size}")
                    shots.sortedBy { it.index }.forEach { s ->
                        val v = s.valueNumber?.let { " ${it}" } ?: (if (s.valueBool == true) " MAKE" else if (s.valueBool == false) " MISS" else "")
                        Text("#${s.index} –${v}${s.note?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ProximityEntry(nextIndex: Int, onAdd: (Double, String?) -> Unit) {
    var value by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Proximity (cm) for shot #$nextIndex") })
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                value.toDoubleOrNull()?.let { onAdd(it, note.ifBlank { null }) }
                value = ""; note = ""
            }) { Text("Add Shot") }
        }
    }
}

@Composable
fun MakeMissEntry(nextIndex: Int, onAdd: (Boolean, String?) -> Unit) {
    var note by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onAdd(true, note.ifBlank { null }); note = "" }) { Text("MAKE (#$nextIndex)") }
        Button(onClick = { onAdd(false, note.ifBlank { null }); note = "" }) { Text("MISS (#$nextIndex)") }
    }
    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") })
}

@Composable
fun TimeEntry(nextIndex: Int, onAdd: (Double, String?) -> Unit) {
    var value by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Time (sec) for shot #$nextIndex") })
    }
    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") })
    Button(onClick = {
        value.toDoubleOrNull()?.let { onAdd(it, note.ifBlank { null }) }
    }) { Text("Add Shot") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxSample(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(options.getOrNull(selectedIndex) ?: "") }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            readOnly = true,
            value = textFieldValue,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, s ->
                DropdownMenuItem(text = { Text(s) }, onClick = {
                    onSelected(index); textFieldValue = s; expanded = false
                })
            }
        }
    }
}

@Composable
fun ExportScreen(state: UiState, onExport: () -> Unit, onShare: () -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.plan == null) {
            Text("Import a weekly plan first.")
        } else {
            Text("Ready to export your week’s results as JSON. You can share or save the file.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onExport) { Text("Save to File") }
                OutlinedButton(onClick = onShare) { Text("Share (copy/email)") }
            }
            Divider()
            Button(onClick = onReset, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Reset Week (Clear Plan & Results)")
            }
        }
    }
}
