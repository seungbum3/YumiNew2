package com.example.yumi2.model

data class Player(
    val summonerName: String,
    val championId: Int,
    val championEngName: String,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val teamId: Int,
    val spell1Id: Int,
    val spell2Id: Int,
    val cs: Int,
    val csPerMin: Double,
    val gold: Int,
    val itemIds: List<Int>,
    val isWin: Boolean
)



