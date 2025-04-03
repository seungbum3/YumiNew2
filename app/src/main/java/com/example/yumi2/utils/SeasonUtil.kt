package com.example.yumi2.util

fun extractSeason(gameVersion: String): Int? {
    // 게임 버전이 "13.5.1" 형식이라면, 앞의 숫자를 시즌 번호로 사용
    return gameVersion.split(".").firstOrNull()?.toIntOrNull()
}
