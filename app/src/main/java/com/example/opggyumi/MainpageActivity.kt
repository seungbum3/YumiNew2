package com.example.opggyumi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.opggyumi.adapter.ChampionAdapter
import com.example.opggyumi.comment.MainActivity
import com.example.opggyumi.viewmodel.ChampionViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainpageActivity : AppCompatActivity() {
    private val viewModel: ChampionViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mainpage)


        val noticeButton: Button = findViewById(R.id.notice)

        noticeButton.setOnClickListener {
            val url =
                "https://www.leagueoflegends.com/ko-kr/news/game-updates/patch-2025-s1-3-notes/"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        val NameSearch: Button = findViewById(R.id.NameSearch)
        NameSearch.setOnClickListener {
            val intent = Intent(this, NameSearchActivity::class.java)
            startActivity(intent)
        }

        recyclerView = findViewById(R.id.championRecyclerView)
        // 🔹 2줄로 가로로 배치 + 스크롤 방향을 가로로 설정
        recyclerView.layoutManager = GridLayoutManager(this, 2, GridLayoutManager.HORIZONTAL, false)

        viewModel.championList.observe(this) { championList ->
            Log.d("RecyclerView", "챔피언 리스트 업데이트됨: $championList") // 🔹 로그 추가

            recyclerView.adapter = ChampionAdapter(championList) // 🔹 올바른 데이터 전달
        }

        viewModel.fetchChampionRotations()

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigationView.selectedItemId = R.id.category1  // '홈'을 기본 선택

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.category1 -> true  // 현재 페이지이므로 이동하지 않음
                R.id.category2 -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }

                R.id.category3 -> {
                    finish()
                    true
                }

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