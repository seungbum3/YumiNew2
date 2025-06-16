package com.example.yumi2.repository

import retrofit2.HttpException
import android.util.Log
import com.example.yumi2.api.RiotApiClient
import com.example.yumi2.api.RiotApiService
import com.example.yumi2.model.AccountResponse
import com.example.yumi2.model.ChampionStats
import com.example.yumi2.model.LeagueEntry
import com.example.yumi2.model.MatchDetail
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
import com.example.yumi2.model.RankInfo
import com.example.yumi2.model.toResponse
import kotlinx.coroutines.delay

class SummonerRepository {
    private val apiKey = "RGAPI-75d112fb-3386-43f1-976d-e6166f4417a9"

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



    // 🔁 429 처리용 retry 함수 추가 (클래스 최상단에 위치)
    suspend fun fetchMatchWithRetry(matchId: String, maxRetries: Int = 3): MatchDetail? {
        repeat(maxRetries) { attempt ->
            try {
                delay(1000L) // 기본 딜레이
                return riotMatchApi.getMatchDetail(matchId, apiKey)
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 429) {
                    val delayTime = (attempt + 1) * 1000L
                    Log.w("MatchRetry", "🔁 429 에러 - ${delayTime}ms 후 재시도 (attempt ${attempt + 1})")
                    delay(delayTime)
                } else {
                    Log.e("MatchRetry", "❌ fetchMatch 실패 - ${e.message}")
                    return null
                }
            }
        }
        Log.e("MatchRetry", "❌ 모든 재시도 실패 - matchId=$matchId")
        return null
    }

    suspend fun getChampionStats(
        puuid: String,
        queue: Int? = 420,
        start: Int,
        count: Int
    ): List<ChampionStats> = withContext(Dispatchers.IO) {
        val championStatsMap = mutableMapOf<Int, ChampionStatsAccumulator>()

        // Data Dragon 매핑 (챔피언 ID -> (영문ID, 한글이름))
        val mapping = ChampionMappingUtil.fetchLatestChampionMapping()

        try {
            // matchIds 불러오기
            val matchIds = riotMatchApi.getMatchIdsByPuuid(
                puuid = puuid,
                start = 0,
                count = 50,
                queue = null,
                apiKey = apiKey
            )
            Log.d("MatchHistory", "받은 matchIds: $matchIds")
            if (matchIds.isEmpty()) {
                Log.d("SummonerRepository", "No match IDs returned for puuid: $puuid")
                return@withContext emptyList<ChampionStats>()
            }
            Log.d("SummonerRepository", "가져온 matchIds (start=$start, count=$count): $matchIds")

            // 각 경기 상세 정보를 조회
            for (matchId in matchIds) {
                val matchDetail = fetchMatchWithRetry(matchId) ?: continue
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

    suspend fun getTop3ChampionStatsQuick(puuid: String, queue: Int): List<ChampionStats> {
        return withContext(Dispatchers.IO) {
            val matchIds = riotMatchApi.getMatchIdsByPuuid(
                puuid = puuid,
                start = 0,
                count = 5,
                queue = queue,
                apiKey = apiKey
            )

            val statsMap = mutableMapOf<Int, ChampionStatsAccumulator>()
            val mapping = ChampionMappingUtil.fetchLatestChampionMapping()

            for (matchId in matchIds) {
                try {
                    delay(1200L)
                    val matchDetail = riotMatchApi.getMatchDetail(matchId, apiKey)
                    val participant = matchDetail.info.participants.find { it.puuid == puuid } ?: continue
                    val champId = participant.championId

                    val acc = statsMap.getOrPut(champId) { ChampionStatsAccumulator(champId) }
                    acc.games++
                    if (participant.win) acc.wins++
                    acc.kills += participant.kills
                    acc.deaths += participant.deaths
                    acc.assists += participant.assists
                    acc.cs += (participant.totalMinionsKilled + participant.neutralMinionsKilled)
                    acc.gold += participant.goldEarned

                } catch (_: Exception) {
                    continue
                }
            }

            return@withContext statsMap.values.sortedByDescending { it.games }
                .take(3)
                .map { acc ->
                    val info = mapping[acc.championId]
                    ChampionStats(
                        championId = acc.championId,
                        championName = info?.korName ?: "알 수 없음",
                        championEngId = info?.engId ?: "Unknown",
                        games = acc.games,
                        wins = acc.wins,
                        kills = acc.kills,
                        deaths = acc.deaths,
                        assists = acc.assists,
                        cs = acc.cs,
                        gold = acc.gold
                    )
                }
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
            val allMatchIds = mutableListOf<String>()
            var start = 0
            val pageCount = 20

            outer@ while (true) {
                val pageMatchIds = riotMatchApi.getMatchIdsByPuuid(
                    puuid = puuid,
                    start = start,
                    count = pageCount,
                    queue = queue,  // 그대로 넘김
                    apiKey = apiKey
                )

                if (pageMatchIds.isEmpty()) break

                for (matchId in pageMatchIds) {
                    val matchDetail = riotMatchApi.getMatchDetail(matchId, apiKey)
                    val gameCreation = matchDetail.info.gameCreation
                    val queueId = matchDetail.info.queueId

                    Log.d("ChampionStatsDebug", "🔍 matchId=$matchId, queueId=$queueId, gameCreation=$gameCreation")

                    // 시즌 필터링
                    if (gameCreation < CURRENT_SEASON_START) {
                        Log.d("ChampionStatsDebug", "⛔ matchId=$matchId 제외됨: 이전 시즌")
                        break@outer
                    }

                    // queue 필터 (null이면 통과, 지정되었으면 일치 여부 확인)
                    if (queue != null && queueId != queue) {
                        Log.d("ChampionStatsDebug", "⛔ matchId=$matchId 제외됨: queue 불일치 ($queueId)")
                        continue
                    }

                    allMatchIds.add(matchId)
                }

                if (pageMatchIds.size < pageCount) break
                start += pageCount
            }

            for (matchId in allMatchIds) {
                val matchDetail = riotMatchApi.getMatchDetail(matchId, apiKey)
                val participant = matchDetail.info.participants.find { it.puuid == puuid } ?: continue

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

            Log.d("ChampionStatsDebug", "✅ 최종 통계 수집 완료 — resultList.size=${resultList.size}")
            resultList.sortByDescending { it.games }
            return@withContext resultList

        } catch (e: Exception) {
            Log.e("ChampionStats", "❌ 전체 챔피언 전적 가져오기 실패: ${e.message}")
            return@withContext emptyList()
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

                // 랭크 정보 가져오기
                val rankInfo = getRankInfo(summonerId)

                // ✅ LeagueEntry → RankInfo 로 변환
                val solo = rankInfo.first?.let {
                    RankInfo(
                        tier = it.tier,
                        rank = it.rank,
                        leaguePoints = it.leaguePoints,
                        wins = it.wins,
                        losses = it.losses
                    )
                }

                val flex = rankInfo.second?.let {
                    RankInfo(
                        tier = it.tier,
                        rank = it.rank,
                        leaguePoints = it.leaguePoints,
                        wins = it.wins,
                        losses = it.losses
                    )
                }

                val summonerResponse = SummonerResponse(
                    puuid = puuidResponse.puuid,
                    summonerId = summonerId,
                    gameName = response.gameName,
                    tagLine = response.tagLine,
                    profileIconId = puuidResponse.profileIconId,
                    summonerLevel = puuidResponse.summonerLevel,
                    soloRank = solo,
                    flexRank = flex
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
            // 1. Summoner API를 호출합니다.
            val summonerResp = riotGameApi.getSummonerByPuuid(puuid, apiKey)
            Log.d("SummonerRepository", "✔️ Summoner API 응답 for puuid $puuid: $summonerResp")
            Log.d("SummonerRepository", "Summoner API - name: '${summonerResp.name}'")

            var finalName = summonerResp.name
            if (finalName.isNullOrBlank()) {
                Log.d("SummonerRepository", "Summoner API에서 name이 비어있어 Account API 호출: puuid $puuid")
                val accountResp = riotAccountApi.getAccountByPuuid(puuid, apiKey)
                Log.d("SummonerRepository", "✔️ Account API 응답 for puuid $puuid: $accountResp")
                finalName = if (accountResp.gameName.isNullOrBlank()) "Unknown" else accountResp.gameName
                Log.d("SummonerRepository", "최종 사용될 name: '$finalName'")
            } else {
                Log.d("SummonerRepository", "Summoner API에서 유효한 name을 받았습니다: '$finalName'")
            }

            Summoner(
                summonerId = summonerResp.summonerId,
                puuid = summonerResp.puuid,
                name = finalName,   // 변경된 필드명 사용
                profileIconId = summonerResp.profileIconId,
                summonerLevel = summonerResp.summonerLevel
            )
        } catch (e: Exception) {
            Log.e("SummonerRepository", "❌ PUUID로 소환사 정보 가져오기 실패 for puuid $puuid: ${e.toString()}")
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
        count: Int = 5
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

                val accountInfo = riotAccountApi.getAccountByPuuid(participant.puuid, apiKey)

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

                val accountCache = mutableMapOf<String, AccountResponse>() // puuid -> account

                val redTeamPlayers = info.participants
                    .filter { it.teamId == 200 }
                    .map { p ->
                        val accountInfo = accountCache.getOrPut(p.puuid) {
                            riotAccountApi.getAccountByPuuid(p.puuid, apiKey).toResponse()
                        }

                        val redCsPerMin = if (gameDuration > 0) {
                            (p.totalMinionsKilled + p.neutralMinionsKilled) / (gameDuration / 60.0)
                        } else 0.0

                        Player(
                            gameName = accountInfo.gameName,
                            tagLine = accountInfo.tagLine,
                            summonerName = "${accountInfo.gameName}#${accountInfo.tagLine}",
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
                        val accountInfo = accountCache.getOrPut(p.puuid) {
                            riotAccountApi.getAccountByPuuid(p.puuid, apiKey).toResponse()
                        }

                        val blueCsPerMin = if (gameDuration > 0) {
                            (p.totalMinionsKilled + p.neutralMinionsKilled) / (gameDuration / 60.0)
                        } else 0.0

                        Player(
                            gameName = accountInfo.gameName,
                            tagLine = accountInfo.tagLine,
                            summonerName = "${accountInfo.gameName}#${accountInfo.tagLine}",
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
                        queueId = info.queueId,
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
