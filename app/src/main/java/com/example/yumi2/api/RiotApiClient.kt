package com.example.yumi2.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RiotApiClient {
    val api: RiotApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://asia.api.riotgames.com/")  // ← 지역에 따라 수정
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RiotApiService::class.java)
    }
}
