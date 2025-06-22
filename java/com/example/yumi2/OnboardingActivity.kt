package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.onboardingViewPager)
        tabLayout = findViewById(R.id.tabLayout)
        startButton = findViewById(R.id.startButton)

        val items = listOf(
            OnboardingItem(R.drawable.onboarding_1, "소환사 검색", "닉네임으로 전적을 쉽게 확인하세요."),
            OnboardingItem(R.drawable.onboarding_2, "모의 밴픽", "실전처럼 밴픽을 연습해보세요."),
            OnboardingItem(R.drawable.onboarding_4, "커뮤니티", "유저들과 전략을 나눠보세요."),
            OnboardingItem(R.drawable.onboarding_3, "능력치 계산기", "레벨과 아이템에 따른 스탯을 확인하세요.")
        )

        viewPager.adapter = OnboardingAdapter(items)

        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                startButton.visibility = if (position == items.lastIndex) View.VISIBLE else View.GONE
            }
        })

        startButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}

