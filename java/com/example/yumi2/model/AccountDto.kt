package com.example.yumi2.model

data class AccountDto(
    val puuid: String,
    val gameName: String,
    val tagLine: String
)

// 🔽 이거 아래에 추가해 주세요
fun AccountDto.toResponse(): AccountResponse {
    return AccountResponse(
        puuid = this.puuid,
        gameName = this.gameName,
        tagLine = this.tagLine
    )
}
