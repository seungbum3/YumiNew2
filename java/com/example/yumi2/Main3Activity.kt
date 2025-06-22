package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.yumi2.comment.MainActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class Main3Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main3)

        findViewById<CardView>(R.id.btnBanPick).setOnClickListener {
            // 나만의 즐겨찾기 아이템 페이지로 이동
            startActivity(Intent(this, BanPickMain::class.java))
        }
        findViewById<CardView>(R.id.btnMyItem).setOnClickListener {
            // 나만의 즐겨찾기 아이템 페이지로 이동
            startActivity(Intent(this, ItemSelectionActivity::class.java))
        }
        findViewById<CardView>(R.id.btnChampCalc).setOnClickListener {
            // 챔피언 수치 계산 페이지로 이동
            startActivity(Intent(this, ChampcalActivity::class.java))
        }
        findViewById<CardView>(R.id.btnThird).setOnClickListener {
            // 챔피언 비교 해보기 페이지로 이동
            startActivity(Intent(this, ChampionCompareActivity::class.java))
        }
        findViewById<CardView>(R.id.btnFourth).setOnClickListener {
            // 챔피언 비교 해보기 페이지로 이동
            startActivity(Intent(this, BanpickFavorite::class.java))
        }

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigationView.selectedItemId = R.id.category3
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.category1 -> {
                    startActivity(Intent(this, MainpageActivity::class.java))
                    finish()
                    true
                }
                R.id.category2 -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.category3 -> true
                R.id.category4 -> {
                    startActivity(Intent(this, MyPageActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}