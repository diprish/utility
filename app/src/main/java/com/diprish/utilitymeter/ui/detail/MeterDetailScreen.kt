package com.diprish.utilitymeter.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diprish.utilitymeter.data.MeterReading
import com.diprish.utilitymeter.ui.components.UsageChart
import com.diprish.utilitymeter.ui.formatDateTime
import com.diprish.utilitymeter.ui.formatNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterDetailScreen(
    onBack: () -> Unit,
    onAddReading: (Long) -> Unit,
    viewModel: MeterDetailViewModel = viewModel(factory = MeterDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val meter = state.meter

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meter?.name ?: "Meter") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (meter != null) {
                ExtendedFloatingActionButton(
                    text = { Text("Add reading") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = { onAddReading(meter.id) },
                )
            }
        },
    ) { padding ->
        if (meter == null) {
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SummaryCard(
                    unit = meter.unit,
                    readingCount = state.readings.size,
                    totalUsage = state.totalUsage,
                    averageLabel = viewModel.averageLabel(state),
                )
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Usage over time", style = MaterialTheme.typography.titleMedium)
                        UsageChart(
                            bars = state.usageBars,
                            unit = meter.unit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                Text(
                    "Readings",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.readings.isEmpty()) {
                item {
                    Text(
                        "No readings yet. Tap “Add reading” to record one from the camera or by hand.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.readings, key = { it.id }) { reading ->
                    ReadingRow(
                        reading = reading,
                        unit = meter.unit,
                        onDelete = { viewModel.deleteReading(reading) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    unit: String,
    readingCount: Int,
    totalUsage: Double,
    averageLabel: String?,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat("Readings", readingCount.toString())
            Stat("Total used", "${formatNumber(totalUsage)} $unit")
            Stat("Avg / day", averageLabel ?: "—")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadingRow(
    reading: MeterReading,
    unit: String,
    onDelete: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${formatNumber(reading.value)} $unit",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    formatDateTime(reading.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (reading.note.isNotBlank()) {
                    Text(
                        reading.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete reading",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
