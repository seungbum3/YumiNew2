package com.example.yumi2.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yumi2.model.ChampionStats
import com.example.yumi2.model.LeagueEntry
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

    private val _summonerInfo = MutableStateFlow<SummonerResponse?>(null)
    val summonerInfo: StateFlow<SummonerResponse?> = _summonerInfo

    private val _championStats = MutableStateFlow<List<ChampionStats>>(emptyList())
    val championStats: StateFlow<List<ChampionStats>> = _championStats

    fun loadChampionStats(puuid: String) {
        viewModelScope.launch {
            val stats = repository.getChampionStats(puuid)
            _championStats.value = stats
        }
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
                        versions.getString(0)
                    } else {
                        "14.1.1"
                    }
                } catch (e: Exception) {
                    "14.1.1"
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
