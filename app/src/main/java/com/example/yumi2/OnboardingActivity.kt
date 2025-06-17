package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class OnboardingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        // 온보딩에 사용할 이미지와 설명
        val pages = listOf(
            OnboardingPage(R.drawable.yumi_icon
                , "LOL 전적 검색을 간편하게!"),
            OnboardingPage(R.drawable.yumi_icon, "친구와 함께 기록 공유하기"),
            OnboardingPage(R.drawable.yumi_icon, "나만의 아이템 추천까지!")
        )

        viewPager.adapter = OnboardingPagerAdapter(pages)

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        findViewById<TextView>(R.id.tvSignUp).setOnClickListener {
            startActivity(Intent(this, JoinActivity::class.java))
        }
    }
}
