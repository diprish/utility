package com.diprish.utilitymeter.data

/**
 * The kind of utility a meter tracks. Each type carries a sensible default
 * unit that is shown in the UI and used when charting usage.
 */
enum class MeterType(val displayName: String, val defaultUnit: String) {
    ELECTRICITY("Electricity", "kWh"),
    WATER("Water", "m³"),
    GAS("Gas", "m³"),
    HEAT("Heat", "kWh"),
    OTHER("Other", "units");

    companion object {
        fun fromName(name: String?): MeterType =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}
