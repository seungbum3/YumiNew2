package com.example.yumi2.model

data class RecentMatchesAggregate(
    val matches: List<MatchHistoryItem>,  // 실제 매치 히스토리 목록 (10개)
    val totalWins: Int,                   // 승리 수
    val totalLosses: Int,                 // 패배 수
    val averageKills: Float,             // 평균 킬
    val averageDeaths: Float,            // 평균 데스
    val averageAssists: Float,           // 평균 어시스트
    val averageKDA: Float                // (킬+어시)/데스 (데스=0이면 킬+어시)
)
