package com.example.yumi2.model

import com.google.gson.annotations.SerializedName

data class Summoner(
    @SerializedName("id") val summonerId: String, // ✅ `id`를 `summonerId`로 매핑
    @SerializedName("puuid") val puuid: String,
    @SerializedName("name") val gameName: String,
    @SerializedName("profileIconId") val profileIconId: Int,
    @SerializedName("summonerLevel") val summonerLevel: Int
)
