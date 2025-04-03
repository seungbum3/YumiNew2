package com.example.yumi2.model



data class MatchDetail(
    val info: MatchInfo
)

data class MatchInfo(
    val gameCreation: Long,
    val gameDuration: Long,
    val queueId: Int,
    val participants: List<Participant>
)
