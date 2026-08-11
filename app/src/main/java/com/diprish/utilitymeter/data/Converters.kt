package com.diprish.utilitymeter.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromMeterType(type: MeterType): String = type.name

    @TypeConverter
    fun toMeterType(name: String): MeterType = MeterType.fromName(name)
}
