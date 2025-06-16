package com.example.yumi2.model

import com.google.gson.annotations.SerializedName

data class Participant(
    @SerializedName("summonerName")
    val summonerName: String,

    @SerializedName("puuid")
    val puuid: String,

    @SerializedName("championId")
    val championId: Int,

    @SerializedName("kills")
    val kills: Int,

    @SerializedName("deaths")
    val deaths: Int,

    @SerializedName("assists")
    val assists: Int,

    @SerializedName("teamId")
    val teamId: Int,

    @SerializedName("win")
    val win: Boolean,

    @SerializedName("summoner1Id")
    val summoner1Id: Int,

    @SerializedName("summoner2Id")
    val summoner2Id: Int,

    @SerializedName("item0")
    val item0: Int,

    @SerializedName("item1")
    val item1: Int,

    @SerializedName("item2")
    val item2: Int,

    @SerializedName("item3")
    val item3: Int,

    @SerializedName("item4")
    val item4: Int,

    @SerializedName("item5")
    val item5: Int,

    @SerializedName("item6")
    val item6: Int,

    @SerializedName("totalMinionsKilled")
    val totalMinionsKilled: Int,

    @SerializedName("neutralMinionsKilled")
    val neutralMinionsKilled: Int,

    @SerializedName("goldEarned")
    val goldEarned: Int
)
