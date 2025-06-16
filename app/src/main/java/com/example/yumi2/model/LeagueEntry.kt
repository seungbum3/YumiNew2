package com.example.yumi2.model

data class LeagueEntry(
    val queueType: String,  // "RANKED_SOLO_5x5" or "RANKED_FLEX_SR"
    val tier: String,        // "EMERALD", "DIAMOND" 등
    val rank: String,        // "I", "II", "III"
    val leaguePoints: Int,   // LP
    val wins: Int,           // 승리 수
    val losses: Int          // 패배 수
)