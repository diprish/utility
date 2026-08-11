package com.diprish.utilitymeter

import android.app.Application
import com.diprish.utilitymeter.data.AppDatabase
import com.diprish.utilitymeter.data.MeterRepository

/**
 * Application subclass acting as a tiny service locator. Keeping a single
 * repository instance here avoids pulling in a full DI framework for an app
 * of this size.
 */
class UtilityMeterApp : Application() {

    val repository: MeterRepository by lazy {
        MeterRepository(AppDatabase.get(this).meterDao())
    }
}
