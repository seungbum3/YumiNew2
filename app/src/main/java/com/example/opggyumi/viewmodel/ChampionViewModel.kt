package com.example.opggyumi.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.opggyumi.api.ChampionData
import com.google.firebase.firestore.FirebaseFirestore

class ChampionViewModel : ViewModel() {
    private val _championList = MutableLiveData<List<ChampionData>>() // 🔹 데이터 타입 변경
    val championList: LiveData<List<ChampionData>> get() = _championList

    private val db = FirebaseFirestore.getInstance()

    fun fetchChampionRotations() {
        val currentDate = "2025-02-11" // 현재 날짜 (테스트용)
        db.collection("champion_rotation").document(currentDate)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val champions = document["champion_list"] as? List<HashMap<String, Any>>
                    val championList = champions?.map { champ ->
                        ChampionData(
                            (champ["id"] as String).toInt(),  // 🔹 String을 Int로 변환
                            champ["name"] as String,
                            champ["imageUrl"] as String
                        )
                    } ?: emptyList()

                    // 🔹 로그 추가
                    Log.d("Firestore", "가져온 챔피언 데이터: $championList")

                    _championList.postValue(championList) // 🔹 LiveData 업데이트
                } else {
                    Log.e("Firestore", "문서가 존재하지 않음.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Firestore 데이터 가져오기 실패: ${e.message}")
            }
    }
}