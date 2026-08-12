package com.diprish.utilitymeter.ui.backup

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.diprish.utilitymeter.UtilityMeterApp
import com.diprish.utilitymeter.backup.AuthOutcome
import com.diprish.utilitymeter.backup.BackupManager
import com.diprish.utilitymeter.backup.BackupScheduler
import com.diprish.utilitymeter.backup.GoogleAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val connected: Boolean = false,
    val autoEnabled: Boolean = false,
    val lastBackupAt: Long = 0L,
    val busy: Boolean = false,
    val message: String? = null,
)

private enum class Action { BACKUP, RESTORE }

class BackupViewModel(
    private val app: UtilityMeterApp,
    private val auth: GoogleAuthManager,
    private val backup: BackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow(
        BackupUiState(
            connected = backup.isConnected(),
            autoEnabled = backup.isAutoEnabled(),
            lastBackupAt = backup.lastBackupAt(),
        )
    )
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Non-null when the screen must launch the Google consent flow. */
    private val _consentIntent = MutableStateFlow<PendingIntent?>(null)
    val consentIntent: StateFlow<PendingIntent?> = _consentIntent.asStateFlow()

    private var pendingAction: Action? = null

    fun backupNow() = run(Action.BACKUP)

    fun restore() = run(Action.RESTORE)

    fun setAutoEnabled(enabled: Boolean) {
        backup.setAutoEnabled(enabled)
        BackupScheduler.setEnabled(app, enabled)
        _state.update { it.copy(autoEnabled = enabled) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun run(action: Action) {
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val outcome = auth.authorize()) {
                is AuthOutcome.Authorized -> execute(action, outcome.token)
                is AuthOutcome.NeedsConsent -> {
                    pendingAction = action
                    _consentIntent.value = outcome.pendingIntent
                    _state.update { it.copy(busy = false) }
                }
                is AuthOutcome.Error -> _state.update {
                    it.copy(busy = false, message = "Google sign-in failed: ${outcome.message}")
                }
            }
        }
    }

    /** Called by the screen with the result of the consent activity. */
    fun onConsentResult(data: Intent?) {
        val action = pendingAction
        pendingAction = null
        _consentIntent.value = null
        if (data == null || action == null) {
            _state.update { it.copy(busy = false, message = "Google sign-in cancelled") }
            return
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val outcome = auth.resultFromIntent(data)) {
                is AuthOutcome.Authorized -> execute(action, outcome.token)
                else -> _state.update { it.copy(busy = false, message = "Authorization failed") }
            }
        }
    }

    private suspend fun execute(action: Action, token: String) {
        _state.update { it.copy(busy = true) }
        val message = try {
            when (action) {
                Action.BACKUP -> "Backed up ${backup.backup(token)} readings to Drive."
                Action.RESTORE -> backup.restore(token).let {
                    if (!it.found) "No backup found in your Drive."
                    else "Restored ${it.meters} meters and ${it.readings} readings."
                }
            }
        } catch (t: Throwable) {
            "Failed: ${t.message ?: "unknown error"}"
        }
        _state.update {
            it.copy(
                busy = false,
                message = message,
                connected = backup.isConnected(),
                lastBackupAt = backup.lastBackupAt(),
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UtilityMeterApp
                BackupViewModel(app, app.authManager, app.backupManager)
            }
        }
    }
}
