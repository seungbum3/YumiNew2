package com.example.yumi2

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.yumi2.model.ChampionData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso

class BanPickChampion : AppCompatActivity() {

    companion object {
        var currentPickIndex = 0
        val pickOrder = listOf(
            R.id.blue_ban_1, R.id.red_ban_1,
            R.id.blue_ban_2, R.id.red_ban_2,
            R.id.blue_ban_3, R.id.red_ban_3,
            R.id.blue1, R.id.red1, R.id.red2,
            R.id.blue2, R.id.blue3,
            R.id.red3,
            R.id.blue_ban_4, R.id.red_ban_4,
            R.id.blue_ban_5, R.id.red_ban_5,
            R.id.red4,
            R.id.blue4, R.id.blue5,
            R.id.red5
        )
    }

    private var isTimerEnabled = false
    private lateinit var banPickTimeText: TextView
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.banpick_champion)
        ensureSelectionsInit()

        findViewById<Button>(R.id.PageBack).setOnClickListener { finish() }

        isTimerEnabled = intent.getBooleanExtra("timer_enabled", false)
        banPickTimeText = findViewById(R.id.BanPickTime)
        banPickTimeText.visibility = if (isTimerEnabled) TextView.VISIBLE else TextView.GONE

        if (isTimerEnabled) {
            startTimer()
        }

        findViewById<TextView>(R.id.BlueTeam).text =
            intent.getStringExtra("blue_team_name") ?: "불루팀"
        findViewById<TextView>(R.id.RedTeam).text = intent.getStringExtra("red_team_name") ?: "레드팀"

        updatePickTitle()

        findViewById<Button>(R.id.Champion).setOnClickListener {
            if (currentPickIndex >= pickOrder.size) {
                Toast.makeText(this, "모든 챔피언이 선택되었습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showChampionChoiceDialog()
        }

        findViewById<Button>(R.id.Reset).setOnClickListener {
            currentPickIndex = 0
            BanPickChampionChoice.resetSelections()
            for (id in pickOrder) {
                val iv = findViewById<ImageView>(id)
                val resName = resources.getResourceEntryName(id)
                if (resName.contains("ban")) {
                    iv.setImageResource(R.drawable.champion_ban)
                } else {
                    iv.setImageDrawable(null)
                }
                iv.setBackgroundColor(Color.parseColor("#434343"))
            }
            countDownTimer?.cancel()
            if (isTimerEnabled) {
                banPickTimeText.visibility = TextView.VISIBLE
                banPickTimeText.text = "60초"
                startTimer()
            }
            Toast.makeText(this, "초기화 완료", Toast.LENGTH_SHORT).show()
            updatePickTitle()
        }

        findViewById<Button>(R.id.WhoWin).setOnClickListener {
            val selected = BanPickChampionChoice.selectedChampions.filter { it.isNotBlank() }
            if (selected.size < 10) {
                Toast.makeText(this, "블루팀/레드팀 챔피언을 모두 선택해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val blueTeam = selected.subList(0, 5)
            val redTeam = selected.subList(5, 10)
            val intent = Intent(this, BanPickPredictionActivity::class.java)
            intent.putStringArrayListExtra("blue_team", ArrayList(blueTeam))
            intent.putStringArrayListExtra("red_team", ArrayList(redTeam))
            startActivity(intent)
        }

        // 밴픽 저장 버튼
        findViewById<Button>(R.id.SaveBanPick).setOnClickListener {
            val blueTeamName = findViewById<TextView>(R.id.BlueTeam).text.toString()
            val redTeamName = findViewById<TextView>(R.id.RedTeam).text.toString()
            val selected = BanPickChampionChoice.selectedChampions.toList()

            Log.d(
                "BanPickSaveCheck",
                "selectedChampions.size=${BanPickChampionChoice.selectedChampions.size}, pickOrder.size=${pickOrder.size}, selectedChampions=${BanPickChampionChoice.selectedChampions.joinToString()}"
            )

            // 인덱스에 맞춰 밴/픽 분리
            val blueBanIdxs = listOf(0, 2, 12, 14, 17)
            val redBanIdxs = listOf(1, 3, 13, 15, 18)
            val bluePickIdxs = listOf(6, 9, 10, 16, 19)
            val redPickIdxs = listOf(7, 8, 11, 15, 18)

            val blueBans = blueBanIdxs.map { idx -> selected.getOrNull(idx) ?: "" }
            val redBans = redBanIdxs.map { idx -> selected.getOrNull(idx) ?: "" }
            val bluePicks = bluePickIdxs.map { idx -> selected.getOrNull(idx) ?: "" }
            val redPicks = redPickIdxs.map { idx -> selected.getOrNull(idx) ?: "" }

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                Toast.makeText(this, "로그인 후 저장할 수 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val data = hashMapOf(
                "timestamp" to System.currentTimeMillis(),
                "blueTeamName" to blueTeamName,
                "redTeamName" to redTeamName,
                "blueBans" to blueBans,
                "redBans" to redBans,
                "bluePicks" to bluePicks,
                "redPicks" to redPicks
            )
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("savedBanpicks")
                .add(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "밴픽이 저장되었습니다!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onPause() {
        super.onPause()
        BanPickChampionChoice.resetSelections()
        currentPickIndex = 0
    }
    override fun onDestroy() {
        super.onDestroy()
        BanPickChampionChoice.resetSelections()
        countDownTimer?.cancel()
    }
    private fun ensureSelectionsInit() {
        if (BanPickChampionChoice.selectedChampions.size != pickOrder.size) {
            BanPickChampionChoice.selectedChampions = MutableList(pickOrder.size) { "" }
        }
    }
    private fun updatePickTitle() {
        val titleView = findViewById<TextView>(R.id.BanpickTitle)
        for (id in pickOrder) {
            val iv = findViewById<ImageView>(id)
            iv.background = ColorDrawable(Color.parseColor("#434343"))
        }
        if (currentPickIndex < pickOrder.size) {
            val resName = resources.getResourceEntryName(pickOrder[currentPickIndex])
            val viewId = pickOrder[currentPickIndex]
            val isBan = resName.contains("ban")
            val highlightView = findViewById<ImageView>(viewId)
            val highlightDrawable = if (isBan) {
                R.drawable.banpick_ban_highlight
            } else {
                R.drawable.banpick_highlight
            }
            highlightView.setBackgroundResource(highlightDrawable)
            val team = when {
                resName.startsWith("blue") -> "불루팀"
                resName.startsWith("red") -> "레드팀"
                else -> ""
            }
            val title = if (isBan) "$team 금지 챔피언 선택해주세요"
            else "$team ${resName.filter { it.isDigit() }}번 챔피언 선택해주세요"
            titleView.text = title
            banPickTimeText.visibility = if (isTimerEnabled) TextView.VISIBLE else TextView.GONE
        } else {
            titleView.text = "밴픽 완료"
            banPickTimeText.visibility = TextView.GONE
        }
    }

    private fun showChampionChoiceDialog() {
        val dialog = BanPickChampionChoice.newInstance(
            mode = "solo", // ← 여기 명시적으로 모드를 넣어줌
            onChampionSelected = { champion ->
                processChampionSelection(champion.id, champion.splashUrl, champion.iconUrl)
            }
        )
        dialog.show(supportFragmentManager, "ChampionDialog")
    }


    private fun startTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                banPickTimeText.text = "${millisUntilFinished / 1000}초"
            }

            override fun onFinish() {
                banPickTimeText.text = "0초"

                if (currentPickIndex < pickOrder.size) {
                    val viewId = pickOrder[currentPickIndex]
                    val resName = resources.getResourceEntryName(viewId)

                    // 사용되지 않은 챔피언 리스트
                    val unused = BanPickChampionChoice.allChampions.filterNot {
                        BanPickChampionChoice.selectedChampions.contains(it.id)
                    }

                    if (unused.isNotEmpty()) {
                        val randomChamp = unused.random()
                        val imageView = findViewById<ImageView>(viewId)
                        val isBan = resName.contains("ban")
                        val url = if (isBan) randomChamp.iconUrl else randomChamp.splashUrl
                            ?: randomChamp.iconUrl

                        if (!url.isNullOrBlank()) {
                            Picasso.get().load(url).into(imageView)
                        }

                        BanPickChampionChoice.selectedChampions[currentPickIndex] = randomChamp.id
                        currentPickIndex++

                        val toastMsg = if (isBan) "금지 챔피언 ${randomChamp.name} 자동 선택"
                        else "${randomChamp.name} 자동 선택"
                        Toast.makeText(this@BanPickChampion, toastMsg, Toast.LENGTH_SHORT).show()

                        updatePickTitle()
                        if (currentPickIndex < pickOrder.size) startTimer()
                    }
                }
            }
        }.start()
    }

    private fun processChampionSelection(championId: String, splashUrl: String?, iconUrl: String?) {
        if (championId.isNotEmpty() && currentPickIndex < pickOrder.size) {
            val viewId = pickOrder[currentPickIndex]
            val imageView = findViewById<ImageView>(viewId)
            val resName = resources.getResourceEntryName(viewId)
            val url = if (resName.contains("ban")) iconUrl else splashUrl ?: iconUrl
            if (!url.isNullOrBlank()) {
                Picasso.get().load(url).into(imageView)
            } else {
                Toast.makeText(this, "이미지 로딩 실패", Toast.LENGTH_SHORT).show()
            }
            BanPickChampionChoice.selectedChampions[currentPickIndex] = championId
            currentPickIndex++
            updatePickTitle()
            if (isTimerEnabled && currentPickIndex < pickOrder.size) {
                startTimer()
            }
        }
    }
}
