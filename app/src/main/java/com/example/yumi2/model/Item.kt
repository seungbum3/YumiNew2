package com.example.yumi2.model

data class Item(
    val id: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val tags: List<String> = emptyList(),
    val cost: Int = 0,
    val stats: String = "",      // 능력치 정보
    val effect: String = "",     // 아이템 효과
    val description: String = "" // 아이템 설명
)
