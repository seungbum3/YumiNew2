package com.example.yumi2.model

data class MatchHistoryItem(
    val championId: Int,
    val championEngName: String,   // 예: "Jhin", "Ahri"
    val championKorName: String,   // 예: "진", "아리"
    val queueType: String,         // 예: "솔로랭크", "자유랭크", "일반" 등
    val isWin: Boolean,            // 승/패
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val kdaString: String,         // 예: "5 / 2 / 7"
    val kdaRatioString: String,    // 예: "6.00"
    val summonerSpell1: Int,       // summoner1Id
    val summonerSpell2: Int,       // summoner2Id
    val itemIds: List<Int>,        // [item0, item1, item2, item3, item4, item5, item6]
    val cs: Int,
    val gold: Int,
    val gameDuration: Long,        // 게임 길이 (초 또는 밀리초)
    val gameCreation: Long,        // 게임 시작 시간 (타임스탬프, ms)
    val redTeamParticipants: List<Player>,
    val blueTeamParticipants: List<Player>
)
