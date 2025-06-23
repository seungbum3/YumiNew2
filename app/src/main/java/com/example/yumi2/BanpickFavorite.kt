package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore

class BanpickFavorite : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid
    private val pickViews = listOf(
        R.id.blue1, R.id.blue2, R.id.blue3, R.id.blue4, R.id.blue5,
        R.id.red1, R.id.red2, R.id.red3, R.id.red4, R.id.red5
    )
    private val banViews = listOf(
        R.id.blue_ban_1, R.id.blue_ban_2, R.id.blue_ban_3, R.id.blue_ban_4, R.id.blue_ban_5,
        R.id.red_ban_1, R.id.red_ban_2, R.id.red_ban_3, R.id.red_ban_4, R.id.red_ban_5
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.banpick_favorite)

        findViewById<Button>(R.id.PageBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.GetBanPick).setOnClickListener {
            if (uid == null) {
                Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.collection("users")
                .document(uid)
                .collection("savedBanpicks")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        Toast.makeText(this, "저장된 밴픽이 없습니다.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val data = documents.first().data
                    val bluePicks = data["bluePicks"] as? List<*> ?: emptyList<String>()
                    val redPicks  = data["redPicks"]  as? List<*> ?: emptyList<String>()

                    // ✅ pick 이미지 불러오기 (한글 기준)
                    val allPicks = bluePicks + redPicks
                    db.collection("champion_choice")
                        .whereIn("name", allPicks)
                        .get()
                        .addOnSuccessListener { snap ->
                            val map = snap.documents.associateBy { it.getString("name") ?: "" }
                            for ((i, name) in allPicks.withIndex()) {
                                val champDoc = map[name]
                                val url = champDoc?.getString("splashUrl")
                                if (!url.isNullOrEmpty()) {
                                    Glide.with(this)
                                        .load(url)
                                        .into(findViewById(pickViews[i]))
                                }
                            }
                        }

                    // ban 챔피언도 있다면 표시
                    val blueBans = data["blueBans"] as? List<*> ?: emptyList<String>()
                    val redBans  = data["redBans"]  as? List<*> ?: emptyList<String>()

                    // ✅ ban 이미지 불러오기 (영문 document ID 기준)
                    val allBans = blueBans + redBans
                    db.collection("champion_choice")
                        .whereIn(FieldPath.documentId(), allBans)
                        .get()
                        .addOnSuccessListener { snap ->
                            val map = snap.documents.associateBy { it.id }

                            // blueBans 먼저
                            for ((i, id) in blueBans.withIndex()) {
                                val champDoc = map[id]
                                val url = champDoc?.getString("iconUrl")
                                if (!url.isNullOrEmpty() && i < 5) {
                                    Glide.with(this)
                                        .load(url)
                                        .into(findViewById(banViews[i]))
                                }
                            }

                            // redBans 다음
                            for ((i, id) in redBans.withIndex()) {
                                val champDoc = map[id]
                                val url = champDoc?.getString("iconUrl")
                                if (!url.isNullOrEmpty() && i + 5 < banViews.size) {
                                    Glide.with(this)
                                        .load(url)
                                        .into(findViewById(banViews[i + 5]))
                                }
                            }
                        }


                }
                .addOnFailureListener {
                    Toast.makeText(this, "밴픽을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    Log.e("BanpickFavorite", "불러오기 실패", it)
                }
        }
    }
}
