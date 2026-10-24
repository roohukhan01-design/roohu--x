package com.roohu.roohux.core.design

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "unknown size"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$bytes B" else String.format(Locale.getDefault(), "%.1f %s", v, units[i])
}

fun formatSpeed(bps: Long): String = "${formatBytes(bps)}/s"

fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s left"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
}
