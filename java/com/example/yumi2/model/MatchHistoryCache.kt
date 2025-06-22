package com.example.yumi2.model

data class MatchHistoryCache(
    val matches: List<MatchHistoryItem> = emptyList(),
    val updatedAt: Long = 0L,
    val isEmpty: Boolean = false // ✅ 빈 리스트 여부 캐시 마커
)
