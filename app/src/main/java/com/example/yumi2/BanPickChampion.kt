package com.example.yumi2

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.yumi2.model.ChampionData
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

        findViewById<Button>(R.id.PageBack).setOnClickListener { finish() }

        isTimerEnabled = intent.getBooleanExtra("timer_enabled", false)
        banPickTimeText = findViewById(R.id.BanPickTime)
        banPickTimeText.visibility = if (isTimerEnabled) TextView.VISIBLE else TextView.GONE

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
            BanPickChampionChoice.selectedChampions.clear()
            for (id in pickOrder) {
                val iv = findViewById<ImageView>(id)
                iv.setImageDrawable(null)
                iv.setBackgroundColor(Color.parseColor("#434343"))
            }
            countDownTimer?.cancel()
            if (isTimerEnabled) {
                banPickTimeText.visibility = TextView.VISIBLE
                banPickTimeText.text = "20초"
            }
            Toast.makeText(this, "초기화 완료", Toast.LENGTH_SHORT).show()
            updatePickTitle()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    private fun updatePickTitle() {
        val titleView = findViewById<TextView>(R.id.BanpickTitle)

        // 🔁 모든 픽 영역 테두리 초기화
        for (id in pickOrder) {
            val iv = findViewById<ImageView>(id)
            iv.background = ColorDrawable(Color.parseColor("#434343")) // 기본 배경색 복원
        }

        if (currentPickIndex < pickOrder.size) {
            val resName = resources.getResourceEntryName(pickOrder[currentPickIndex])
            val viewId = pickOrder[currentPickIndex] // ✅ 여기서 선언

            val isBan = resName.contains("ban")
            val highlightView = findViewById<ImageView>(viewId)

            // ✅ 테두리 색 구분 적용
            val highlightDrawable = if (isBan) {
                R.drawable.banpick_ban_highlight // 🔴 금지
            } else {
                R.drawable.banpick_highlight // 🔵 선택
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
        val dialog = BanPickChampionChoice.newInstance { champion ->
            processChampionSelection(champion.id, champion.splashUrl, champion.iconUrl)
        }
        dialog.show(supportFragmentManager, "ChampionDialog")
    }

    private fun startTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                banPickTimeText.text = "${millisUntilFinished / 1000}초"
            }

            override fun onFinish() {
                banPickTimeText.text = "0초"
                if (currentPickIndex < pickOrder.size) {
                    val unused = BanPickChampionChoice.allChampions.filterNot {
                        BanPickChampionChoice.selectedChampions.contains(it.id)
                    }
                    if (unused.isNotEmpty()) {
                        val randomChamp = unused.random()
                        val viewId = pickOrder[currentPickIndex]
                        val imageView = findViewById<ImageView>(viewId)
                        val url = if (resources.getResourceEntryName(viewId).contains("ban")) {
                            randomChamp.iconUrl
                        } else {
                            randomChamp.splashUrl ?: randomChamp.iconUrl
                        }
                        Picasso.get().load(url).into(imageView)
                        BanPickChampionChoice.selectedChampions.add(randomChamp.id)
                        currentPickIndex++
                        Toast.makeText(
                            this@BanPickChampion,
                            "${randomChamp.name} 자동 선택",
                            Toast.LENGTH_SHORT
                        ).show()
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
            BanPickChampionChoice.selectedChampions.add(championId)
            currentPickIndex++
            updatePickTitle()

            // ✅ 실제로 픽이 진행됐을 때만 타이머 시작
            // if (isTimerEnabled && currentPickIndex < pickOrder.size) {
               //  startTimer()
            }
        }
    }

