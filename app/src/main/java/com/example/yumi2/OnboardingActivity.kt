package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var startButton: Button
    private lateinit var dotLayout: LinearLayout
    private lateinit var dots: Array<ImageView>
    private lateinit var items: List<OnboardingItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        dotLayout = findViewById(R.id.dotLayout)
        viewPager = findViewById(R.id.onboardingViewPager)
        startButton = findViewById(R.id.startButton)

        items = listOf(
            OnboardingItem(R.drawable.onboarding_1, "소환사 검색", "닉네임으로 전적을 쉽게 확인하세요."),
            OnboardingItem(R.drawable.onboarding_2, "모의 밴픽", "실전처럼 밴픽을 연습해보세요."),
            OnboardingItem(R.drawable.onboarding_4, "커뮤니티", "유저들과 전략을 나눠보세요."),
            OnboardingItem(R.drawable.onboarding_3, "능력치 계산기", "레벨과 아이템에 따른 스탯을 확인하세요.")
        )

        viewPager.adapter = OnboardingAdapter(items)

        // TabLayoutMediator 및 tabLayout 관련 코드 완전 제거

        addDotsIndicator(0)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                addDotsIndicator(position)
                startButton.visibility = if (position == items.lastIndex) View.VISIBLE else View.GONE
            }
        })

        startButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun addDotsIndicator(position: Int) {
        val dotsCount = items.size
        dotLayout.removeAllViews()
        dots = Array(dotsCount) { ImageView(this) }
        for (i in 0 until dotsCount) {
            dots[i] = ImageView(this)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(8, 0, 8, 0) // dot 간격
            dots[i].layoutParams = params
            dots[i].setImageResource(if (i == position) R.drawable.tab_dot_selected else R.drawable.tab_dot_unselected)
            dotLayout.addView(dots[i])
        }
    }
}

