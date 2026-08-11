package com.diprish.utilitymeter.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.diprish.utilitymeter.data.MeterType

/** Icon + accent colour used to give each meter type a recognisable identity. */
data class MeterVisual(val icon: ImageVector, val accent: Color)

fun meterVisual(type: MeterType): MeterVisual = when (type) {
    MeterType.ELECTRICITY -> MeterVisual(Icons.Filled.Bolt, Color(0xFFF59E0B))
    MeterType.WATER -> MeterVisual(Icons.Filled.WaterDrop, Color(0xFF2563EB))
    MeterType.GAS -> MeterVisual(Icons.Filled.LocalFireDepartment, Color(0xFFEA580C))
    MeterType.HEAT -> MeterVisual(Icons.Filled.Thermostat, Color(0xFFE11D48))
    MeterType.OTHER -> MeterVisual(Icons.Filled.Speed, Color(0xFF0D9488))
}
