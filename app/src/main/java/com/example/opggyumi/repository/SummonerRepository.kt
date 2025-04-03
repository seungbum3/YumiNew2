package com.example.opggyumi.repository

import android.util.Log
import com.example.opggyumi.api.RiotApiService
import com.example.opggyumi.model.ChampionStats
import com.example.opggyumi.model.LeagueEntry
import com.example.opggyumi.model.Summoner
import com.example.opggyumi.model.SummonerResponse
import com.example.opggyumi.util.ChampionMappingUtil
import com.example.opggyumi.util.ChampionMappingUtil.championIdToName
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SummonerRepository {
    private val apiKey = "RGAPI-18121938-297b-4c52-8248-fc2294026657"

    private val db = FirebaseFirestore.getInstance()


    private val riotAccountApi = Retrofit.Builder()
        .baseUrl("https://asia.api.riotgames.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RiotApiService::class.java)

    private val riotGameApi = Retrofit.Builder()
        .baseUrl("https://kr.api.riotgames.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RiotApiService::class.java)

    private val riotMatchApi = Retrofit.Builder()
        .baseUrl("https://asia.api.riotgames.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RiotApiService::class.java)

    // 챔피언 전적 조회 (Match-V5 API 활용)
    suspend fun getChampionStats(puuid: String): List<ChampionStats> = withContext(Dispatchers.IO) {
        val championStatsMap = mutableMapOf<Int, ChampionStatsAccumulator>()

        // 추가: Data Dragon 매핑 불러오기 (버전은 적절히 결정)
        val version = "13.5.1"
        val mapping = ChampionMappingUtil.fetchChampionMapping(version)

        try {
            // 1) 최근 n경기 matchId
            val matchIds = riotMatchApi.getMatchIdsByPuuid(
                puuid = puuid,
                start = 0,
                count = 5,  // 원하는 경기 수
                apiKey = apiKey
            )
            if (matchIds.isEmpty()) {
                Log.e("SummonerRepository", "No match IDs returned for puuid: $puuid")
                return@withContext emptyList<ChampionStats>()
            }
            Log.d("SummonerRepository", "가져온 matchIds: $matchIds")

            // 2) 각 경기 상세 -> participant 누적
            for (matchId in matchIds) {
                try {
                    val matchDetail = riotMatchApi.getMatchDetail(matchId, apiKey)
                    Log.d("SummonerRepository", "matchId: $matchId, matchDetail fetched")

                    val participant = matchDetail.info.participants.find { it.puuid == puuid }
                    if (participant == null) {
                        Log.e("SummonerRepository", "matchId: $matchId - 참가자 정보 없음 for puuid: $puuid")
                        continue
                    }

                    val champId = participant.championId
                    val kills = participant.kills
                    val deaths = participant.deaths
                    val assists = participant.assists
                    val totalCS = participant.totalMinionsKilled + participant.neutralMinionsKilled
                    val gold = participant.goldEarned
                    val isWin = participant.win

                    val accumulator = championStatsMap.getOrPut(champId) {
                        ChampionStatsAccumulator(championId = champId)
                    }
                    accumulator.games++
                    if (isWin) accumulator.wins++
                    accumulator.kills += kills
                    accumulator.deaths += deaths
                    accumulator.assists += assists
                    accumulator.cs += totalCS
                    accumulator.gold += gold

                } catch (e: Exception) {
                    Log.e("SummonerRepository", "오류 발생 - matchId: $matchId, error: ${e.toString()}")
                    // 한 경기 실패 시 계속 진행
                    continue
                }
            }

            // 3) 누적 -> ChampionStats 리스트 변환
            val resultList = mutableListOf<ChampionStats>()
            for ((champId, acc) in championStatsMap) {
                // 기존 championIdToName(champId) 호출
                val fallbackName = championIdToName(champId)

                // 새로 불러온 mapping에서 영문 ID와 한글 이름 가져오기
                val info = mapping[champId]  // ChampionInfo(engId, korName)
                val championName = info?.korName ?: fallbackName  // ko_KR 이름 or 기존 fallback
                val championEngId = info?.engId ?: "Unknown"      // 아이콘 로드용

                resultList.add(
                    ChampionStats(
                        championId = champId,
                        championName = championName,
                        championEngId = championEngId,  // 아이콘 로드용 필드

                        games = acc.games,
                        wins = acc.wins,
                        kills = acc.kills,
                        deaths = acc.deaths,
                        assists = acc.assists,
                        cs = acc.cs,
                        gold = acc.gold
                    )
                )
            }
            return@withContext resultList

        } catch (e: Exception) {
            Log.e("SummonerRepository", "챔피언 전적 가져오기 실패: ${e.toString()}")
            return@withContext emptyList<ChampionStats>()
        }
    }


    // 내부 누적 계산용 데이터 클래스
    private data class ChampionStatsAccumulator(
        val championId: Int,
        var games: Int = 0,
        var wins: Int = 0,
        var kills: Int = 0,
        var deaths: Int = 0,
        var assists: Int = 0,
        var cs: Int = 0,
        var gold: Int = 0
    )

    // LoL 소환사 정보 조회 (Riot ID 기반)
    // 발로란트 전적은 사용하지 않으므로, by-riot-id API는 그대로 유지하거나 제거 가능함
    suspend fun getSummonerInfo(gameName: String, tagLine: String, uid: String): SummonerResponse? {
        return try {
            val response = riotAccountApi.getSummonerInfo(gameName, tagLine, apiKey)

            if (response.puuid.isNotEmpty()) {
                val puuidResponse = riotGameApi.getSummonerByPuuid(response.puuid, apiKey)
                Log.d("SummonerRepository", "puuidResponse 객체: $puuidResponse")

                val summonerId = puuidResponse.summonerId ?: ""
                if (summonerId.isEmpty()) {
                    Log.e("SummonerRepository", "⚠️ Summoner ID가 없습니다! 랭크 정보를 불러올 수 없습니다.")
                    return null
                }

                val rankInfo = getRankInfo(summonerId)

                val summonerResponse = SummonerResponse(
                    puuid = puuidResponse.puuid,
                    summonerId = summonerId,
                    gameName = response.gameName,
                    tagLine = response.tagLine,
                    profileIconId = puuidResponse.profileIconId,
                    summonerLevel = puuidResponse.summonerLevel,
                    soloRank = rankInfo.first,
                    flexRank = rankInfo.second
                )

                db.collection("users")
                    .document(uid)
                    .collection("SearchNameList")
                    .document(summonerId)
                    .set(summonerResponse, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("SummonerRepository", "Firestore 저장 성공!")
                    }
                    .addOnFailureListener { e ->
                        Log.e("SummonerRepository", "Firestore 저장 실패: $e")
                    }
                summonerResponse

            } else {
                Log.e("SummonerRepository", "⚠️ PUUID가 비어 있음.")
                null
            }
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e("SummonerRepository", "❌ 소환사 정보 가져오기 실패: HTTP ${e.code()} - $errorBody")
            null
        } catch (e: Exception) {
            Log.e("SummonerRepository", "❌ 소환사 정보 가져오기 실패: ${e.toString()}")
            null
        }
    }

    // PUUID 기반 소환사 정보 조회
    suspend fun getSummonerByPuuid(puuid: String): Summoner? {
        return try {
            val response = riotGameApi.getSummonerByPuuid(puuid, apiKey)
            Log.d("SummonerRepository", "✔️ API 응답: $response")
            Summoner(
                summonerId = response.summonerId,
                puuid = response.puuid,
                gameName = response.gameName ?: "Unknown",
                profileIconId = response.profileIconId,
                summonerLevel = response.summonerLevel
            )
        } catch (e: Exception) {
            Log.e("SummonerRepository", "❌ PUUID로 소환사 정보 가져오기 실패: ${e.toString()}")
            null
        }
    }

    // summonerId 기반 랭크 정보 조회
    suspend fun getRankInfo(summonerId: String): Pair<LeagueEntry?, LeagueEntry?> {
        return try {
            val response = riotGameApi.getRankInfo(summonerId, apiKey)
            val soloRank = response.find { it.queueType == "RANKED_SOLO_5x5" }
            val flexRank = response.find { it.queueType == "RANKED_FLEX_SR" }
            Pair(soloRank, flexRank)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }
}
