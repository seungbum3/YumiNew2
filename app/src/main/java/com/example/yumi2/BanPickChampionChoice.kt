package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.adapter.ChampionAdapter
import com.example.yumi2.model.ChampionData
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class BanPickChampionChoice : AppCompatActivity() {

    companion object {
        var selectedChampions = mutableSetOf<String>()
        var allChampions = listOf<ChampionData>()
    }

    private lateinit var championAdapter: ChampionAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var timerTextView: TextView

    private var currentRole: String? = null
    private var selectedChampion: ChampionData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("BanPickChoice", "onCreate 호출됨")

        setContentView(R.layout.banpick_champion_choice)
        Log.d("BanPickChoice", "레이아웃 설정 완료")

        try {
            // 닫기 버튼
            findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
                finish()
            }

            // 확정 버튼
            findViewById<Button>(R.id.btn_confirm).setOnClickListener {
                selectedChampion?.let { champ ->
                    if (selectedChampions.contains(champ.id)) {
                        Toast.makeText(this, "이미 선택된 챔피언입니다", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val resultIntent = Intent().apply {
                        putExtra("championId", champ.id)
                        putExtra("iconUrl", champ.iconUrl)
                        putExtra("splashUrl", champ.splashUrl)
                    }
                    setResult(RESULT_OK, resultIntent)

                    selectedChampions.add(champ.id)
                    championAdapter.submitList(allChampions)
                    finish()
                } ?: run {
                    Toast.makeText(this, "챔피언을 선택해주세요", Toast.LENGTH_SHORT).show()
                }
            }

            recyclerView = findViewById(R.id.rv_champion_list)
            recyclerView.layoutManager = GridLayoutManager(this, 5)

            championAdapter = ChampionAdapter { champion ->
                selectedChampion = champion
                Toast.makeText(this, "${champion.name} 선택됨", Toast.LENGTH_SHORT).show()
            }
            recyclerView.adapter = championAdapter
            Log.d("BanPickChoice", "리사이클러뷰 세팅 완료")

            searchEditText = findViewById(R.id.et_champion_search)
            searchEditText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    filterChampions(search = s.toString())
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            // 역할 버튼
            findViewById<Button>(R.id.btn_top).setOnClickListener {
                toggleRole("Top", listOf("Fighter", "Tank"))
            }
            findViewById<Button>(R.id.btn_jungle).setOnClickListener {
                toggleRole("Jungle", listOf("Assassin", "Fighter"))
            }
            findViewById<Button>(R.id.btn_mid).setOnClickListener {
                toggleRole("Mid", listOf("Mage", "Assassin"))
            }
            findViewById<Button>(R.id.btn_adc).setOnClickListener {
                toggleRole("Marksman", listOf("Marksman"))
            }
            findViewById<Button>(R.id.btn_support).setOnClickListener {
                toggleRole("Support", listOf("Support"))
            }

            loadChampionList()
        } catch (e: Exception) {
            Log.e("BanPickChoice", "onCreate에서 예외 발생: ${e.message}", e)
        }
    }

    private fun loadChampionList() {
        Log.d("BanPickChoice", "챔피언 리스트 불러오는 중...")

        val db = Firebase.firestore
        db.collection("champion_choice")
            .get()
            .addOnSuccessListener { snapshot ->
                val list = mutableListOf<ChampionData>()
                for (doc in snapshot) {
                    val id = doc.id
                    val name = doc.getString("name") ?: ""
                    val iconUrl = doc.getString("iconUrl") ?: ""
                    val splashUrl = doc.getString("splashUrl") ?: ""
                    val loadingUrl = doc.getString("loadingUrl") ?: ""
                    val title = doc.getString("title") ?: ""
                    val tags = doc.get("tags") as? List<String> ?: emptyList()

                    list.add(
                        ChampionData(
                            id = id,
                            name = name,
                            iconUrl = iconUrl,
                            splashUrl = splashUrl,
                            loadingUrl = loadingUrl,
                            title = title,
                            tags = tags
                        )
                    )
                }

                allChampions = list
                championAdapter.submitList(allChampions)
                Log.d("BanPickChoice", "챔피언 ${list.size}개 로딩 완료")
            }
            .addOnFailureListener {
                Log.e("BanPickChoice", "챔피언 로딩 실패: ${it.message}", it)
            }
    }

    private fun toggleRole(roleName: String, filterTags: List<String>) {
        currentRole = if (currentRole == roleName) null else roleName
        filterChampions()
    }

    private fun filterChampions(search: String = searchEditText.text.toString()) {
        val trimmedSearch = search.trim().lowercase()

        val filtered = allChampions.filter { champ ->
            val matchesSearch = champ.name.lowercase().contains(trimmedSearch)
            val matchesRole = currentRole?.let { role ->
                when (role) {
                    "Top" -> champ.tags.any { it in listOf("Fighter", "Tank") }
                    "Jungle" -> champ.tags.any { it in listOf("Assassin", "Fighter") }
                    "Mid" -> champ.tags.any { it in listOf("Mage", "Assassin") }
                    else -> champ.tags.contains(role)
                }
            } ?: true

            matchesSearch && matchesRole
        }

        championAdapter.submitList(filtered)
        Log.d("BanPickChoice", "검색 결과: ${filtered.size}개")
    }
}
