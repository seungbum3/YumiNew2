package com.example.yumi2.model

data class Player(
    var summonerName: String = "",
    var championId: Int = 0,
    var championEngName: String = "",
    var kills: Int = 0,
    var deaths: Int = 0,
    var assists: Int = 0,
    var teamId: Int = 0,
    var spell1Id: Int = 0,
    var spell2Id: Int = 0,
    var cs: Int = 0,
    var csPerMin: Double = 0.0,
    var gold: Int = 0,
    var itemIds: List<Int> = emptyList(),
    var isWin: Boolean = false
)
