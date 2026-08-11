package com.diprish.utilitymeter.ui.reading

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import com.diprish.utilitymeter.UtilityMeterApp
import com.diprish.utilitymeter.data.MeterReading
import com.diprish.utilitymeter.data.MeterRepository
import com.diprish.utilitymeter.ocr.MeterOcr
import com.diprish.utilitymeter.ui.applyDatePickerMillis
import com.diprish.utilitymeter.ui.formatNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AddReadingState(
    val photoPath: String? = null,
    /** Wall-clock time the photo was captured; null when there is no photo. */
    val photoTakenAt: Long? = null,
    val valueText: String = "",
    val note: String = "",
    /** The reading's date/time. Defaults to now; user can change the date. */
    val timestamp: Long = System.currentTimeMillis(),
    val ocrRunning: Boolean = false,
    /** Non-null after a scan when we could not find a number. */
    val ocrMessage: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val previousValue: Double? = null,
    val unit: String = "",
    val meterName: String = "",
    /** True when editing an existing reading rather than creating a new one. */
    val isEditing: Boolean = false,
) {
    val parsedValue: Double? get() = valueText.trim().replace(',', '.').toDoubleOrNull()
    val canSave: Boolean get() = parsedValue != null && !saving
}

class AddReadingViewModel(
    private val repository: MeterRepository,
    private val meterId: Long,
    private val readingId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(AddReadingState(isEditing = readingId >= 0))
    val state: StateFlow<AddReadingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val meter = repository.meter(meterId).first()
            if (readingId >= 0) {
                // Editing: prefill from the stored reading.
                val existing = repository.reading(readingId)
                _state.update {
                    it.copy(
                        unit = meter?.unit.orEmpty(),
                        meterName = meter?.name.orEmpty(),
                        valueText = existing?.let { r -> formatNumber(r.value) } ?: "",
                        note = existing?.note.orEmpty(),
                        timestamp = existing?.timestamp ?: it.timestamp,
                        photoPath = existing?.photoPath,
                        photoTakenAt = existing?.photoTakenAt,
                    )
                }
            } else {
                // New reading: show the last reading for context/sanity-check.
                val previous = repository.latestReading(meterId)?.value
                _state.update {
                    it.copy(
                        previousValue = previous,
                        unit = meter?.unit.orEmpty(),
                        meterName = meter?.name.orEmpty(),
                    )
                }
            }
        }
    }

    fun onValueChange(text: String) {
        // Allow only digits and a single decimal separator.
        val filtered = text.filter { it.isDigit() || it == '.' || it == ',' }
        _state.update { it.copy(valueText = filtered, ocrMessage = null) }
    }

    fun onNoteChange(text: String) {
        _state.update { it.copy(note = text) }
    }

    /** [pickedUtcMillis] is the UTC-midnight value returned by the DatePicker. */
    fun onDateSelected(pickedUtcMillis: Long) {
        _state.update { it.copy(timestamp = applyDatePickerMillis(pickedUtcMillis, it.timestamp)) }
    }

    /** Called after the camera writes a photo. Kicks off OCR on the image. */
    fun onPhotoCaptured(context: Context, file: File) {
        _state.update {
            it.copy(
                photoPath = file.absolutePath,
                photoTakenAt = System.currentTimeMillis(),
                ocrRunning = true,
                ocrMessage = null,
            )
        }
        viewModelScope.launch {
            try {
                val result = MeterOcr.recognize(context, Uri.fromFile(file))
                _state.update { current ->
                    if (result.reading != null) {
                        current.copy(
                            ocrRunning = false,
                            valueText = formatNumber(result.reading),
                            ocrMessage = null,
                        )
                    } else {
                        current.copy(
                            ocrRunning = false,
                            ocrMessage = "Couldn't read a number — type it in below.",
                        )
                    }
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(ocrRunning = false, ocrMessage = "Scan failed — type the reading in below.")
                }
            }
        }
    }

    fun retakePhoto() {
        _state.update { it.copy(photoPath = null, photoTakenAt = null, ocrMessage = null, ocrRunning = false) }
    }

    fun save() {
        val current = _state.value
        val value = current.parsedValue ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val reading = MeterReading(
                // id = 0 lets Room autogenerate for new rows; a real id updates in place.
                id = if (readingId >= 0) readingId else 0,
                meterId = meterId,
                value = value,
                timestamp = current.timestamp,
                photoPath = current.photoPath,
                photoTakenAt = current.photoTakenAt,
                note = current.note.trim(),
            )
            if (readingId >= 0) repository.updateReading(reading) else repository.addReading(reading)
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UtilityMeterApp
                val savedState: SavedStateHandle = createSavedStateHandle()
                val meterId: Long = savedState["meterId"] ?: 0L
                val readingId: Long = savedState["readingId"] ?: -1L
                AddReadingViewModel(app.repository, meterId, readingId)
            }
        }
    }
}
