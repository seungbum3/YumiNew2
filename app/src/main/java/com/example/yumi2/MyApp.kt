package com.example.yumi2

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val auth = FirebaseAuth.getInstance()
        // 앱 시작할 때 한 번 적용
        auth.currentUser?.let { applyUserTheme(it.uid) }
        // 로그인/로그아웃 시에도 계속 감지
        auth.addAuthStateListener { firebaseAuth ->
            firebaseAuth.currentUser?.let { applyUserTheme(it.uid) }
                ?: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun applyUserTheme(uid: String) {
        val prefs = getSharedPreferences("settings_$uid", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}

