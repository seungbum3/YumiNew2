package com.example.yumi2.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yumi2.api.RiotApiClient
import com.example.yumi2.model.ChampionStats
import com.example.yumi2.model.ChampionStatsCache
import com.example.yumi2.model.LeagueEntry
import com.example.yumi2.model.MatchHistoryCache
import com.example.yumi2.model.MatchHistoryItem
import com.example.yumi2.model.RankInfo
import com.example.yumi2.model.RecentMatchesAggregate
import com.example.yumi2.model.SummonerResponse
import com.example.yumi2.repository.SummonerRepository
import com.example.yumi2.util.ChampionMappingUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class SummonerViewModel : ViewModel() {

    private val riotApiKey = "RGAPI-311dcbc8-723e-4e24-9318-1e0a2caf28f4"

    private val repository = SummonerRepository()

    private val _recentMatchesStats = MutableStateFlow<RecentMatchesAggregate?>(null)
    val recentMatchesStats: StateFlow<RecentMatchesAggregate?> = _recentMatchesStats

    private val _matchHistoryList = MutableStateFlow<List<MatchHistoryItem>>(emptyList())
    val matchHistoryList: StateFlow<List<MatchHistoryItem>> = _matchHistoryList

    private val _summonerInfo = MutableStateFlow<SummonerResponse?>(null)
    val summonerInfo: StateFlow<SummonerResponse?> = _summonerInfo

    private val allChampionStats = mutableListOf<ChampionStats>()
    private val displayedChampionStats = mutableListOf<ChampionStats>()

    private val _championStats = MutableStateFlow<List<ChampionStats>>(emptyList())
    val championStats: StateFlow<List<ChampionStats>> = _championStats

    private var currentIndex = 0
    private val pageSize = 4

    fun searchSummonerByRiotId(gameName: String, tagLine: String, uid: String) {
        viewModelScope.launch {
            try {
                val response = RiotApiClient.api.getSummonerInfo(
                    gameName = gameName,
                    tagLine = tagLine,
                    apiKey = riotApiKey
                )
                _summonerInfo.value = response
                Log.d("ViewModel", "✅ Riot ID 검색 성공: $response")

                // 🔽 응답 받은 후 캐시된 전적 로딩 시도
                response.puuid?.let { puuid ->
                    loadRecentMatches(puuid, null)
                }

            } catch (e: Exception) {
                _summonerInfo.value = null
                Log.e("ViewModel", "❌ Riot ID 검색 실패: ${e.message}")
            }
        }
    }


    // ✅ 2. 소환사명 기반 검색 → Summoner → PUUID → AccountDto 변환
    fun searchSummonerByName(name: String, uid: String) {
        viewModelScope.launch {
            try {
                val summoner = RiotApiClient.api.getSummonerByName(
                    summonerName = name,
                    apiKey = riotApiKey
                )

                val account = RiotApiClient.api.getAccountByPuuid(
                    puuid = summoner.puuid,
                    apiKey = riotApiKey
                )

                val result = SummonerResponse(
                    puuid = account.puuid,
                    summonerId = summoner.summonerId,
                    gameName = account.gameName,
                    tagLine = account.tagLine,
                    profileIconId = summoner.profileIconId,
                    summonerLevel = summoner.summonerLevel,
                    soloRank = null,
                    flexRank = null
                )

                _summonerInfo.value = result
                Log.d("ViewModel", "✅ Summoner Name 검색 + Account 변환 성공: $result")

                // 🔽 응답 받은 후 캐시된 전적 로딩 시도
                result.puuid.let { puuid ->
                    loadRecentMatches(puuid, null)
                }

            } catch (e: Exception) {
                _summonerInfo.value = null
                Log.e("ViewModel", "❌ Summoner Name 검색 실패: ${e.message}")
            }
        }
    }


    fun loadAllRecentMatchesSafely(puuid: String) {
        viewModelScope.launch {
            loadRecentMatches(puuid, null) // 전체 큐
            delay(3000L)
            loadRecentMatches(puuid, 420)  // 솔랭
            delay(3000L)
            loadRecentMatches(puuid, 440)  // 자랭
            delay(3000L)
            loadRecentMatches(puuid, 450)  // 칼바람
        }
    }

    fun loadSoloFlexRecentMatches(puuid: String) {
        viewModelScope.launch {
            val solo = repository.getRecentMatchHistory(puuid, queue = 420)
            val flex = repository.getRecentMatchHistory(puuid, queue = 440)

            // 두 리스트 합치고 시간순 정렬
            val combined = (solo.matches + flex.matches)
                .sortedByDescending { it.gameCreation }
                .take(3)

            val totalWins = combined.count { it.isWin }
            val totalLosses = combined.size - totalWins
            val sumKills = combined.sumOf { it.kills }
            val sumDeaths = combined.sumOf { it.deaths }
            val sumAssists = combined.sumOf { it.assists }

            val averageKills = if (combined.isNotEmpty()) sumKills.toFloat() / combined.size else 0f
            val averageDeaths = if (combined.isNotEmpty()) sumDeaths.toFloat() / combined.size else 0f
            val averageAssists = if (combined.isNotEmpty()) sumAssists.toFloat() / combined.size else 0f
            val averageKDA = if (sumDeaths == 0) {
                (sumKills + sumAssists).toFloat()
            } else {
                (sumKills + sumAssists).toFloat() / sumDeaths
            }

            _recentMatchesStats.value = RecentMatchesAggregate(
                matches = combined,
                totalWins = totalWins,
                totalLosses = totalLosses,
                averageKills = averageKills,
                averageDeaths = averageDeaths,
                averageAssists = averageAssists,
                averageKDA = averageKDA
            )
        }
    }

    fun loadTop3ChampionStatsQuick(puuid: String, queue: Int) {
        viewModelScope.launch {
            val top3Stats = repository.getTop3ChampionStatsQuick(puuid, queue)
            _championStats.value = top3Stats
        }
    }

    private val savingFlags = mutableSetOf<String>()  // 중복 저장 방지용

    fun loadRecentMatches(puuid: String, queue: Int? = null) {
        viewModelScope.launch {
            delay(1000L)

            val firestore = FirebaseFirestore.getInstance()
            val docId = if (queue == null) "${puuid}_all" else "${puuid}_queue$queue"
            val docRef = firestore.collection("summoner_match_cache").document(docId)

            Log.d("loadRecentMatches", "🚀 시작 — puuid=$puuid, queue=$queue, docId=$docId")

            // 1) 캐시 조회
            try {
                val snapshot = docRef.get().await()
                Log.d("loadRecentMatches", "📄 캐시 조회 성공 — 문서 존재 여부: ${snapshot.exists()}")

                val cached = try {
                    snapshot.toObject(MatchHistoryCache::class.java)
                } catch (e: Exception) {
                    Log.e("loadRecentMatches", "❌ toObject 캐스팅 실패: ${e.message}", e)
                    null
                }

                Log.d("loadRecentMatches", "📦 toObject 결과: $cached")

                if (cached != null) {
                    if (cached.isEmpty) {
                        Log.d("loadRecentMatches", "🚫 이전에 빈 결과 캐시됨 → API 재호출 생략")
                        _matchHistoryList.value = emptyList()
                        return@launch
                    } else if (cached.matches.isNotEmpty()) {
                        Log.d("loadRecentMatches", "✅ 캐시 사용됨. match 개수: ${cached.matches.size}")
                        _matchHistoryList.value = cached.matches
                        return@launch
                    }
                } else {
                    Log.w("loadRecentMatches", "⚠ 캐시가 null입니다.")
                }
            } catch (e: Exception) {
                Log.e("loadRecentMatches", "❌ 캐시 조회 실패: ${e.message}")
                // 계속 진행하여 API 호출
            }

            // ✅ 중복 요청 방지
            if (savingFlags.contains(docId)) {
                Log.d("loadRecentMatches", "⏳ 중복 저장 요청 차단됨 ($docId)")
                return@launch
            }

            savingFlags.add(docId)
            try {
                // 2) Riot API 호출
                Log.d("loadRecentMatches", "🌐 Riot API 호출 시작 (queue=$queue)")
                val aggregate = repository.getRecentMatchHistory(puuid, queue, start = 0, count = 5)
                Log.d("loadRecentMatches", "✅ Riot API 응답 — match 수: ${aggregate.matches.size}")

                _matchHistoryList.value = aggregate.matches

                // 3) 빈 결과일 경우 저장 생략
                if (aggregate.matches.isEmpty()) {
                    Log.d("loadRecentMatches", "🚫 빈 결과라 캐시 저장 생략 ($docId)")
                    return@launch
                }

                // 4) 캐시 저장
                val cache = MatchHistoryCache(
                    matches = aggregate.matches,
                    updatedAt = System.currentTimeMillis(),
                    isEmpty = false
                )
                Log.d("loadRecentMatches", "💾 캐시 저장 시도 — matches=${cache.matches.size}, isEmpty=${cache.isEmpty}")
                docRef.set(cache).await()
                Log.d("loadRecentMatches", "📦 Firestore에 전적 캐시 저장 완료 (docId=$docId)")

            } catch (e: Exception) {
                Log.e("loadRecentMatches", "❌ Riot API 호출 실패: ${e.message}", e)
            } finally {
                savingFlags.remove(docId)  // 플래그 제거
            }
        }
    }



    fun loadChampionStatsAll(puuid: String, queue: Int?) {
        viewModelScope.launch {
            Log.d("ChampionStats", "▶ loadChampionStatsAll() 시작 — puuid=$puuid, queue=$queue")

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) {
                Log.w("ChampionStats", "⛔ 현재 사용자 UID를 가져올 수 없습니다. 함수 종료.")
                return@launch
            }

            val docId = if (queue == null) "${puuid}_all" else "${puuid}_queue$queue"
            val docRef = FirebaseFirestore.getInstance()
                .collection("summoners")
                .document(uid)
                .collection("champion_stats")
                .document(docId)

            try {
                val snapshot = docRef.get().await()
                Log.d("ChampionStats", "📄 snapshot.exists() = ${snapshot.exists()}")

                val cached = try {
                    snapshot.toObject(ChampionStatsCache::class.java)
                } catch (e: Exception) {
                    Log.e("ChampionStats", "❌ toObject 변환 실패 → 문서 삭제 후 새로고침: ${e.message}", e)
                    // 🔥 잘못된 캐시 문서 삭제
                    docRef.delete().await()
                    null
                }

                if (cached != null && !cached.stats.isNullOrEmpty()) {
                    val stats = cached.stats!!
                    Log.d("ChampionStats", "✅ 캐시된 stats 사용 (size=${stats.size})")
                    val top3 = stats.sortedByDescending { it.games }.take(3)
                    displayedChampionStats.clear()
                    displayedChampionStats.addAll(top3)
                    _championStats.value = displayedChampionStats.toList()
                    return@launch
                } else {
                    Log.w("ChampionStats", "⚠ 캐시가 비어있거나 잘못된 구조입니다. → API 재요청")
                }
            } catch (e: Exception) {
                Log.e("ChampionStats", "❌ 캐시 조회 실패: ${e.message}", e)
            }

            // 🔄 API 호출
            try {
                val stats = repository.getChampionStatsAllAtOnce(puuid, queue)
                Log.d("ChampionStats", "🌐 API 응답 수신: ${stats.size}개")

                if (stats.isEmpty()) {
                    Log.w("ChampionStats", "⚠ 응답이 비어 있음 → 캐시 저장 생략")
                    return@launch
                }

                val top3 = stats.sortedByDescending { it.games }.take(3)
                displayedChampionStats.clear()
                displayedChampionStats.addAll(top3)
                _championStats.value = displayedChampionStats.toList()

                val cache = ChampionStatsCache(stats = stats, updatedAt = System.currentTimeMillis())
                docRef.set(cache)
                    .addOnSuccessListener { Log.d("ChampionStats", "✅ 캐시 저장 성공") }
                    .addOnFailureListener { e -> Log.e("ChampionStats", "❌ 캐시 저장 실패: ${e.message}", e) }

            } catch (e: Exception) {
                Log.e("ChampionStats", "❌ API 호출 중 예외: ${e.message}", e)
            }
        }
    }




    fun loadNextPage() {
        if (currentIndex >= allChampionStats.size) return

        val endIndex = (currentIndex + pageSize).coerceAtMost(allChampionStats.size)
        val nextChunk = allChampionStats.subList(currentIndex, endIndex)
        displayedChampionStats.addAll(nextChunk)
        _championStats.value = displayedChampionStats.toList()
        currentIndex = endIndex
    }

    fun resetStats() {
        allChampionStats.clear()
        displayedChampionStats.clear()
        currentIndex = 0
        _championStats.value = emptyList()
    }

    suspend fun getLatestLolVersion(): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://ddragon.leagueoflegends.com/api/versions.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val versions = JSONArray(response)
                    versions.getString(0)
                } else {
                    "13.6.1"
                }
            } catch (e: Exception) {
                "13.6.1"
            }
        }
    }

    private suspend fun getPuuidFromFirestore(gameName: String, tagLine: String, uid: String): String? {
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val querySnapshot = firestore.collection("users")
                .document(uid)
                .collection("SearchNameList")
                .whereEqualTo("gameName", gameName)
                .whereEqualTo("tagLine", tagLine)
                .get()
                .await()
            if (!querySnapshot.isEmpty) querySnapshot.documents[0].getString("puuid") else null
        } catch (e: Exception) {
            Log.e("SummonerViewModel", "Firestore에서 PUUID 가져오기 실패: ${e.message}")
            null
        }
    }

    private suspend fun getSummonerFromFirestore(uid: String, gameName: String, tagLine: String): SummonerResponse? {
        return try {
            val safeDocId = "${gameName}_${tagLine}"  // 🔄 언더스코어 형식 통일
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection("users")
                .document(uid)
                .collection("SearchNameList")
                .document(safeDocId)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) return null

            val data = snapshot.data ?: return null
            val soloRankData = data["soloRank"] as? Map<String, Any>
            val flexRankData = data["flexRank"] as? Map<String, Any>

            val soloRank = soloRankData?.let {
                RankInfo(
                    tier = it["tier"] as? String ?: "",
                    rank = it["rank"] as? String ?: "",
                    leaguePoints = (it["leaguePoints"] as? Long)?.toInt() ?: 0,
                    wins = (it["wins"] as? Long)?.toInt() ?: 0,
                    losses = (it["losses"] as? Long)?.toInt() ?: 0
                )
            }

            val flexRank = flexRankData?.let {
                RankInfo(
                    tier = it["tier"] as? String ?: "",
                    rank = it["rank"] as? String ?: "",
                    leaguePoints = (it["leaguePoints"] as? Long)?.toInt() ?: 0,
                    wins = (it["wins"] as? Long)?.toInt() ?: 0,
                    losses = (it["losses"] as? Long)?.toInt() ?: 0
                )
            }

            SummonerResponse(
                puuid = data["puuid"] as? String ?: "",
                summonerId = data["summonerId"] as? String ?: "",
                gameName = data["gameName"] as? String ?: "",
                tagLine = data["tagLine"] as? String ?: "",
                profileIconId = (data["profileIconId"] as? Long)?.toInt() ?: 0,
                summonerLevel = (data["summonerLevel"] as? Long)?.toInt() ?: 0,
                soloRank = soloRank,
                flexRank = flexRank
            )
        } catch (e: Exception) {
            Log.e("Firestore", "Firestore에서 소환사 정보 가져오기 실패: ${e.message}")
            null
        }
    }


    fun searchSummoner(gameName: String, tagLine: String, uid: String) {
        viewModelScope.launch {
            val firestore = FirebaseFirestore.getInstance()
            val userDoc = firestore.collection("users").document(uid)
                .collection("SearchNameList")
                .document("$gameName#$tagLine")

            // 1) 먼저 Firestore에서 읽어보기
            val snap = userDoc.get().await()
            if (snap.exists()) {
                _summonerInfo.value = snap.toObject(SummonerResponse::class.java)
                return@launch
            }

            // 2) 없으면 Riot API 호출
            try {
                val result = repository.getSummonerInfo(gameName, tagLine, uid)
                result?.let {
                    _summonerInfo.value = it

                    // 3) Firestore에 저장 (캐시)
                    userDoc.set(it).await()
                }
            } catch (e: Exception) {
                Log.e("SummonerViewModel", "소환사 조회 실패: $e")
                // 필요하면 _summonerInfo.value = null 처리
            }
        }
    }


    suspend fun getSummonerIconUrl(profileIconId: Int): String {
        val latestVersion = getLatestLolVersion()
        return "https://ddragon.leagueoflegends.com/cdn/$latestVersion/img/profileicon/$profileIconId.png"
    }
}