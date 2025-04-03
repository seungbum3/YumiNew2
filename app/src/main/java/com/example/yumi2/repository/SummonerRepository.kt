package com.example.yumi2.repository

import android.util.Log
import com.example.yumi2.api.RiotApiService
import com.example.yumi2.model.ChampionStats
import com.example.yumi2.model.LeagueEntry
import com.example.yumi2.model.MatchHistoryItem
import com.example.yumi2.model.RecentMatchesAggregate
import com.example.yumi2.model.Summoner
import com.example.yumi2.model.SummonerResponse
import com.example.yumi2.util.ChampionMappingUtil
import com.example.yumi2.util.ChampionMappingUtil.championIdToName
import com.example.yumi2.util.extractSeason
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.yumi2.model.Player

class SummonerRepository {
    private val apiKey = "RGAPI-bdea8f74-647a-4560-89de-3143109a570d"

    private val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

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

    // 예시: 2025년 1월 10일 00:00:00 UTC (원하는 값으로 조정)
    private val CURRENT_SEASON_START = 1736467200000L

    /**
     * 챔피언 전적 조회 (솔로랭크/자유랭크/전체)
     *
     * @param queue: Int? -> 420: 솔로, 440: 자유, null: 전체 (두 큐 모두)
     * @param puuid: 소환사 PUUID
     * @param start: 불러올 경기의 시작 인덱스
     * @param count: 불러올 경기 수
     */
    suspend fun getChampionStats(
        puuid: String,
        queue: Int? = 420,
        start: Int,
        count: Int
    ): List<ChampionStats> = withContext(Dispatchers.IO) {
        val championStatsMap = mutableMapOf<Int, ChampionStatsAccumulator>()

        // Data Dragon 매핑 (챔피언 ID -> (영문ID, 한글이름))
        val version = "13.5.1"
        val mapping = ChampionMappingUtil.fetchChampionMapping(version)

        try {
            // matchIds 불러오기
            val matchIds = riotMatchApi.getMatchIdsByPuuid(
                puuid = puuid,
                start = start,
                count = count,
                queue = queue,
                apiKey = apiKey
            )
            if (matchIds.isEmpty()) {
                Log.d("SummonerRepository", "No match IDs returned for puuid: $puuid")
                return@withContext emptyList<ChampionStats>()
            }
            Log.d("SummonerRepository", "가져온 matchIds (start=$start, count=$count): $matchIds")

            // 각 경기 상세 정보를 조회
            for (matchId in matchIds) {
                try {
                    val matchDetail = riotMatchApi.getMatchDetail(matchId, apiKey)
                    Log.d("SummonerRepository", "matchId: $matchId, matchDetail fetched")

                    // 현재 시즌 필터링: gameCreation 기준
                    val gameCreation = matchDetail.info.gameCreation
                    if (gameCreation < CURRENT_SEASON_START) {
                        Log.d("SummonerRepository", "matchId: $matchId => 이전 시즌 경기, 스킵")
                        continue
                    }

                    // queue가 null인 경우, 솔로(420) 또는 자유(440) 경기만 누적
                    if (queue == null) {
                        val qid = matchDetail.info.queueId
                        if (qid != 420 && qid != 440) {
                            Log.d(
                                "SummonerRepository",
                                "matchId: $matchId has queueId $qid, not solo/flex. Skipping."
                            )
                            continue
                        }
                    }
                    // PUUID에 해당하는 participant 정보 찾기
                    val participant = matchDetail.info.participants.find { it.puuid == puuid }
                    if (participant == null) {
                        Log.e(
                            "SummonerRepository",
                            "matchId: $matchId - 참가자 정보 없음 for puuid: $puuid"
                        )
                        continue
                    }

                    // 누적: 킬, 데스, 어시, CS, 골드, 승/패
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
                    continue
                }
            }

            // 누적 데이터를 ChampionStats 리스트로 변환
            val resultList = mutableListOf<ChampionStats>()
            for ((champId, acc) in championStatsMap) {
                val fallbackName = championIdToName(champId)
                val info = mapping[champId]  // ChampionInfo(engId, korName)
                val championName = info?.korName ?: fallbackName
                val championEngId = info?.engId ?: "Unknown"
                resultList.add(
                    ChampionStats(
                        championId = champId,
                        championName = championName,
                        championEngId = championEngId,
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
            // 게임 수 내림차순 정렬
            resultList.sortByDescending { it.games }
            return@withContext resultList

        } catch (e: Exception) {
            Log.e("SummonerRepository", "챔피언 전적 가져오기 실패: ${e.toString()}")
            return@withContext emptyList<ChampionStats>()
        }
    }

    /**
     * 모든 경기 아이디를 한 번에 가져와 전체 챔피언 스탯 계산
     */
    suspend fun getChampionStatsAllAtOnce(
        puuid: String,
        queue: Int? = 420
    ): List<ChampionStats> = withContext(Dispatchers.IO) {
        val championStatsMap = mutableMapOf<Int, ChampionStatsAccumulator>()

        // Data Dragon 매핑
        val version = "13.5.1"
        val mapping = ChampionMappingUtil.fetchChampionMapping(version)

        try {
            // '모든' 경기 아이디를 모으는 로직 (페이지네이션)
            val allMatchIds = mutableListOf<String>()
            var start = 0
            val pageCount = 100
            outer@ while (true) {
                val pageMatchIds = riotMatchApi.getMatchIdsByPuuid(
                    puuid = puuid,
                    start = start,
                    count = pageCount,
                    queue = queue,
                    apiKey = apiKey
                )
                if (pageMatchIds.isEmpty()) break

                for (matchId in pageMatchIds) {
                    val matchDetail = riotMatchApi.getMatchDetail(matchId, apiKey)
                    val gameCreation = matchDetail.info.gameCreation
                    // CURRENT_SEASON_START 이후 경기만
                    if (gameCreation < CURRENT_SEASON_START) {
                        break@outer
                    } else {
                        allMatchIds.add(matchId)
                    }
                }
                if (pageMatchIds.size < pageCount) break
                start += pageCount
            }

            // 전체 시즌 경기 ID에 대해 누적 계산
            for (matchId in allMatchIds) {
                val matchDetail = riotMatchApi.getMatchDetail(matchId, apiKey)
                val participant =
                    matchDetail.info.participants.find { it.puuid == puuid } ?: continue

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
            }

            // 누적 데이터를 ChampionStats 리스트로 변환
            val resultList = mutableListOf<ChampionStats>()
            for ((champId, acc) in championStatsMap) {
                val fallbackName = championIdToName(champId)
                val info = mapping[champId]
                val championName = info?.korName ?: fallbackName
                val championEngId = info?.engId ?: "Unknown"
                resultList.add(
                    ChampionStats(
                        championId = champId,
                        championName = championName,
                        championEngId = championEngId,
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
            resultList.sortByDescending { it.games }
            return@withContext resultList

        } catch (e: Exception) {
            Log.e("SummonerRepository", "전체 챔피언 전적 가져오기 실패: $e")
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
        } catch (e: retrofit2.HttpException) {
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

    /**
     * 최근 경기 기록 조회
     */
    suspend fun getRecentMatchHistory(
        puuid: String,
        queue: Int? = null,   // 420(솔로), 440(자유), null(전체)
        start: Int = 0,
        count: Int = 10
    ): RecentMatchesAggregate = withContext(Dispatchers.IO) {

        Log.d(
            "SummonerRepository",
            "getRecentMatchHistory() called with puuid=$puuid, queue=$queue, start=$start, count=$count"
        )

        val matchList = mutableListOf<MatchHistoryItem>()

        try {
            // matchIds 불러오기
            val matchIds = riotMatchApi.getMatchIdsByPuuid(
                puuid = puuid,
                start = start,
                count = count,
                queue = queue,
                apiKey = apiKey
            )
            Log.d("SummonerRepository", "matchIds returned: $matchIds")

            if (matchIds.isEmpty()) {
                Log.w("SummonerRepository", "matchIds is empty => returning emptyList()")
                return@withContext RecentMatchesAggregate(
                    matches = emptyList(),
                    totalWins = 0,
                    totalLosses = 0,
                    averageKills = 0f,
                    averageDeaths = 0f,
                    averageAssists = 0f,
                    averageKDA = 0f
                )
            }

            // Data Dragon 버전
            val version = "13.5.1"
            // 챔피언 ID -> (영문, 한글) 맵핑
            val championMapping = ChampionMappingUtil.fetchChampionMapping(version)

            // 각 matchId에 대해 상세 정보 조회
            for (matchId in matchIds) {
                val matchDetail = riotMatchApi.getMatchDetail(matchId, apiKey)
                Log.d("SummonerRepository", "Got detail for matchId=$matchId")

                val info = matchDetail.info
                val gameDuration = info.gameDuration

                val participant = info.participants.find { it.puuid == puuid }
                if (participant == null) {
                    Log.w("SummonerRepository", "No participant found for puuid=$puuid in matchId=$matchId")
                    continue
                }

                val csPerMin = if (gameDuration > 0) {
                    (participant.totalMinionsKilled + participant.neutralMinionsKilled) / (gameDuration / 60.0)
                } else 0.0

                val queueTypeString = when (info.queueId) {
                    420 -> "솔로랭크"
                    440 -> "자유랭크"
                    430 -> "일반"
                    900 -> "URF"
                    else -> "기타"
                }

                val champInfo = championMapping[participant.championId]
                val championEngName = champInfo?.engId ?: "Unknown"
                val championKorName = champInfo?.korName ?: "알수없음"

                val kills = participant.kills
                val deaths = participant.deaths
                val assists = participant.assists
                val kdaDetail = "$kills / $deaths / $assists"
                val kdaRatio = if (deaths == 0) {
                    (kills + assists).toString()
                } else {
                    String.format("%.2f", (kills + assists) / deaths.toFloat())
                }

                val items = listOf(
                    participant.item0,
                    participant.item1,
                    participant.item2,
                    participant.item3,
                    participant.item4,
                    participant.item5,
                    participant.item6
                )

                val redTeamPlayers = info.participants
                    .filter { it.teamId == 200 }
                    .map { p ->
                        val redCsPerMin = if (gameDuration > 0) {
                            (p.totalMinionsKilled + p.neutralMinionsKilled) / (gameDuration / 60.0)
                        } else 0.0

                        Player(
                            summonerName = p.summonerName,
                            championId = p.championId,
                            championEngName = championMapping[p.championId]?.engId ?: "Unknown",
                            kills = p.kills,
                            deaths = p.deaths,
                            assists = p.assists,
                            teamId = p.teamId,
                            spell1Id = p.summoner1Id,
                            spell2Id = p.summoner2Id,
                            cs = p.totalMinionsKilled + p.neutralMinionsKilled,
                            csPerMin = redCsPerMin,
                            gold = p.goldEarned,
                            itemIds = listOf(p.item0, p.item1, p.item2, p.item3, p.item4, p.item5),
                            isWin = p.win
                        )
                    }

                val blueTeamPlayers = info.participants
                    .filter { it.teamId == 100 }
                    .map { p ->
                        val blueCsPerMin = if (gameDuration > 0) {
                            (p.totalMinionsKilled + p.neutralMinionsKilled) / (gameDuration / 60.0)
                        } else 0.0

                        Player(
                            summonerName = p.summonerName,
                            championId = p.championId,
                            championEngName = championMapping[p.championId]?.engId ?: "Unknown",
                            kills = p.kills,
                            deaths = p.deaths,
                            assists = p.assists,
                            teamId = p.teamId,
                            spell1Id = p.summoner1Id,
                            spell2Id = p.summoner2Id,
                            cs = p.totalMinionsKilled + p.neutralMinionsKilled,
                            csPerMin = blueCsPerMin,
                            gold = p.goldEarned,
                            itemIds = listOf(p.item0, p.item1, p.item2, p.item3, p.item4, p.item5),
                            isWin = p.win
                        )
                    }

                matchList.add(
                    MatchHistoryItem(
                        championId = participant.championId,
                        championEngName = championEngName,
                        championKorName = championKorName,
                        queueType = queueTypeString,
                        isWin = participant.win,
                        kills = kills,
                        deaths = deaths,
                        assists = assists,
                        kdaString = kdaDetail,
                        kdaRatioString = kdaRatio,
                        summonerSpell1 = participant.summoner1Id,
                        summonerSpell2 = participant.summoner2Id,
                        itemIds = items,
                        cs = participant.totalMinionsKilled + participant.neutralMinionsKilled,
                        gold = participant.goldEarned,
                        gameDuration = gameDuration,
                        gameCreation = info.gameCreation,
                        redTeamParticipants = redTeamPlayers,
                        blueTeamParticipants = blueTeamPlayers
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("SummonerRepository", "getRecentMatchHistory() Exception: ${e.message}")
        }

        // 통합 통계 계산
        val totalMatches = matchList.size
        val totalWins = matchList.count { it.isWin }
        val totalLosses = totalMatches - totalWins

        val sumKills = matchList.sumOf { it.kills }
        val sumDeaths = matchList.sumOf { it.deaths }
        val sumAssists = matchList.sumOf { it.assists }

        val averageKills = if (totalMatches > 0) sumKills.toFloat() / totalMatches else 0f
        val averageDeaths = if (totalMatches > 0) sumDeaths.toFloat() / totalMatches else 0f
        val averageAssists = if (totalMatches > 0) sumAssists.toFloat() / totalMatches else 0f

        val averageKDA = if (sumDeaths == 0) {
            (sumKills + sumAssists).toFloat()
        } else {
            (sumKills + sumAssists).toFloat() / sumDeaths
        }

        Log.d(
            "SummonerRepository",
            "getRecentMatchHistory() returning result size=${matchList.size}"
        )
        return@withContext RecentMatchesAggregate(
            matches = matchList,
            totalWins = totalWins,
            totalLosses = totalLosses,
            averageKills = averageKills,
            averageDeaths = averageDeaths,
            averageAssists = averageAssists,
            averageKDA = averageKDA
        )
    }
}
