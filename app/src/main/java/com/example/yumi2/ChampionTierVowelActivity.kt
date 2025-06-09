package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.adapter.TierChampionAdapter
import com.example.yumi2.model.TierChampion
import com.google.firebase.firestore.FirebaseFirestore
import android.graphics.Color
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.Source

class ChampionTierVowelActivity : AppCompatActivity() {
    private var currentRoleTags: List<String> = listOf("Top")
    private lateinit var adapter: TierChampionAdapter

    // 정렬 타입/방향 관리
    enum class SortType { COMBINED, WIN, PICK, BAN }
    enum class SortDirection { DESC, ASC, DEFAULT }
    private var currentSortType: SortType = SortType.COMBINED
    private var currentSortDirection: SortDirection = SortDirection.DESC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.champion_tier_vowel)

        val backButton = findViewById<Button>(R.id.PageBack)
        backButton.background.setTint(ContextCompat.getColor(this, R.color.black))
        backButton.setOnClickListener {
            val intent = Intent(this, MainpageActivity::class.java)
            startActivity(intent)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.championTierRecyclerView)
        adapter = TierChampionAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 역할 버튼 클릭 시 태그/필터 변경
        findViewById<Button>(R.id.btn_top).setOnClickListener {
            highlightSelectedButton(it as MaterialButton)
            currentRoleTags = listOf("Top")
            filterByTags(currentRoleTags)
        }
        findViewById<Button>(R.id.btn_jungle).setOnClickListener {
            highlightSelectedButton(it as MaterialButton)
            currentRoleTags = listOf("Jungle")
            filterByTags(currentRoleTags)
        }
        findViewById<Button>(R.id.btn_mid).setOnClickListener {
            highlightSelectedButton(it as MaterialButton)
            currentRoleTags = listOf("Mid")
            filterByTags(currentRoleTags)
        }
        findViewById<Button>(R.id.btn_adc).setOnClickListener {
            highlightSelectedButton(it as MaterialButton)
            currentRoleTags = listOf("ADC")
            filterByTags(currentRoleTags)
        }
        findViewById<Button>(R.id.btn_support).setOnClickListener {
            highlightSelectedButton(it as MaterialButton)
            currentRoleTags = listOf("Sup")
            filterByTags(currentRoleTags)
        }

        // 헤더 클릭(정렬)
        findViewById<TextView>(R.id.header_win).setOnClickListener {
            onSortHeaderClicked(SortType.WIN)
        }
        findViewById<TextView>(R.id.header_pick).setOnClickListener {
            onSortHeaderClicked(SortType.PICK)
        }
        findViewById<TextView>(R.id.header_ban).setOnClickListener {
            onSortHeaderClicked(SortType.BAN)
        }

        // 기본값: 탑 역할 자동 선택
        findViewById<Button>(R.id.btn_top).performClick()
    }

    // 헤더(정렬 기준) 클릭시 토글
    private fun onSortHeaderClicked(type: SortType) {
        if (currentSortType == type) {
            // 같은 헤더 연속 클릭: DESC → ASC → DEFAULT → DESC...
            currentSortDirection = when (currentSortDirection) {
                SortDirection.DESC -> SortDirection.ASC
                SortDirection.ASC -> SortDirection.DEFAULT
                SortDirection.DEFAULT -> SortDirection.DESC
            }
            // 기본으로 돌아가면 종합점수로 바꿈
            if (currentSortDirection == SortDirection.DEFAULT) {
                currentSortType = SortType.COMBINED
            }
        } else {
            // 다른 헤더 클릭시 DESC로 시작
            currentSortType = type
            currentSortDirection = SortDirection.DESC
        }
        filterByTags(currentRoleTags)
    }

    private fun filterByTags(roleTags: List<String>) {
        val db = FirebaseFirestore.getInstance()
        db.collection("champion_choice")
            .get(Source.SERVER)
            .addOnSuccessListener { result ->
                val filteredList = result.mapNotNull { doc ->
                    val tags = (doc.get("tags") as? List<*>)?.mapNotNull { it as? String }
                    if (tags != null && tags.any { it in roleTags }) {
                        val name = doc.getString("name") ?: ""
                        val iconUrl = doc.getString("iconUrl") ?: ""
                        val pickRate = doc.getDouble("pickRate")?.toString() ?: "-"
                        val banRate = doc.getDouble("banRate")?.toString() ?: "-"
                        val winRate = doc.getDouble("winRate")?.toString() ?: "-"
                        TierChampion(name, iconUrl, winRate, pickRate, banRate)
                    } else null
                }
                val sortedList = sortChampionList(filteredList, currentSortType, currentSortDirection)
                adapter.setItems(sortedList)
            }
            .addOnFailureListener {
                Log.e("ChampionTier", "챔피언 필터링 실패: ${it.message}")
            }
    }

    private fun highlightSelectedButton(selectedButton: MaterialButton) {
        val buttons = listOf(
            findViewById<MaterialButton>(R.id.btn_top),
            findViewById<MaterialButton>(R.id.btn_jungle),
            findViewById<MaterialButton>(R.id.btn_mid),
            findViewById<MaterialButton>(R.id.btn_adc),
            findViewById<MaterialButton>(R.id.btn_support)
        )
        for (btn in buttons) {
            btn.setTextColor(Color.parseColor("#9E9E9E"))
        }
        selectedButton.setTextColor(Color.BLACK)
    }

    private fun sortChampionList(
        list: List<TierChampion>,
        sortType: SortType,
        sortDirection: SortDirection
    ): List<TierChampion> {
        // 1. 먼저 기준별 정렬
        val sorted = when (sortType) {
            SortType.COMBINED -> list.sortedByDescending {
                (it.winRate.toFloatOrNull() ?: 0f) * 0.6f +
                        (it.pickRate.toFloatOrNull() ?: 0f) * 0.3f +
                        (it.banRate.toFloatOrNull() ?: 0f) * 0.1f
            }
            SortType.WIN -> list.sortedByDescending { it.winRate.toFloatOrNull() ?: 0f }
            SortType.PICK -> list.sortedByDescending { it.pickRate.toFloatOrNull() ?: 0f }
            SortType.BAN -> list.sortedByDescending { it.banRate.toFloatOrNull() ?: 0f }
        }
        // 2. 방향에 따라 뒤집기 or 기본정렬(종합점수)
        return when (sortDirection) {
            SortDirection.DESC -> sorted
            SortDirection.ASC -> sorted.reversed()
            SortDirection.DEFAULT -> sortChampionList(list, SortType.COMBINED, SortDirection.DESC)
        }
    }
}
