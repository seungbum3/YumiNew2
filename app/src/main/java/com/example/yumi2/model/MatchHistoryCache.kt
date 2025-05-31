package com.example.yumi2.model

data class MatchHistoryCache(
    val matches: List<MatchHistoryItem> = emptyList(),
    val updatedAt: Long = 0L
)
