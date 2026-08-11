package com.diprish.utilitymeter.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import com.diprish.utilitymeter.UtilityMeterApp
import com.diprish.utilitymeter.data.Meter
import com.diprish.utilitymeter.data.MeterReading
import com.diprish.utilitymeter.data.MeterRepository
import com.diprish.utilitymeter.ui.components.UsageBar
import com.diprish.utilitymeter.ui.formatNumber
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MeterDetailState(
    val meter: Meter? = null,
    /** Readings newest-first for display in the list. */
    val readings: List<MeterReading> = emptyList(),
    val usageBars: List<UsageBar> = emptyList(),
    /** Usage since the previous reading, keyed by reading id (when computable). */
    val readingDeltas: Map<Long, Double> = emptyMap(),
    val totalUsage: Double = 0.0,
    val averagePerDay: Double? = null,
    val loading: Boolean = true,
) {
    val latestReading: MeterReading? get() = readings.firstOrNull()
}

class MeterDetailViewModel(
    private val repository: MeterRepository,
    private val meterId: Long,
) : ViewModel() {

    private val labelFormat = SimpleDateFormat("d MMM", Locale.getDefault())

    val state: StateFlow<MeterDetailState> =
        combine(
            repository.meter(meterId),
            repository.readings(meterId),
        ) { meter, readings ->
            buildState(meter, readings)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MeterDetailState(),
        )

    private fun buildState(meter: Meter?, readingsAsc: List<MeterReading>): MeterDetailState {
        // Usage between consecutive readings (readings arrive oldest-first).
        val bars = mutableListOf<UsageBar>()
        val deltas = mutableMapOf<Long, Double>()
        for (i in 1 until readingsAsc.size) {
            val delta = readingsAsc[i].value - readingsAsc[i - 1].value
            deltas[readingsAsc[i].id] = delta
            bars += UsageBar(
                label = labelFormat.format(Date(readingsAsc[i].timestamp)),
                // Guard against a meter reset / mis-read producing a negative bar.
                value = if (delta >= 0) delta else 0.0,
            )
        }
        // Keep the chart readable: show the most recent dozen periods.
        val recentBars = bars.takeLast(12)

        val total = bars.sumOf { it.value }
        val averagePerDay = if (readingsAsc.size >= 2) {
            val spanMillis = readingsAsc.last().timestamp - readingsAsc.first().timestamp
            val days = spanMillis / (1000.0 * 60 * 60 * 24)
            if (days > 0) total / days else null
        } else null

        return MeterDetailState(
            meter = meter,
            readings = readingsAsc.reversed(),
            usageBars = recentBars,
            readingDeltas = deltas,
            totalUsage = total,
            averagePerDay = averagePerDay,
            loading = false,
        )
    }

    fun deleteReading(reading: MeterReading) {
        viewModelScope.launch { repository.deleteReading(reading) }
    }

    /** Formatted average-per-day string for the header, or null when N/A. */
    fun averageLabel(state: MeterDetailState): String? =
        state.averagePerDay?.let { "${formatNumber(it)} ${state.meter?.unit.orEmpty()}/day" }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UtilityMeterApp
                val savedState: SavedStateHandle = createSavedStateHandle()
                val meterId: Long = savedState["meterId"] ?: 0L
                MeterDetailViewModel(app.repository, meterId)
            }
        }
    }
}
