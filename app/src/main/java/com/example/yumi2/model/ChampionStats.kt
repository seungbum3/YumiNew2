package com.example.yumi2.model

data class ChampionStats(
    val championId: Int,
    val championName: String,
    val championEngId: String,

    val games: Int,
    val wins: Int,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val cs: Int,
    val gold: Int
) {
    val winRate: Double
        get() = if (games > 0) (wins.toDouble() / games * 100.0) else 0.0

    val avgKills: Double
        get() = if (games > 0) kills.toDouble() / games else 0.0

    val avgDeaths: Double
        get() = if (games > 0) deaths.toDouble() / games else 0.0

    val avgAssists: Double
        get() = if (games > 0) assists.toDouble() / games else 0.0

    val avgCS: Double
        get() = if (games > 0) cs.toDouble() / games else 0.0

    val avgGold: Double
        get() = if (games > 0) gold.toDouble() / games else 0.0
}
