package com.example.yumi2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import com.google.firebase.firestore.SetOptions


class FavoritesAdapter(
    private var favoriteList: MutableList<HashMap<String, String>>, // 🔹 변경 가능한 리스트
    private val userId: String
) : RecyclerView.Adapter<FavoritesAdapter.FavoriteViewHolder>() {

    // 🔹 즐겨찾기 상태를 저장할 Map (소환사명 → 현재 상태)
    private val favoriteStatus = mutableMapOf<String, Boolean>()

    class FavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val favoriteIcon: ImageView = itemView.findViewById(R.id.favoriteIcon) // 별표 아이콘
        val favoriteName: TextView = itemView.findViewById(R.id.favoriteName) // 닉네임
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_favorite, parent, false)
        return FavoriteViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        val favorite = favoriteList[position]
        val summonerName = favorite["summonerName"] ?: "알 수 없음"

        holder.favoriteName.text = summonerName

        // 🔹 Firestore에서 가져온 즐겨찾기 상태를 초기화
        if (!favoriteStatus.containsKey(summonerName)) {
            favoriteStatus[summonerName] = true // 기본적으로 즐겨찾기 활성화
        }

        // 🔹 현재 상태에 따라 UI 변경
        updateFavoriteIcon(holder.favoriteIcon, favoriteStatus[summonerName] ?: true)

        // 🔹 별표 클릭 이벤트 (UI 상태만 변경)
        holder.favoriteIcon.setOnClickListener {
            val isFavorite = favoriteStatus[summonerName] ?: true
            favoriteStatus[summonerName] = !isFavorite // 상태 반전

            // UI 업데이트
            updateFavoriteIcon(holder.favoriteIcon, !isFavorite)
        }
    }

    override fun getItemCount(): Int = favoriteList.size


    fun updateFavorites(newList: MutableList<HashMap<String, String>>) {
        favoriteList.clear()
        favoriteList.addAll(newList)
        notifyDataSetChanged()
    }
    // 🔹 별표 아이콘 변경 함수
    private fun updateFavoriteIcon(icon: ImageView, isFavorite: Boolean) {
        if (isFavorite) {
            icon.setImageResource(R.drawable.ic_star) // ⭐ 즐겨찾기 추가
        } else {
            icon.setImageResource(R.drawable.ic_star_empty) // ☆ 즐겨찾기 해제
        }
    }

    // 🔹 마이페이지를 떠날 때 Firestore에 변경 사항 반영하는 함수
    fun syncFavoritesWithFirestore(db: FirebaseFirestore, onComplete: () -> Unit) {
        val batch = db.batch()

        favoriteStatus.forEach { (summonerName, isFavorite) ->
            val docRef = db.collection("users").document(userId)
                .collection("favorites").document(summonerName)

            if (isFavorite) {
                val favoriteData = hashMapOf("summonerName" to summonerName)
                batch.set(docRef, favoriteData, SetOptions.merge()) // ✅ 기존 데이터와 병합 (중복 방지)
            } else {
                batch.delete(docRef)
            }
        }

        batch.commit()
            .addOnSuccessListener {
                Log.d("Firestore", "즐겨찾기 동기화 완료")
                onComplete()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "즐겨찾기 동기화 실패", e)
            }
    }

}
