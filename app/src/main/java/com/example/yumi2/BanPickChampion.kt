package com.example.yumi2

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
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
        if (isTimerEnabled) startTimer()

        findViewById<TextView>(R.id.BlueTeam).text =
            intent.getStringExtra("blue_team_name") ?: "불루팀"
        findViewById<TextView>(R.id.RedTeam).text =
            intent.getStringExtra("red_team_name") ?: "레드팀"

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
            pickOrder.forEach { id ->
                val iv = findViewById<ImageView>(id)
                val name = resources.getResourceEntryName(id)
                if (name.contains("ban")) iv.setImageResource(R.drawable.champion_ban)
                else iv.setImageDrawable(null)
                iv.setBackgroundColor(Color.parseColor("#434343"))
            }
            countDownTimer?.cancel()
            if (isTimerEnabled) {
                banPickTimeText.visibility = TextView.VISIBLE
                banPickTimeText.text = "30초"
                startTimer()
            }
            Toast.makeText(this, "초기화 완료", Toast.LENGTH_SHORT).show()
            updatePickTitle()
        }

        findViewById<Button>(R.id.WhoWin).setOnClickListener {
            // 1) 현재까지 선택된 20개 슬롯 리스트
            val all = BanPickChampionChoice.selectedChampions
            if (all.any { it.isBlank() }) {
                Toast.makeText(this, "블루팀/레드팀 챔피언을 모두 선택해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2) 정답 인덱스 배열
            val blueBanIdxs  = listOf(0, 2, 4, 12, 14)
            val redBanIdxs   = listOf(1, 3, 5, 13, 15)
            val bluePickIdxs = listOf(6, 9, 10, 17, 18)
            val redPickIdxs  = listOf(7, 8, 11, 16, 19)

            // 3) 각 팀 밴/픽 리스트 추출
            val blueBans  = blueBanIdxs .map { all[it] }
            val redBans   = redBanIdxs  .map { all[it] }
            val bluePicks = bluePickIdxs.map { all[it] }
            val redPicks  = redPickIdxs .map { all[it] }

            // 4) Intent 에 실어서 Prediction 화면으로
            val intent = Intent(this, BanPickPredictionActivity::class.java).apply {
                putStringArrayListExtra("blue_team", ArrayList(bluePicks))
                putStringArrayListExtra("red_team",  ArrayList(redPicks))
                putStringArrayListExtra("blue_bans", ArrayList(blueBans))
                putStringArrayListExtra("red_bans",  ArrayList(redBans))
                putExtra("blue_team_name", findViewById<TextView>(R.id.BlueTeam).text.toString())
                putExtra("red_team_name",  findViewById<TextView>(R.id.RedTeam).text.toString())
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentPickIndex >= pickOrder.size) {
            banPickTimeText.visibility = TextView.GONE
            Toast.makeText(this, "이미 밴픽이 완료되었습니다.", Toast.LENGTH_SHORT).show()
        }
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
        pickOrder.forEach { id ->
            findViewById<ImageView>(id)
                .background = ColorDrawable(Color.parseColor("#434343"))
        }
        if (currentPickIndex < pickOrder.size) {
            val id       = pickOrder[currentPickIndex]
            val name     = resources.getResourceEntryName(id)
            val isBan    = name.contains("ban")
            val highlight= findViewById<ImageView>(id)
            highlight.setBackgroundResource(
                if (isBan) R.drawable.banpick_ban_highlight
                else       R.drawable.banpick_highlight
            )
            val teamText = if (name.startsWith("blue")) "불루팀" else "레드팀"
            val title    = if (isBan)
                "$teamText 금지 챔피언 선택해주세요"
            else
                "$teamText ${name.filter { it.isDigit() }}번 챔피언 선택해주세요"
            titleView.text = title
            banPickTimeText.visibility = if (isTimerEnabled) TextView.VISIBLE else TextView.GONE
        } else {
            titleView.text = "밴픽 완료"
            banPickTimeText.visibility = TextView.GONE
        }
    }

    private fun showChampionChoiceDialog() {
        val dialog = BanPickChampionChoice.newInstance(
            mode = "solo",
            onChampionSelected = { champ ->
                processChampionSelection(champ.id, champ.splashUrl, champ.iconUrl)
            }
        )
        dialog.show(supportFragmentManager, "ChampionDialog")
    }

    private fun startTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(30_000, 1_000) {
            override fun onTick(ms: Long) {
                banPickTimeText.text = "${ms/1000}초"
            }
            override fun onFinish() {
                banPickTimeText.text = "0초"
                if (currentPickIndex < pickOrder.size) {
                    val id = pickOrder[currentPickIndex]
                    val name = resources.getResourceEntryName(id)
                    val unused = BanPickChampionChoice.allChampions
                        .filterNot { BanPickChampionChoice.selectedChampions.contains(it.id) }
                    if (unused.isNotEmpty()) {
                        val r = unused.random()
                        val iv = findViewById<ImageView>(id)
                        val url = if (name.contains("ban")) r.iconUrl else r.splashUrl ?: r.iconUrl
                        if (url.isNotBlank()) Picasso.get().load(url).into(iv)
                        BanPickChampionChoice.selectedChampions[currentPickIndex] = r.id
                        currentPickIndex++
                        Toast.makeText(
                            this@BanPickChampion,
                            if (name.contains("ban")) "금지 ${r.name} 자동 선택"
                            else "${r.name} 자동 선택",
                            Toast.LENGTH_SHORT
                        ).show()
                        updatePickTitle()
                        if (currentPickIndex < pickOrder.size) startTimer()
                    }
                }
            }
        }.start()
    }

    private fun processChampionSelection(id: String, splash: String?, icon: String?) {
        if (id.isNotEmpty() && currentPickIndex < pickOrder.size) {
            val viewId = pickOrder[currentPickIndex]
            val iv = findViewById<ImageView>(viewId)
            val name = resources.getResourceEntryName(viewId)
            val url = if (name.contains("ban")) icon else splash ?: icon
            if (url?.isNotBlank() == true) Picasso.get().load(url).into(iv)
            else Toast.makeText(this, "이미지 로딩 실패", Toast.LENGTH_SHORT).show()
            BanPickChampionChoice.selectedChampions[currentPickIndex] = id
            currentPickIndex++
            updatePickTitle()
            if (isTimerEnabled && currentPickIndex < pickOrder.size) startTimer()
        }
    }
}
