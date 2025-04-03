package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.yumi2.ItemSelectionActivity
import com.example.yumi2.MyPageActivity
import com.example.yumi2.LoginActivity
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val settingsContainer = findViewById<LinearLayout>(R.id.settingsContainer)

        val settingTexts = listOf(
            "로그아웃",
            "친구목록",
            "나만의 아이템 즐겨찾기",
            "게시글 임시저장",
            "알림 설정",
            "테마 설정",
            "이용약관",
            "개인정보 처리방침"
        )

        for (i in settingTexts.indices) {
            val itemView = layoutInflater.inflate(R.layout.item_setting, settingsContainer, false)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dpToPx(6)
            itemView.layoutParams = params

            val itemText = itemView.findViewById<TextView>(R.id.itemText)
            itemText.text = settingTexts[i]

            when (settingTexts[i]) {
                "로그아웃" -> {
                    itemView.setOnClickListener {
                        // Firebase 로그아웃
                        FirebaseAuth.getInstance().signOut()
                        // SharedPreferences에 저장된 로그인 정보 삭제
                        val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                        sharedPref.edit().remove("loggedInUserId").apply()
                        // LoginActivity로 전환 (백스택 모두 제거)
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish()
                    }
                }
                "나만의 아이템 즐겨찾기" -> {
                    itemView.setOnClickListener {
                        val intent = Intent(this, ItemSelectionActivity::class.java)
                        startActivity(intent)
                    }
                }
                // 필요한 경우 다른 항목에 대해서도 처리
            }

            settingsContainer.addView(itemView)
        }
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            val intent = Intent(this, MyPageActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
