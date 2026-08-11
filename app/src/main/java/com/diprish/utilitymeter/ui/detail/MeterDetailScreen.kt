package com.diprish.utilitymeter.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diprish.utilitymeter.data.Meter
import com.diprish.utilitymeter.data.MeterReading
import com.diprish.utilitymeter.ui.components.ReadingPhotoViewer
import com.diprish.utilitymeter.ui.components.ReadingThumbnail
import com.diprish.utilitymeter.ui.components.UsageChart
import com.diprish.utilitymeter.ui.formatDate
import com.diprish.utilitymeter.ui.formatDateTime
import com.diprish.utilitymeter.ui.formatNumber
import com.diprish.utilitymeter.ui.meterVisual

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterDetailScreen(
    onBack: () -> Unit,
    onAddReading: (Long) -> Unit,
    onEditReading: (meterId: Long, readingId: Long) -> Unit,
    viewModel: MeterDetailViewModel = viewModel(factory = MeterDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val meter = state.meter
    var viewerReading by remember { mutableStateOf<MeterReading?>(null) }

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
        val accent = meterVisual(meter.type).accent
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HeroCard(meter = meter, latest = state.latestReading)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "Total used",
                        value = "${formatNumber(state.totalUsage)} ${meter.unit}",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Avg / day",
                        value = viewModel.averageLabel(state) ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Usage over time", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Consumption per period (${meter.unit})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        UsageChart(
                            bars = state.usageBars,
                            unit = meter.unit,
                            barColor = accent,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                Text(
                    "Readings",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
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
                        delta = state.readingDeltas[reading.id],
                        accent = accent,
                        onEdit = { onEditReading(meter.id, reading.id) },
                        onOpenPhoto = { viewerReading = reading },
                        onDelete = { viewModel.deleteReading(reading) },
                    )
                }
            }
        }
    }

    viewerReading?.let { reading ->
        reading.photoPath?.let { path ->
            ReadingPhotoViewer(
                path = path,
                // Prefer the real capture time; fall back to the reading date
                // for photos saved before capture times were recorded.
                dateText = formatDateTime(reading.photoTakenAt ?: reading.timestamp),
                onDismiss = { viewerReading = null },
            )
        }
    }
}

@Composable
private fun HeroCard(meter: Meter, latest: MeterReading?) {
    val visual = meterVisual(meter.type)
    val onAccent = Color.White
    Card(shape = RoundedCornerShape(24.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(visual.accent, lerp(visual.accent, Color.Black, 0.35f)),
                    )
                )
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(onAccent.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(visual.icon, contentDescription = null, tint = onAccent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            meter.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onAccent,
                        )
                        val subtitle = buildString {
                            append(meter.type.displayName)
                            if (meter.location.isNotBlank()) append(" · ${meter.location}")
                        }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = onAccent.copy(alpha = 0.85f),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "LATEST READING",
                    style = MaterialTheme.typography.labelSmall,
                    color = onAccent.copy(alpha = 0.8f),
                )
                if (latest != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatNumber(latest.value),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = onAccent,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            meter.unit,
                            style = MaterialTheme.typography.titleMedium,
                            color = onAccent.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        "on ${formatDate(latest.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onAccent.copy(alpha = 0.85f),
                    )
                } else {
                    Text(
                        "No readings yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = onAccent,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadingRow(
    reading: MeterReading,
    unit: String,
    delta: Double?,
    accent: Color,
    onEdit: () -> Unit,
    onOpenPhoto: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (reading.photoPath != null) {
                ReadingThumbnail(
                    path = reading.photoPath,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenPhoto),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${formatNumber(reading.value)} $unit",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    formatDate(reading.timestamp),
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
            if (delta != null) {
                DeltaChip(delta = delta, accent = accent)
                Spacer(Modifier.width(4.dp))
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

@Composable
private fun DeltaChip(delta: Double, accent: Color) {
    val positive = delta >= 0
    val tint = if (positive) accent else MaterialTheme.colorScheme.error
    Surface(
        color = tint.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = (if (positive) "+" else "−") + formatNumber(kotlin.math.abs(delta)),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = tint,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
