package com.example.yumi2.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yumi2.model.ChampionStats
import com.example.yumi2.model.LeagueEntry
import com.example.yumi2.model.MatchHistoryItem
import com.example.yumi2.model.RecentMatchesAggregate
import com.example.yumi2.model.SummonerResponse
import com.example.yumi2.repository.SummonerRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class SummonerViewModel : ViewModel() {

    private val repository = SummonerRepository()

    private val _recentMatchesStats = MutableStateFlow<RecentMatchesAggregate?>(null)
    val recentMatchesStats: StateFlow<RecentMatchesAggregate?> = _recentMatchesStats


    // 매치 히스토리를 담을 StateFlow
    private val _matchHistoryList = MutableStateFlow<List<MatchHistoryItem>>(emptyList())
    val matchHistoryList: StateFlow<List<MatchHistoryItem>> = _matchHistoryList

    // 1) 소환사 정보
    private val _summonerInfo = MutableStateFlow<SummonerResponse?>(null)
    val summonerInfo: StateFlow<SummonerResponse?> = _summonerInfo

    // 2) 전체 챔피언 전적 (한 번에 모두 불러온 리스트)
    private val allChampionStats = mutableListOf<ChampionStats>()

    // 3) 실제로 UI에 표시할 챔피언 전적 (4개씩 끊어서 표시)
    private val displayedChampionStats = mutableListOf<ChampionStats>()

    // 4) StateFlow로 UI에 노출
    private val _championStats = MutableStateFlow<List<ChampionStats>>(emptyList())
    val championStats: StateFlow<List<ChampionStats>> = _championStats

    // 페이지네이션 관련 변수
    private var currentIndex = 0
    private val pageSize = 4

    fun loadRecentMatches(puuid: String, queue: Int? = null) {
        Log.d("SummonerViewModel", "loadRecentMatches() called with puuid=$puuid, queue=$queue")
        viewModelScope.launch {
            try {
                // Repository에서 RecentMatchesAggregate 객체를 받아옴
                val aggregate = repository.getRecentMatchHistory(puuid, queue, start = 0, count = 10)
                // aggregate.matches => List<MatchHistoryItem>
                // aggregate.totalWins, averageKills 등 => 통합 통계

                Log.d("SummonerViewModel", "loadRecentMatches() -> matches.size=${aggregate.matches.size}")

                // (1) 매치 리스트만 StateFlow에 저장
                _matchHistoryList.value = aggregate.matches

                // (2) 통합 통계(승/패, 평균 KDA 등)도 StateFlow에 저장
                _recentMatchesStats.value = aggregate

            } catch (e: Exception) {
                Log.e("SummonerViewModel", "loadRecentMatches() Exception: $e")
            }
        }
    }


    // 필요하면 더 불러올 수도 있음
    fun loadMoreMatches(puuid: String, queue: Int? = null, start: Int, count: Int) {
        viewModelScope.launch {
            try {
                // aggregate: RecentMatchesAggregate 객체 반환 (매치 리스트 + 통계)
                val aggregate = repository.getRecentMatchHistory(puuid, queue, start, count)
                // 기존 리스트에 이어 붙이기 위해 aggregate.matches 사용
                val currentList = _matchHistoryList.value.toMutableList()
                currentList.addAll(aggregate.matches)
                _matchHistoryList.value = currentList

                // 통계 업데이트(간단히 기존 통계에 새로운 매치들을 덧붙이는 방식은 복잡하므로,
                // 전체 통계를 다시 계산하는 로직을 별도로 구현하는 것이 좋습니다.)
            } catch (e: Exception) {
                Log.e("SummonerViewModel", "loadMoreMatches() Exception: $e")
            }
        }
    }


    fun loadChampionStatsAll(puuid: String, queue: Int?) {
        viewModelScope.launch {
            // 1) Repository에서 "전체 전적"을 한 번에 불러오는 함수 (getChampionStatsAllAtOnce)
            val stats = repository.getChampionStatsAllAtOnce(puuid, queue)

            // 2) 기존 데이터 초기화
            allChampionStats.clear()
            allChampionStats.addAll(stats)

            // 3) 표시 목록 초기화
            displayedChampionStats.clear()
            currentIndex = 0

            // 4) 첫 4개만 UI에 표시
            loadNextPage()
        }
    }

    fun loadNextPage() {
        if (currentIndex >= allChampionStats.size) {
            // 이미 모든 챔피언 전적을 표시했다면 종료
            return
        }

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

    // 최신 LOL 버전 가져오기 (외부 호출 가능)
    suspend fun getLatestLolVersion(): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://ddragon.leagueoflegends.com/api/versions.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val versions = JSONArray(response)
                    versions.getString(0) // 첫 번째(최신) 버전
                } else {
                    "13.6.1" // fallback
                }
            } catch (e: Exception) {
                "13.6.1"
            }
        }
    }

    // Firestore에서 gameName과 tagLine으로 PUUID 가져오기 (uid 기반)
    private suspend fun getPuuidFromFirestore(
        gameName: String,
        tagLine: String,
        uid: String
    ): String? {
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val querySnapshot = firestore.collection("users")
                .document(uid)
                .collection("SearchNameList")
                .whereEqualTo("gameName", gameName)
                .whereEqualTo("tagLine", tagLine)
                .get()
                .await()
            if (!querySnapshot.isEmpty) {
                querySnapshot.documents[0].getString("puuid")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("SummonerViewModel", "Firestore에서 PUUID 가져오기 실패: ${e.message}")
            null
        }
    }

    // Firestore에서 소환사 정보 가져오기 (uid 및 puuid 기반)
    private suspend fun getSummonerFromFirestore(
        uid: String,
        puuid: String
    ): SummonerResponse? {
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection("users")
                .document(uid)
                .collection("SearchNameList")
                .document(puuid)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) return null

            val data = snapshot.data ?: return null
            val soloRankData = data["soloRank"] as? Map<String, Any>
            val flexRankData = data["flexRank"] as? Map<String, Any>

            val soloRank = soloRankData?.let {
                LeagueEntry(
                    queueType = it["queueType"] as? String ?: "",
                    tier = it["tier"] as? String ?: "",
                    rank = it["rank"] as? String ?: "",
                    leaguePoints = (it["leaguePoints"] as? Long)?.toInt() ?: 0,
                    wins = (it["wins"] as? Long)?.toInt() ?: 0,
                    losses = (it["losses"] as? Long)?.toInt() ?: 0
                )
            }
            val flexRank = flexRankData?.let {
                LeagueEntry(
                    queueType = it["queueType"] as? String ?: "",
                    tier = it["tier"] as? String ?: "",
                    rank = it["rank"] as? String ?: "",
                    leaguePoints = (it["leaguePoints"] as? Long)?.toInt() ?: 0,
                    wins = (it["wins"] as? Long)?.toInt() ?: 0,
                    losses = (it["losses"] as? Long)?.toInt() ?: 0
                )
            }
            SummonerResponse(
                puuid = data["puuid"] as String,
                summonerId = data["summonerId"] as? String ?: "",
                gameName = data["gameName"] as String,
                tagLine = data["tagLine"] as String,
                profileIconId = (data["profileIconId"] as Long).toInt(),
                summonerLevel = (data["summonerLevel"] as? Long)?.toInt() ?: 0,
                soloRank = soloRank,
                flexRank = flexRank
            )
        } catch (e: Exception) {
            Log.e("Firestore", "Firestore에서 소환사 정보 가져오기 실패: ${e.message}")
            null
        }
    }

    // 소환사 검색: 먼저 Firestore에서 사용자(uid)별 기록을 확인하고, 없으면 Riot API 호출
    fun searchSummoner(gameName: String, tagLine: String, uid: String) {
        viewModelScope.launch {
            val puuid = getPuuidFromFirestore(gameName, tagLine, uid)
            val firestoreResult = puuid?.let { getSummonerFromFirestore(uid, it) }
            if (firestoreResult != null) {
                Log.d("SummonerViewModel", "🔥 Firestore에서 소환사 정보 가져옴.")
                _summonerInfo.value = firestoreResult
            } else {
                Log.d("SummonerViewModel", "⚡ Firestore에 정보 없음, Riot API 호출")
                val result = repository.getSummonerInfo(gameName, tagLine, uid)
                result?.let { _summonerInfo.value = it }
            }
        }
    }

    // UI에서 사용할 아이콘 URL 생성
    suspend fun getSummonerIconUrl(profileIconId: Int): String {
        val latestVersion = getLatestLolVersion()
        return "https://ddragon.leagueoflegends.com/cdn/$latestVersion/img/profileicon/$profileIconId.png"
    }
}
