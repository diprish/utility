package com.diprish.utilitymeter.ui.meters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.diprish.utilitymeter.UtilityMeterApp
import com.diprish.utilitymeter.data.Meter
import com.diprish.utilitymeter.data.MeterRepository
import com.diprish.utilitymeter.data.MeterType
import com.diprish.utilitymeter.data.MeterWithStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MetersViewModel(private val repository: MeterRepository) : ViewModel() {

    val meters: StateFlow<List<MeterWithStats>> =
        repository.metersWithStats().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun addMeter(name: String, type: MeterType, unit: String, location: String) {
        viewModelScope.launch {
            repository.addMeter(
                Meter(
                    name = name.trim(),
                    type = type,
                    unit = unit.trim().ifEmpty { type.defaultUnit },
                    location = location.trim(),
                    // Negative time => sorts before reordered meters (0..n-1), so
                    // a freshly added meter shows up at the top of the list.
                    position = -System.currentTimeMillis(),
                )
            )
        }
    }

    fun deleteMeter(meter: Meter) {
        viewModelScope.launch { repository.deleteMeter(meter) }
    }

    /** Persist a new manual ordering by writing each meter's list index as its position. */
    fun persistOrder(ordered: List<MeterWithStats>) {
        viewModelScope.launch {
            repository.updateMeters(
                ordered.mapIndexed { index, item -> item.meter.copy(position = index.toLong()) }
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UtilityMeterApp
                MetersViewModel(app.repository)
            }
        }
    }
}
