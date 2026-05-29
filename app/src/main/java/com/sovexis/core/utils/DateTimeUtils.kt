package com.sovexis.core.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    private const val DEFAULT_FORMAT = "yyyy-MM-dd HH:mm:ss"
    private const val DATE_FORMAT = "yyyy-MM-dd"
    private const val TIME_FORMAT = "HH:mm:ss"

    fun formatTimestamp(timestamp: Long, pattern: String = DEFAULT_FORMAT): String {
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }

    fun formatDate(timestamp: Long): String = formatTimestamp(timestamp, DATE_FORMAT)

    fun formatTime(timestamp: Long): String = formatTimestamp(timestamp, TIME_FORMAT)

    fun getCurrentTimestamp(): Long = System.currentTimeMillis()

    fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000}分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000}小时前"
            diff < 604_800_000 -> "${diff / 86_400_000}天前"
            else -> formatDate(timestamp)
        }
    }
}
