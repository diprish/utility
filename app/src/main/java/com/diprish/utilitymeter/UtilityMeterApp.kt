package com.diprish.utilitymeter

import android.app.Application
import com.diprish.utilitymeter.backup.BackupManager
import com.diprish.utilitymeter.backup.GoogleAuthManager
import com.diprish.utilitymeter.data.AppDatabase
import com.diprish.utilitymeter.data.MeterRepository

/**
 * Application subclass acting as a tiny service locator. Keeping single
 * instances here avoids pulling in a full DI framework for an app of this size.
 */
class UtilityMeterApp : Application() {

    val repository: MeterRepository by lazy {
        MeterRepository(AppDatabase.get(this).meterDao())
    }

    val authManager: GoogleAuthManager by lazy { GoogleAuthManager(this) }

    val backupManager: BackupManager by lazy { BackupManager(this, repository) }
}
