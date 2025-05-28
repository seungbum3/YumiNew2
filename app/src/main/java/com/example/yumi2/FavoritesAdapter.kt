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
        val summonerName = favorite["summonerName"] ?: "닉네임 없음"

        // 아이콘과 닉네임을 설정 (아이콘은 채워진 별 이미지로 표시)
        holder.favoriteName.text = summonerName
        holder.favoriteIcon.setImageResource(R.drawable.ic_star) // 즐겨찾기된 상태

        holder.favoriteIcon.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(userId)
                .collection("favorites")
                .whereEqualTo("summonerName", summonerName)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    for (document in querySnapshot.documents) {
                        db.collection("users").document(userId)
                            .collection("favorites")
                            .document(document.id)
                            .delete()
                            .addOnSuccessListener {
                                Log.d("FavoritesAdapter", "즐겨찾기 제거 성공!")
                                // 로컬 리스트 업데이트
                                favoriteList.removeAt(currentPosition)
                                notifyItemRemoved(currentPosition)
                                notifyItemRangeChanged(currentPosition, favoriteList.size)
                            }
                            .addOnFailureListener { e ->
                                Log.e("FavoritesAdapter", "즐겨찾기 제거 실패: $e")
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FavoritesAdapter", "즐겨찾기 쿼리 실패: $e")
                }
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