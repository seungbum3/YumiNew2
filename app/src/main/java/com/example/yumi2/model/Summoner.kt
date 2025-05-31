package com.example.yumi2.model

import com.google.gson.annotations.SerializedName

data class Summoner(
    @SerializedName("id") val summonerId: String,
    @SerializedName("puuid") val puuid: String,
    @SerializedName("name") val name: String,  // 변경: 변수명을 name으로 통일
    @SerializedName("profileIconId") val profileIconId: Int,
    @SerializedName("summonerLevel") val summonerLevel: Int
)

