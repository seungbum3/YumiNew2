package com.example.yumi2

import android.app.Application
import android.util.Log
import com.example.yumi2.util.RuneImageManager

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("MyApplication", "앱 시작됨 - 룬 이미지 로딩 시작")

        RuneImageManager.loadRuneImages {
            Log.d("MyApplication", "✅ 룬 이미지 로딩 완료! 총 개수: ${RuneImageManager.runeMap.size}")
        }
    }
}
