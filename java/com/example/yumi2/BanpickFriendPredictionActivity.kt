package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso

class BanpickFriendPredictionActivity : AppCompatActivity() {
    data class ChampRow(
        val name: String,
        val iconUrl: String,
        val win: Double,
        val pick: Double,
        val ban: Double,
        val score: Double
    )

    private var lastBlueList: List<ChampRow>? = null
    private var lastRedList: List<ChampRow>? = null
    private var lastBlueRate: Double = 50.0
    private var lastRedRate: Double = 50.0
    private var blueTeamName: String = "블루팀"
    private var redTeamName: String = "레드팀"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.banpick_friend_prediction)

        blueTeamName = intent.getStringExtra("blue_team_name") ?: "블루팀"
        redTeamName  = intent.getStringExtra("red_team_name")  ?: "레드팀"

        val blueIds = intent.getStringArrayListExtra("blue_team") ?: listOf()
        val redIds  = intent.getStringArrayListExtra("red_team")  ?: listOf()

        if (blueIds.size < 5 || redIds.size < 5) {
            Toast.makeText(this, "팀별로 5명의 챔피언을 모두 선택해야 합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val db    = FirebaseFirestore.getInstance()
        val allIds = blueIds + redIds

        db.collection("champion_choice")
            .whereIn(FieldPath.documentId(), allIds)
            .get()
            .addOnSuccessListener { snap ->
                val docsById = snap.documents.associateBy { it.id }

                val blueList = blueIds.mapNotNull { id ->
                    docsById[id]?.let { doc ->
                        val name    = doc.getString("name")    ?: "?"
                        val iconUrl = doc.getString("iconUrl") ?: ""
                        val win     = doc.getDouble("winRate") ?: 0.0
                        val pick    = doc.getDouble("pickRate") ?: 0.0
                        val ban     = doc.getDouble("banRate")  ?: 0.0
                        val score   = win * 0.55 + pick * 0.25 + ban * 0.20
                        ChampRow(name, iconUrl, win, pick, ban, score)
                    }
                }

                val redList = redIds.mapNotNull { id ->
                    docsById[id]?.let { doc ->
                        val name    = doc.getString("name")    ?: "?"
                        val iconUrl = doc.getString("iconUrl") ?: ""
                        val win     = doc.getDouble("winRate") ?: 0.0
                        val pick    = doc.getDouble("pickRate") ?: 0.0
                        val ban     = doc.getDouble("banRate")  ?: 0.0
                        val score   = win * 0.55 + pick * 0.25 + ban * 0.20
                        ChampRow(name, iconUrl, win, pick, ban, score)
                    }
                }

                if (blueList.size < 5 || redList.size < 5) {
                    Toast.makeText(this, "일부 챔피언 정보가 누락되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                lastBlueList = blueList
                lastRedList  = redList

                val blueScore = blueList.map { it.score }.average()
                val redScore  = redList.map  { it.score }.average()
                val total     = blueScore + redScore
                val blueRate  = if (total > 0) (blueScore / total * 100) else 50.0
                val redRate   = if (total > 0) (redScore  / total * 100) else 50.0

                lastBlueRate = blueRate
                lastRedRate  = redRate

                findViewById<TextView>(R.id.prediction_result).text =
                    "예상 승률\n블루팀 %.1f%%  |  레드팀 %.1f%%"
                        .format(blueRate, redRate)

                showTable(findViewById(R.id.blue_table), blueList)
                showTable(findViewById(R.id.red_table),  redList)
            }
            .addOnFailureListener {
                Toast.makeText(this, "챔피언 데이터를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }

        findViewById<Button>(R.id.btn_save_banpick).setOnClickListener { saveBanPickResult() }
        findViewById<Button>(R.id.PageBack).setOnClickListener { finish() }
    }

    private fun saveBanPickResult() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "로그인 후 저장할 수 있습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val blueList = lastBlueList
        val redList = lastRedList
        if (blueList == null || redList == null) {
            Toast.makeText(this, "밴픽 데이터를 불러오는 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val blueBans = listOfNotNull(
            BanpickChampionChoiceFriend.selectedChampions.getOrNull(BanpickFriendChampionActivity.pickOrder.indexOf(R.id.blue_ban_1)),
            BanpickChampionChoiceFriend.selectedChampions.getOrNull(BanpickFriendChampionActivity.pickOrder.indexOf(R.id.blue_ban_2)),
            BanpickChampionChoiceFriend.selectedChampions.getOrNull(BanpickFriendChampionActivity.pickOrder.indexOf(R.id.blue_ban_3)),
            BanpickChampionChoiceFriend.selectedChampions.getOrNull(BanpickFriendChampionActivity.pickOrder.indexOf(R.id.blue_ban_4)),
            BanpickChampionChoiceFriend.selectedChampions.getOrNull(BanpickFriendChampionActivity.pickOrder.indexOf(R.id.blue_ban_5))
        )

        val redBans = listOfNotNull(
            BanpickChampionChoiceFriend.selectedChampions.getOrNull(BanpickFriendChampionActivity.pickOrder.indexOf(R.id.red_ban_1)),
            BanpickChampionChoiceFriend.selectedChampions.getOrNull(BanpickFriendChampionActivity.pickOrder.indexOf(R.id.red_ban_2)),
            BanpickChampionChoiceFriend.selectedChampions.getOrNull(BanpickFriendChampionActivity.pickOrder.indexOf(R.id.red_ban_3)),
            BanpickChampionChoiceFriend.selectedChampions.getOrNull(BanpickFriendChampionActivity.pickOrder.indexOf(R.id.red_ban_4)),
            BanpickChampionChoiceFriend.selectedChampions.getOrNull(BanpickFriendChampionActivity.pickOrder.indexOf(R.id.red_ban_5))
        )

        val data = hashMapOf(
            "timestamp" to System.currentTimeMillis(),
            "blueTeamName" to blueTeamName,
            "redTeamName" to redTeamName,
            "bluePicks" to blueList.map { it.name },
            "redPicks" to redList.map { it.name },
            "blueRate" to lastBlueRate,
            "redRate" to lastRedRate,
            "blueBans" to blueBans,
            "redBans" to redBans
        )

        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("savedBanpicks")
            .add(data)
            .addOnSuccessListener {
                Toast.makeText(this, "밴픽 결과가 저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showTable(table: TableLayout, list: List<ChampRow>) {
        table.removeAllViews()

        val header = TableRow(this)
        val headers = listOf("", "이름", "승률", "픽률", "밴률")
        headers.forEachIndexed { i, txt ->
            val tv = TextView(this)
            tv.text = txt
            tv.setPadding(8, 10, 8, 10)
            tv.textSize = 16f
            tv.setTextColor(0xFFFFFFFF.toInt())
            if (i >= 2) {
                tv.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                tv.setTextAlignment(android.view.View.TEXT_ALIGNMENT_VIEW_END)
            }
            val params = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT, if (i == 1) 2f else 1f
            )
            tv.layoutParams = params
            header.addView(tv)
        }
        table.addView(header)

        list.forEach {
            val row = TableRow(this)

            val iv = ImageView(this)
            iv.layoutParams = TableRow.LayoutParams(60, 60)
            iv.setPadding(2,2,2,2)
            if (it.iconUrl.isNotBlank()) {
                Picasso.get().load(it.iconUrl).into(iv)
            } else {
                iv.setImageResource(R.drawable.yumi_icon)
            }
            row.addView(iv)

            row.addView(TextView(this).apply {
                text = it.name
                setPadding(8, 10, 8, 10)
                setTextColor(0xFFE0E0E0.toInt())
                textSize = 15f
                layoutParams = TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, 2f
                )
            })

            row.addView(TextView(this).apply {
                text = "%.1f%%".format(it.win)
                gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                setTextAlignment(android.view.View.TEXT_ALIGNMENT_VIEW_END)
                setTextColor(0xFF6EAEFF.toInt())
                textSize = 15f
                layoutParams = TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, 1f
                )
            })

            row.addView(TextView(this).apply {
                text = "%.1f%%".format(it.pick)
                gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                setTextAlignment(android.view.View.TEXT_ALIGNMENT_VIEW_END)
                setTextColor(0xFF4ECC7C.toInt())
                textSize = 15f
                layoutParams = TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, 1f
                )
            })

            row.addView(TextView(this).apply {
                text = "%.1f%%".format(it.ban)
                gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                setTextAlignment(android.view.View.TEXT_ALIGNMENT_VIEW_END)
                setTextColor(0xFFFF5570.toInt())
                textSize = 15f
                layoutParams = TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, 1f
                )
            })
            table.addView(row)
        }
    }
}
