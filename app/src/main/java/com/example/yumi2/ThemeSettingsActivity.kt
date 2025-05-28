package com.example.yumi2

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.auth.FirebaseAuth

class ThemeSettingsActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme_settings)

        // ★ UID 기반 prefs 파일 열기
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        prefs = getSharedPreferences("settings_$uid", MODE_PRIVATE)

        val switchDark = findViewById<SwitchCompat>(R.id.switchDarkMode)

        // ① 저장된 설정 불러오기
        val isDark = prefs.getBoolean("dark_mode", false)
        switchDark.isChecked = isDark
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // ② 스위치 토글 시 즉시 적용 & 저장
        switchDark.setOnCheckedChangeListener { _, checked ->
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            prefs.edit().putBoolean("dark_mode", checked).apply()
        }
    }
}
