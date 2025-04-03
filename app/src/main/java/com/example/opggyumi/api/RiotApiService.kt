package com.example.opggyumi.api
import com.example.opggyumi.model.LeagueEntry
import com.example.opggyumi.model.Summoner
import com.example.opggyumi.model.SummonerResponse
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

    // ✅ Riot ID 기반 소환사 정보 조회 (닉네임 + 태그라인)
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

    // ✅ PUUID 기반 소환사 정보 조회
    @GET("lol/summoner/v4/summoners/by-puuid/{puuid}")
    suspend fun getSummonerByPuuid(
        @Path("puuid") puuid: String,
        @Header("X-Riot-Token") apiKey: String
    ): Summoner

    // ✅ 소환사 랭크 정보 조회
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
        @Header("X-Riot-Token") apiKey: String
    ): List<String>

    // 2) 매치ID로 매치 상세 정보 가져오기
    @GET("lol/match/v5/matches/{matchId}")
    suspend fun getMatchDetail(
        @Path("matchId") matchId: String,
        @Header("X-Riot-Token") apiKey: String
    ): MatchDto
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
    val gameDuration: Long,
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
    val win: Boolean
)

