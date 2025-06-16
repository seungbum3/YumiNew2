package com.example.yumi2.model

data class MatchHistoryItem(
    var championId: Int = 0,
    var championEngName: String = "",
    var championKorName: String = "",
    var queueType: String = "",
    var queueId: Int = 0,
    var isWin: Boolean = false,
    var kills: Int = 0,
    var deaths: Int = 0,
    var assists: Int = 0,
    var kdaString: String = "",
    var kdaRatioString: String = "",
    var summonerSpell1: Int = 0,
    var summonerSpell2: Int = 0,
    var itemIds: List<Int> = emptyList(),
    var cs: Int = 0,
    var gold: Int = 0,
    var gameDuration: Long = 0L,
    var gameCreation: Long = 0L,
    var redTeamParticipants: List<Player> = emptyList(),
    var blueTeamParticipants: List<Player> = emptyList()
)
