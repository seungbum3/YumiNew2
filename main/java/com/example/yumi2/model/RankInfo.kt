package com.example.yumi2.model

data class RankInfo(
    var tier: String = "",
    var rank: String = "",
    var leaguePoints: Int = 0,
    var wins: Int = 0,
    var losses: Int = 0,
    var queueType: String = ""
)