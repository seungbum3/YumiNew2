package com.example.yumi2.api

import com.example.yumi2.model.LeagueEntry
import com.example.yumi2.model.MatchDetail
import com.example.yumi2.model.Summoner
import com.example.yumi2.model.SummonerResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Header
import retrofit2.http.Query

data class ChampionRotationResponse(val champion_list: List<ChampionData>)

data class ChampionData(
    val id: Int,
    val name: String,
    val imageUrl: String
)

interface RiotApiService {

    @GET("riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}")
    suspend fun getSummonerInfo(
        @Path("gameName") gameName: String,
        @Path("tagLine") tagLine: String,
        @Header("X-Riot-Token") apiKey: String
    ): SummonerResponse

    @GET("lol/summoner/v4/summoners/by-name/{summonerName}")
    suspend fun getSummonerByName(
        @Path("summonerName") summonerName: String,
        @Header("X-Riot-Token") apiKey: String
    ): Summoner

    @GET("lol/summoner/v4/summoners/by-puuid/{puuid}")
    suspend fun getSummonerByPuuid(
        @Path("puuid") puuid: String,
        @Header("X-Riot-Token") apiKey: String
    ): Summoner

    @GET("lol/league/v4/entries/by-summoner/{summonerId}")
    suspend fun getRankInfo(
        @Path("summonerId") summonerId: String,
        @Header("X-Riot-Token") apiKey: String
    ): List<LeagueEntry>

    @GET("lol/match/v5/matches/by-puuid/{puuid}/ids")
    suspend fun getMatchIdsByPuuid(
        @Path("puuid") puuid: String,
        @Query("start") start: Int,
        @Query("count") count: Int,
        @Query("queue") queue: Int? = null,
        @Header("X-Riot-Token") apiKey: String
    ): List<String>

    // ✅ 정답 MatchDetail 반환!
    @GET("lol/match/v5/matches/{matchId}")
    suspend fun getMatchDetail(
        @Path("matchId") matchId: String,
        @Header("X-Riot-Token") apiKey: String
    ): MatchDetail
}


// ▼ Match-V5 응답을 담을 DTO들 (간소화 버전)
data class MatchDto(
    val metadata: Metadata,
    val info: Info
)

data class Metadata(
    val matchId: String,
    val participants: List<String>
)

data class Info(
    val gameCreation: Long,   // 게임 생성 시간 (밀리초)
    val gameDuration: Long,
    val gameVersion: String,  // 게임 버전 (예: "13.5.1")
    val queueId: Int,         // 큐 ID (예: 420)
    val participants: List<Participant>
)


data class Participant(
    val puuid: String,
    val championId: Int,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val goldEarned: Int,
    val totalMinionsKilled: Int,
    val neutralMinionsKilled: Int,
    val win: Boolean,

    // ▼ 아이템 필드 (추가)
    val item0: Int,
    val item1: Int,
    val item2: Int,
    val item3: Int,
    val item4: Int,
    val item5: Int,
    val item6: Int,

    // ▼ 소환사 주문 필드 (추가)
    val summoner1Id: Int,
    val summoner2Id: Int
)


