package com.example.yumi2.util

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.IOException

object RuneImageManager {
    private const val RUNE_URL = "https://ddragon.leagueoflegends.com/cdn/15.9.1/data/ko_KR/runesReforged.json"
    private val client = OkHttpClient()

    val runeMap = mutableMapOf<Int, String>()

    fun loadRuneImages(onLoaded: () -> Unit = {}) {
        val request = Request.Builder().url(RUNE_URL).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RuneImageManager", "❌ 룬 이미지 로딩 실패: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                val listType = object : TypeToken<List<RuneReforged>>() {}.type
                val runeList: List<RuneReforged> = Gson().fromJson(json, listType)

                for (style in runeList) {
                    for (slot in style.slots) {
                        for (rune in slot.runes) {
                            runeMap[rune.id] = "https://ddragon.leagueoflegends.com/cdn/img/${rune.icon}"
                        }
                    }
                }
                onLoaded()
            }
        })
    }
}
