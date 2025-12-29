package com.example.uvccamerademo

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun toWorkState(value: String): WorkState = WorkState.valueOf(value)

    @TypeConverter
    fun fromWorkState(state: WorkState): String = state.name

    @TypeConverter
    fun toUploadState(value: String): UploadState = UploadState.valueOf(value)

    @TypeConverter
    fun fromUploadState(state: UploadState): String = state.name
}
