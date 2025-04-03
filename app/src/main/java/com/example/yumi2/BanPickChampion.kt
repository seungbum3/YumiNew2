package com.example.yumi2

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.squareup.picasso.Picasso

class BanPickChampion : AppCompatActivity() {

    companion object {
        // 현재 몇 번째 픽인지 나타내는 인덱스
        var currentPickIndex = 0

        // 픽 순서에 해당하는 View ID (blue1 → red1 → red2 → ...)
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

        val PageBack: Button = findViewById(R.id.PageBack)
        PageBack.setOnClickListener {
            startActivity(Intent(this, BanPickMain::class.java))
        }

        // BanPickMain에서 넘긴 타이머 ON/OFF 값을 받기
        isTimerEnabled = intent.getBooleanExtra("timer_enabled", false)
        Log.d("TimerCheck", "타이머 활성화 여부: $isTimerEnabled")

        // 시간 표시 TextView
        banPickTimeText = findViewById(R.id.BanPickTime)

        // 타이머가 OFF면 TextView 숨김
        if (!isTimerEnabled) {
            banPickTimeText.visibility = TextView.GONE
        }

        val BlueTeamName = intent.getStringExtra("blue_team_name") ?: "블루팀"
        val RedTeamName = intent.getStringExtra("red_team_name") ?: "레드팀"
        val blueTeamTextView = findViewById<TextView>(R.id.BlueTeam)
        val redTeamTextView = findViewById<TextView>(R.id.RedTeam)
        blueTeamTextView.text = BlueTeamName
        redTeamTextView.text = RedTeamName

        // 초기 제목 업데이트 (첫 픽의 경우)
        updatePickTitle()

        val championButton: Button = findViewById(R.id.Champion)
        championButton.setOnClickListener {
            if (currentPickIndex >= pickOrder.size) {
                Toast.makeText(this, "모든 챔피언이 선택되었습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, BanPickChampionChoice::class.java)
            intent.putExtra("timer_enabled", isTimerEnabled)
            startActivityForResult(intent, 1001)

            if (isTimerEnabled) {
                startTimer()
            }
        }

        val resetBtn: Button = findViewById(R.id.Reset)
        resetBtn.setOnClickListener {
            currentPickIndex = 0
            BanPickChampionChoice.selectedChampions.clear()

            for (id in pickOrder) {
                val imageView = findViewById<ImageView>(id)
                imageView.setImageDrawable(null)
                imageView.setBackgroundColor(Color.parseColor("#434343"))
            }

            countDownTimer?.cancel()
            if (isTimerEnabled) {
                banPickTimeText.visibility = TextView.VISIBLE
                banPickTimeText.text = "20초"
            } else {
                banPickTimeText.visibility = TextView.GONE
            }
            Toast.makeText(this, "초기화 완료", Toast.LENGTH_SHORT).show()

            updatePickTitle()
        }
    }

    private fun updatePickTitle() {
        val titleTextView = findViewById<TextView>(R.id.BanpickTitle)

        if (currentPickIndex < pickOrder.size) {
            val resourceName = resources.getResourceEntryName(pickOrder[currentPickIndex])

            val isBan = resourceName.contains("ban")
            val team = when {
                resourceName.startsWith("blue") -> "블루팀"
                resourceName.startsWith("red") -> "레드팀"
                else -> ""
            }

            val title = if (isBan) {
                "$team 금지 챔피언 선택해주세요"
            } else {
                val number = resourceName.filter { it.isDigit() }
                "$team ${number}번 챔피언 선택해주세요"
            }

            titleTextView.text = title
            banPickTimeText.visibility = if (isTimerEnabled) TextView.VISIBLE else TextView.GONE
        } else {
            titleTextView.text = "밴픽 완료"
            banPickTimeText.visibility = TextView.GONE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001 && resultCode == Activity.RESULT_OK && data != null) {
            countDownTimer?.cancel()

            val championId = data.getStringExtra("championId")
            val splashUrl = data.getStringExtra("splashUrl")?.takeIf { it.isNotBlank() }
            val iconUrl = data.getStringExtra("iconUrl")?.takeIf { it.isNotBlank() }

            Log.d("BanPick", "onActivityResult - championId: $championId")
            Log.d("BanPick", "onActivityResult - splashUrl: $splashUrl")
            Log.d("BanPick", "onActivityResult - iconUrl: $iconUrl")

            if (championId != null && currentPickIndex < pickOrder.size) {
                val imageViewId = pickOrder[currentPickIndex]
                val imageView = findViewById<ImageView>(imageViewId)
                val resourceName = resources.getResourceEntryName(imageViewId)

                val imageToUse = if (resourceName.contains("ban")) {
                    iconUrl
                } else {
                    splashUrl ?: iconUrl // splash가 null이면 imageUrl 사용
                }

                if (!imageToUse.isNullOrBlank()) {
                    Picasso.get().load(imageToUse).into(imageView)
                } else {
                    Toast.makeText(this, "이미지 로딩 실패", Toast.LENGTH_SHORT).show()
                }

                BanPickChampionChoice.selectedChampions.add(championId)
                currentPickIndex++
            }

            updatePickTitle()

            if (isTimerEnabled && currentPickIndex < pickOrder.size) {
                startTimer()
            }
        }
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
                    val unusedChampions = BanPickChampionChoice.allChampions.filter {
                        !BanPickChampionChoice.selectedChampions.contains(it.id)
                    }

                    if (unusedChampions.isNotEmpty()) {
                        val randomChamp = unusedChampions.random()
                        val imageViewId = pickOrder[currentPickIndex]
                        val imageView = findViewById<ImageView>(imageViewId)

                        val imageToUse = if (resources.getResourceEntryName(imageViewId).contains("ban")) {
                            randomChamp.iconUrl
                        } else {
                            randomChamp.splashUrl ?: randomChamp.iconUrl
                        }

                        Picasso.get().load(imageToUse).into(imageView)
                        BanPickChampionChoice.selectedChampions.add(randomChamp.id)
                        currentPickIndex++

                        Toast.makeText(
                            this@BanPickChampion,
                            "${randomChamp.name} 자동 선택",
                            Toast.LENGTH_SHORT
                        ).show()

                        updatePickTitle()

                        if (currentPickIndex < pickOrder.size) {
                            startTimer()
                        }
                    }
                }
            }
        }.start()
    }
}
