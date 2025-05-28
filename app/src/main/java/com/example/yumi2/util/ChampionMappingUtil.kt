package com.example.yumi2.util

import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL

object ChampionMappingUtil {

    // 기본 LOL 버전 (추후 최신 버전으로 업데이트 필요)
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
        val key: String,  // 예: "266" (문자열이지만 실제로는 숫자로 변환)
        val name: String  // 예: "오리아나" (ko_KR 기준)
    )

    data class ChampionInfo(
        val engId: String,   // "Orianna"
        val korName: String  // "오리아나"
    )

    // 버전을 받아 해당 버전의 챔피언 매핑을 반환하는 suspend 함수
    // 실제로는 한 번 받아온 결과를 메모리에 캐싱하는 것이 좋습니다.
    suspend fun fetchChampionMapping(version: String): Map<Int, ChampionInfo> {
        val url = "https://ddragon.leagueoflegends.com/cdn/$version/data/ko_KR/champion.json"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        return try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val championList = Gson().fromJson(responseText, ChampionList::class.java)
                // key(숫자) -> ChampionInfo(engId, korName) 매핑
                championList.data.values.associateBy(
                    keySelector = { it.key.toIntOrNull() ?: -1 },
                    valueTransform = { champData ->
                        ChampionInfo(
                            engId = champData.id,   // 예: "Orianna"
                            korName = champData.name // 예: "오리아나"
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

    // 간편 조회 함수: 캐싱 없이 매핑을 새로 받아서 조회 (실제 사용 시 캐싱 필요)
    // 기본 버전 값을 제공하여 두 번째 인자를 생략할 수 있습니다.
    suspend fun championIdToName(champId: Int, version: String = DEFAULT_LOL_VERSION): String {
        val mapping = fetchChampionMapping(version)
        return mapping[champId]?.korName ?: "Unknown Champion"
    }
}
