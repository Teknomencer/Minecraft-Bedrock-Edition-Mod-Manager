package com.example

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

data class ModFile(
    val file: File,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val extension: String
) {
    val formattedSize: String
        get() {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            return String.format(Locale.getDefault(), "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }
}
