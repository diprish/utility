package com.diprish.utilitymeter.ui.meters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diprish.utilitymeter.data.MeterWithStats
import com.diprish.utilitymeter.ui.components.ReadingThumbnail
import com.diprish.utilitymeter.ui.formatDate
import com.diprish.utilitymeter.ui.formatNumber
import com.diprish.utilitymeter.ui.meterVisual
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetersListScreen(
    onOpenMeter: (Long) -> Unit,
    viewModel: MetersViewModel = viewModel(factory = MetersViewModel.Factory),
) {
    val meters by viewModel.meters.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    // Local, reorderable copy kept in sync with the database order. Drag updates
    // this immediately for smooth feedback; onDragEnd persists the new order.
    var localOrder by remember { mutableStateOf(meters) }
    LaunchedEffect(meters) { localOrder = meters }

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            localOrder = localOrder.toMutableList().apply { add(to.index, removeAt(from.index)) }
        },
        onDragEnd = { _, _ -> viewModel.persistOrder(localOrder) },
    )

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Utility Meter") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add meter") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { showAddDialog = true },
            )
        },
    ) { padding ->
        if (localOrder.isEmpty()) {
            EmptyState(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                state = reorderState.listState,
                modifier = Modifier.fillMaxSize().reorderable(reorderState),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(localOrder, key = { it.meter.id }) { item ->
                    ReorderableItem(reorderState, key = item.meter.id) { isDragging ->
                        MeterCard(
                            item = item,
                            onClick = { onOpenMeter(item.meter.id) },
                            onDelete = { viewModel.deleteMeter(item.meter) },
                            elevation = if (isDragging) 10.dp else 2.dp,
                            modifier = Modifier.detectReorderAfterLongPress(reorderState),
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMeterDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, unit, location ->
                viewModel.addMeter(name, type, unit, location)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun MeterCard(
    item: MeterWithStats,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: Dp = 2.dp,
) {
    val visual = meterVisual(item.meter.type)
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Accent stripe down the leading edge for a splash of type colour.
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(visual.accent),
            )
            Column(modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 6.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Leading(accent = visual.accent, icon = visual.icon, photoPath = item.latestPhotoPath)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.meter.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = buildString {
                            append(item.meter.type.displayName)
                            if (item.meter.location.isNotBlank()) append(" · ${item.meter.location}")
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete meter",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))

                val latest = item.latestValue
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (latest != null && item.latestTimestamp != null) {
                        Column {
                            Text(
                                "LATEST READING",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    formatNumber(latest),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = visual.accent,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    item.meter.unit,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                        }
                        DateBadge(text = formatDate(item.latestTimestamp), accent = visual.accent)
                    } else {
                        Text(
                            "No readings yet — tap to add one",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Leading(
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    photoPath: String?,
) {
    if (photoPath != null) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            ReadingThumbnail(path = photoPath, modifier = Modifier.fillMaxSize())
            // Small type badge so the meter type stays recognisable over the photo.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(accent.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun DateBadge(text: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "No meters yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = "Tap “Add meter” to add your first electricity, water or gas meter, then start recording readings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
