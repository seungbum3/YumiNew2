package com.example.opggyumi.comment

import java.text.SimpleDateFormat
import java.util.*

fun getRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60 * 1000 -> "${diff / 1000}초 전"                      // 1분 미만
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}분 전"             // 1시간 미만
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}시간 전"     // 1일 미만
        diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}일 전" // 1주일 미만
        else -> {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}