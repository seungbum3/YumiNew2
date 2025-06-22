package com.example.yumi2

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso

class BanPickPredictionActivity : AppCompatActivity() {

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
        setContentView(R.layout.banpick_prediction)

        // 1) Intent 에서 팀명·아이디·밴 리스트 읽기
        blueTeamName = intent.getStringExtra("blue_team_name") ?: "블루팀"
        redTeamName  = intent.getStringExtra("red_team_name")  ?: "레드팀"
        val blueIds  = intent.getStringArrayListExtra("blue_team") ?: listOf()
        val redIds   = intent.getStringArrayListExtra("red_team")  ?: listOf()
        val blueBans = intent.getStringArrayListExtra("blue_bans") ?: listOf()
        val redBans  = intent.getStringArrayListExtra("red_bans")  ?: listOf()

        if (blueIds.size < 5 || redIds.size < 5) {
            Toast.makeText(this, "팀별로 5명의 챔피언을 모두 선택해야 합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2) champion_choice 컬렉션에서 데이터 조회 & 승률 계산
        FirebaseFirestore.getInstance()
            .collection("champion_choice")
            .whereIn(FieldPath.documentId(), blueIds + redIds)
            .get()
            .addOnSuccessListener { snap ->
                val docs = snap.documents.associateBy { it.id }
                lastBlueList = blueIds.mapNotNull { id ->
                    docs[id]?.let { d ->
                        val n = d.getString("name") ?: "?"
                        val iu = d.getString("iconUrl") ?: ""
                        val w = d.getDouble("winRate") ?: 0.0
                        val p = d.getDouble("pickRate") ?: 0.0
                        val b = d.getDouble("banRate") ?: 0.0
                        ChampRow(n, iu, w, p, b, w*0.55 + p*0.25 + b*0.20)
                    }
                }
                lastRedList = redIds.mapNotNull { id ->
                    docs[id]?.let { d ->
                        val n = d.getString("name") ?: "?"
                        val iu = d.getString("iconUrl") ?: ""
                        val w = d.getDouble("winRate") ?: 0.0
                        val p = d.getDouble("pickRate") ?: 0.0
                        val b = d.getDouble("banRate") ?: 0.0
                        ChampRow(n, iu, w, p, b, w*0.55 + p*0.25 + b*0.20)
                    }
                }

                if (lastBlueList!!.size < 5 || lastRedList!!.size < 5) {
                    Toast.makeText(this, "일부 챔피언 정보가 누락되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                // 3) 팀별 스코어 계산
                val blueScore = lastBlueList!!.map { it.score }.average()
                val redScore  = lastRedList!!.map { it.score }.average()
                val total     = blueScore + redScore
                lastBlueRate = if (total>0) blueScore/total*100 else 50.0
                lastRedRate  = if (total>0) redScore/total*100 else 50.0

                // 4) 결과 표시
                findViewById<TextView>(R.id.prediction_result).text =
                    "예상 승률\n블루팀 %.1f%%  |  레드팀 %.1f%%"
                        .format(lastBlueRate, lastRedRate)
                showTable(findViewById(R.id.blue_table), lastBlueList!!)
                showTable(findViewById(R.id.red_table),  lastRedList!!)
            }
            .addOnFailureListener {
                Toast.makeText(this, "챔피언 데이터를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }

        // 5) 저장 버튼: 밴/픽·승률 전부 Firestore 에 쓰기
        findViewById<Button>(R.id.btn_save_banpick).setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                Toast.makeText(this, "로그인 후 저장할 수 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val data = hashMapOf(
                "timestamp"     to System.currentTimeMillis(),
                "blueTeamName"  to blueTeamName,
                "redTeamName"   to redTeamName,
                "blueBans"      to blueBans,
                "redBans"       to redBans,
                "bluePicks"     to lastBlueList!!.map { it.name },
                "redPicks"      to lastRedList!!.map { it.name },
                "blueRate"      to lastBlueRate,
                "redRate"       to lastRedRate
            )
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("savedBanpicks")
                .add(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "밴픽 결과가 저장되었습니다!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // 6) 뒤로가기
        findViewById<Button>(R.id.PageBack).setOnClickListener { finish() }
    }

    private fun showTable(table: TableLayout, list: List<ChampRow>) {
        table.removeAllViews()
        // 헤더
        val header = TableRow(this)
        listOf("", "이름", "승률", "픽률", "밴률").forEachIndexed { i, txt ->
            header.addView(TextView(this).apply {
                text = txt; textSize = 16f; setTextColor(0xFFFFFFFF.toInt())
                if (i>=2) gravity = android.view.Gravity.END
                layoutParams = TableRow.LayoutParams(0,TableRow.LayoutParams.WRAP_CONTENT, if(i==1)2f else 1f)
            })
        }
        table.addView(header)
        // 데이터
        list.forEach { r ->
            val row = TableRow(this)
            // 아이콘
            row.addView(ImageView(this).apply {
                layoutParams = TableRow.LayoutParams(60,60)
                if (r.iconUrl.isNotBlank()) Picasso.get().load(r.iconUrl).into(this)
                else setImageResource(R.drawable.yumi_icon)
            })
            // 이름
            row.addView(TextView(this).apply {
                text=r.name; textSize=15f; setTextColor(0xFFE0E0E0.toInt())
                layoutParams = TableRow.LayoutParams(0,TableRow.LayoutParams.WRAP_CONTENT,2f)
            })
            // 승/픽/밴률
            listOf(r.win, r.pick, r.ban).forEach { pct ->
                row.addView(TextView(this).apply {
                    text="%.1f%%".format(pct); textSize=15f
                    gravity=android.view.Gravity.END
                    setTextColor(
                        when(pct) {
                            r.win  -> 0xFF6EAEFF.toInt()
                            r.pick -> 0xFF4ECC7C.toInt()
                            else   -> 0xFFFF5570.toInt()
                        }
                    )
                    layoutParams = TableRow.LayoutParams(0,TableRow.LayoutParams.WRAP_CONTENT,1f)
                })
            }
            table.addView(row)
        }
    }
}
