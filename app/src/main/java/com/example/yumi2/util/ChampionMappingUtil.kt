package com.example.yumi2.util

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object ChampionMappingUtil {

    // 기본 LOL 버전 fallback 값 (데이터 가져오기에 실패할 경우 사용)
    const val DEFAULT_LOL_VERSION = "14.1.1"

    // Data Dragon의 챔피언 JSON 파일 구조에 맞춘 DTO들
    data class ChampionList(
        val type: String,
        val format: String,
        val version: String,
        val data: Map<String, ChampionData>
    )

    data class ChampionData(
        val id: String,   // 예: "Aatrox"
        val key: String,  // 예: "266" (문자열이지만, 숫자로 변환됩니다)
        val name: String  // 예: "오리아나" (ko_KR 기준)
    )

    data class ChampionInfo(
        val engId: String,   // 예: "Orianna"
        val korName: String  // 예: "오리아나"
    )

    /**
     * Data Dragon의 최신 버전 문자열을 가져옵니다.
     * https://ddragon.leagueoflegends.com/api/versions.json 의 첫 번째 요소가 최신 버전입니다.
     */
    suspend fun getLatestDataDragonVersion(): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://ddragon.leagueoflegends.com/api/versions.json")
            (url.openConnection() as? HttpURLConnection)?.run {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                connect()
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val result = inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(result)
                    if (jsonArray.length() > 0) {
                        jsonArray.getString(0)
                    } else {
                        DEFAULT_LOL_VERSION
                    }
                } else {
                    DEFAULT_LOL_VERSION
                }
            } ?: DEFAULT_LOL_VERSION
        } catch (e: Exception) {
            e.printStackTrace()
            DEFAULT_LOL_VERSION
        }
    }

    /**
     * 최신 Data Dragon 버전을 사용하여 챔피언 매핑 정보를 가져옵니다.
     */
    suspend fun fetchLatestChampionMapping(): Map<Int, ChampionInfo> {
        val latestVersion = getLatestDataDragonVersion()
        return fetchChampionMapping(latestVersion)
    }

    /**
     * 지정된 버전의 Data Dragon을 사용하여 챔피언 매핑 정보를 가져옵니다.
     */
    suspend fun fetchChampionMapping(version: String): Map<Int, ChampionInfo> = withContext(Dispatchers.IO) {
        val url = "https://ddragon.leagueoflegends.com/cdn/$version/data/ko_KR/champion.json"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val championList = Gson().fromJson(responseText, ChampionList::class.java)
                // key(숫자) -> ChampionInfo(engId, korName) 매핑
                championList.data.values.associateBy(
                    keySelector = { it.key.toIntOrNull() ?: -1 },
                    valueTransform = { champData ->
                        ChampionInfo(
                            engId = champData.id,
                            korName = champData.name
                        )
                    }
                ).filterKeys { it != -1 }
            } else {
                emptyMap()
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 챔피언 ID를 입력받아 해당 챔피언의 이름(ko_KR)을 반환합니다.
     * 캐싱 없이 매핑 정보를 새로 받아옵니다.
     * 실제 사용 시에는 캐싱을 고려하세요.
     */
    suspend fun championIdToName(champId: Int, version: String = DEFAULT_LOL_VERSION): String {
        val mapping = fetchChampionMapping(version)
        return mapping[champId]?.korName ?: "Unknown Champion"
    }
}
