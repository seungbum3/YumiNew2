package com.example.yumi2.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.yumi2.model.ChampionData
import com.google.firebase.firestore.FirebaseFirestore

class ChampionViewModel : ViewModel() {
    private val _championList = MutableLiveData<List<ChampionData>>() // 🔹 LiveData
    val championList: LiveData<List<ChampionData>> get() = _championList

    private val db = FirebaseFirestore.getInstance()

    fun fetchChampionRotations() {
        db.collection("champion_rotation").document("latest") // ← 고정 문서로 변경
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val champions = document["champion_list"] as? List<HashMap<String, Any>>
                    val championList = champions?.map { champ ->
                        ChampionData(
                            id = champ["id"]?.toString() ?: "",
                            name = champ["name"] as? String ?: "",
                            tags = champ["tags"] as? List<String> ?: emptyList(),
                            iconUrl = champ["imageUrl"] as? String ?: "", // key는 Firestore에 맞게
                            title = champ["title"] as? String ?: ""
                        )
                    } ?: emptyList()

                    Log.d("Firestore", "✅ 가져온 챔피언 로테이션: $championList")
                    _championList.postValue(championList)
                } else {
                    Log.e("Firestore", "❌ 로테이션 문서가 존재하지 않음.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "❌ Firestore 데이터 가져오기 실패: ${e.message}")
            }
    }
}
