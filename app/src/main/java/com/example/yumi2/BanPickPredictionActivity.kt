package com.example.yumi2

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
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

    // ⚡ 예측 결과 저장용 변수
    private var lastBlueList: List<ChampRow>? = null
    private var lastRedList: List<ChampRow>? = null
    private var lastBlueRate: Double = 50.0
    private var lastRedRate: Double = 50.0
    private var blueTeamName: String = "블루팀"
    private var redTeamName: String = "레드팀"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.banpick_prediction)

        // 인텐트에서 팀명도 같이 받는다면
        blueTeamName = intent.getStringExtra("blue_team_name") ?: "블루팀"
        redTeamName = intent.getStringExtra("red_team_name") ?: "레드팀"

        val blueIds = intent.getStringArrayListExtra("blue_team") ?: listOf()
        val redIds = intent.getStringArrayListExtra("red_team") ?: listOf()

        if (blueIds.size < 5 || redIds.size < 5) {
            Toast.makeText(this, "팀별로 5명의 챔피언을 모두 선택해야 합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val allIds = blueIds + redIds

        db.collection("champion_choice")
            .whereIn(FieldPath.documentId(), allIds)
            .get()
            .addOnSuccessListener { snap ->
                val blueList = mutableListOf<ChampRow>()
                val redList = mutableListOf<ChampRow>()

                for (doc in snap.documents) {
                    val name = doc.getString("name") ?: "?"
                    val iconUrl = doc.getString("iconUrl") ?: ""
                    val win = doc.getDouble("winRate") ?: 0.0
                    val pick = doc.getDouble("pickRate") ?: 0.0
                    val ban = doc.getDouble("banRate") ?: 0.0
                    val score = win * 0.55 + pick * 0.25 + ban * 0.20
                    val row = ChampRow(name, iconUrl, win, pick, ban, score)
                    if (blueIds.contains(doc.id)) blueList.add(row)
                    else if (redIds.contains(doc.id)) redList.add(row)
                }

                if (blueList.size < 5 || redList.size < 5) {
                    Toast.makeText(this, "일부 챔피언 정보가 누락되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                // 예측 결과 임시 저장
                lastBlueList = blueList
                lastRedList = redList

                val blueScore = blueList.map { it.score }.average()
                val redScore = redList.map { it.score }.average()
                val total = blueScore + redScore
                val blueRate = if (total > 0) blueScore / total * 100 else 50.0
                val redRate = if (total > 0) redScore / total * 100 else 50.0

                lastBlueRate = blueRate
                lastRedRate = redRate

                findViewById<TextView>(R.id.prediction_result).text =
                    "예상 승률\n블루팀 %.1f%%  |  레드팀 %.1f%%".format(blueRate, redRate)

                showTable(findViewById(R.id.blue_table), blueList)
                showTable(findViewById(R.id.red_table), redList)
            }
            .addOnFailureListener {
                Toast.makeText(this, "챔피언 데이터를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }

        // 밴픽 저장 버튼
        findViewById<Button>(R.id.btn_save_banpick).setOnClickListener {
            saveBanPickResult()
        }
        // 돌아가기 버튼
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun saveBanPickResult() {
        // 현재 유저 UID 필요 (로그인 필요)
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

        // 저장할 데이터 구조 예시
        val data = hashMapOf(
            "timestamp" to System.currentTimeMillis(),
            "blueTeamName" to blueTeamName,
            "redTeamName" to redTeamName,
            "bluePicks" to blueList.map { it.name },
            "redPicks" to redList.map { it.name },
            "blueRate" to lastBlueRate,
            "redRate" to lastRedRate
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

    // 표(테이블) 그리기 함수
    private fun showTable(table: TableLayout, list: List<ChampRow>) {
        table.removeAllViews()

        // 헤더
        val header = TableRow(this)
        val headers = listOf("", "이름", "승률", "픽률", "밴률")
        headers.forEachIndexed { i, txt ->
            val tv = TextView(this)
            tv.text = txt
            tv.setPadding(8, 10, 8, 10)
            tv.textSize = 16f
            tv.setTextColor(0xFFFFFFFF.toInt())
            // 오른쪽 컬럼은 오른쪽 정렬
            if (i >= 2) {
                tv.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                tv.setTextAlignment(android.view.View.TEXT_ALIGNMENT_VIEW_END)
            }
            // weight 분배로 넓게
            val params = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT, if (i == 1) 2f else 1f
            )
            tv.layoutParams = params
            header.addView(tv)
        }
        table.addView(header)

        // 데이터 행
        list.forEach {
            val row = TableRow(this)

            // 1. 챔피언 이미지
            val iv = ImageView(this)
            iv.layoutParams = TableRow.LayoutParams(60, 60)
            iv.setPadding(2,2,2,2)
            if (it.iconUrl.isNotBlank()) {
                Picasso.get().load(it.iconUrl).into(iv)
            } else {
                iv.setImageResource(R.drawable.yumi_icon)
            }
            row.addView(iv)

            // 2. 이름
            row.addView(TextView(this).apply {
                text = it.name
                setPadding(8, 10, 8, 10)
                setTextColor(0xFFE0E0E0.toInt())
                textSize = 15f
                // weight 2
                layoutParams = TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, 2f
                )
            })
            // 3. 승률
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
            // 4. 픽률
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
            // 5. 밴률
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
