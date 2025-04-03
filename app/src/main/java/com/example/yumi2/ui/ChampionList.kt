package com.example.yumi2.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.R
import com.example.yumi2.adapter.ChampionAdapter
import com.example.yumi2.model.ChampionData
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ChampionList : AppCompatActivity() {

    private lateinit var championRecyclerView: RecyclerView
    private lateinit var championAdapter: ChampionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.champion_list)

        // RecyclerView 초기화
        championRecyclerView = findViewById(R.id.recyclerView)
        championRecyclerView.layoutManager = GridLayoutManager(this, 5)

        // 어댑터 생성 (챔피언 클릭 시 Toast)
        championAdapter = ChampionAdapter { champion ->
            Toast.makeText(this, "${champion.name} 선택됨!", Toast.LENGTH_SHORT).show()
        }
        championRecyclerView.adapter = championAdapter

        // 🔥 Firestore에서 챔피언 불러오기
        loadChampionsFromFirestore()
    }

    // 🔹 Firestore에서 챔피언 리스트 가져오는 함수
    private fun loadChampionsFromFirestore() {
        val db = Firebase.firestore

        Log.d("Firestore", "📦 Firestore 요청 시작")

        db.collection("champion_choice")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val championList = mutableListOf<ChampionData>()
                Log.d("Firestore", "✅ Firestore 응답 수: ${querySnapshot.size()}")

                for (doc in querySnapshot) {
                    Log.d("Firestore", "📄 Document: ${doc.id} → ${doc.data}")

                    val id = doc.id
                    val name = doc.getString("name") ?: "이름없음"
                    val iconUrl = doc.getString("iconUrl") ?: ""
                    val tags = doc.get("tags") as? List<String> ?: emptyList()
                    val title = doc.getString("title") ?: ""

                    val champion = ChampionData(
                        id = id,
                        name = name,
                        tags = tags,
                        iconUrl = iconUrl,
                        title = title
                    )

                    Log.d("Mapping", "🧩 챔피언 매핑됨: $champion")

                    championList.add(champion)
                }

                Log.d("Adapter", "📤 어댑터에 전달할 리스트 수: ${championList.size}")
                championAdapter.submitList(championList)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "❌ Firestore 실패: ${e.message}")
                Toast.makeText(this, "불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
