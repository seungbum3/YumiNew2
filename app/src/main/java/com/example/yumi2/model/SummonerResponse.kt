package com.example.yumi2.model

data class SummonerResponse(
    var puuid: String = "",
    var summonerId: String = "",
    var gameName: String = "",
    var tagLine: String = "",
    var profileIconId: Int = 0,
    var summonerLevel: Int = 0,
    var soloRank: RankInfo? = null,
    var flexRank: RankInfo? = null
)

