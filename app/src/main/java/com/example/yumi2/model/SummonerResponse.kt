package com.example.yumi2.model

data class SummonerResponse(
    val puuid: String,
    val summonerId: String,
    val gameName: String,
    val tagLine: String,
    val profileIconId: Int,
    val summonerLevel: Int,
    val soloRank: LeagueEntry?,  // ✅ 솔로랭크 추가
    val flexRank: LeagueEntry?   // ✅ 자유랭크 추가
)
