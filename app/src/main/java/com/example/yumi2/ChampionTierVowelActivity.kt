package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.adapter.TierChampionAdapter
import com.example.yumi2.model.TierChampion
import com.google.firebase.firestore.FirebaseFirestore
import android.graphics.Color
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class ChampionTierVowelActivity : AppCompatActivity() {

    private lateinit var adapter: TierChampionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.champion_tier_vowel)

        val PageBack: Button = findViewById(R.id.PageBack)

        val backButton = findViewById<Button>(R.id.PageBack)
        backButton.background.setTint(ContextCompat.getColor(this, R.color.black))

        PageBack.setOnClickListener {
            val intent = Intent(this, MainpageActivity::class.java)
            startActivity(intent)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.championTierRecyclerView)
        adapter = TierChampionAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 역할 버튼 클릭 시 필터링
        findViewById<Button>(R.id.btn_top).setOnClickListener {
            highlightSelectedButton(it as MaterialButton)
            filterByTags(listOf("Top"))
        }
        findViewById<Button>(R.id.btn_jungle).setOnClickListener {
            highlightSelectedButton(it as MaterialButton)
            filterByTags(listOf("Jungle"))
        }
        findViewById<Button>(R.id.btn_mid).setOnClickListener {
            highlightSelectedButton(it as MaterialButton)
            filterByTags(listOf("Mid"))
        }
        findViewById<Button>(R.id.btn_adc).setOnClickListener {
            highlightSelectedButton(it as MaterialButton)
            filterByTags(listOf("ADC"))
        }
        findViewById<Button>(R.id.btn_support).setOnClickListener {
            highlightSelectedButton(it as MaterialButton)
            filterByTags(listOf("Sup"))
        }

        // 기본값: 탑 역할 표시
        val btnTop = findViewById<Button>(R.id.btn_top)
        btnTop.performClick()
    }

    private fun filterByTags(roleTags: List<String>) {
        FirebaseFirestore.getInstance()
            .collection("champion_choice")
            .get()
            .addOnSuccessListener { result ->
                val filteredList = result.mapNotNull { doc ->
                    val tags = doc.get("tags") as? List<*>
                    if (tags != null && tags.any { it in roleTags }) {
                        val name = doc.getString("name") ?: ""
                        val iconUrl = doc.getString("iconUrl") ?: ""
                        TierChampion(name, iconUrl, "", "", "")
                    } else null
                }

                Log.d("Firestore", "필터링된 챔피언 수: ${filteredList.size}")
                adapter.setItems(filteredList)
            }
            .addOnFailureListener {
                Log.e("Firestore", "챔피언 필터링 실패: ${it.message}")
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
            btn.setTextColor(Color.parseColor("#9E9E9E")) // 비활성 회색
        }

        selectedButton.setTextColor(Color.BLACK) // 선택된 버튼만 검정색
    }

}
