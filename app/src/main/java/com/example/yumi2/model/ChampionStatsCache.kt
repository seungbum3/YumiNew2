package com.example.yumi2.model

data class ChampionStatsCache(
    val stats: List<ChampionStats> = emptyList(),
    val updatedAt: Long = 0L
)
