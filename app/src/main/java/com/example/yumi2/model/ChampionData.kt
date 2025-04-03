package com.example.yumi2.model

data class ChampionData(
    val id: String = "",
    val name: String = "",
    val tags: List<String> = emptyList(),
    val iconUrl: String = "",     // ✅ 작은 아이콘 이미지 (금지 챔피언 하단에 사용)
    val splashUrl: String = "",    // ✅ 스플래시 아트 (선택 화면)
    val loadingUrl: String = "",   // 🔸 로딩 화면용 이미지 (선택)
    val title: String = ""
)
