package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class BanPickMain : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.banpick_main)

        // RadioGroup을 가져와서 기본 선택을 강제로 TimeOn으로 설정
        val radioGroup = findViewById<RadioGroup>(R.id.TimeCheckBtn)
        radioGroup.check(R.id.TimeOn)

        val BanPickStartBtn: TextView = findViewById(R.id.BanPickStartBtn)
        BanPickStartBtn.setOnClickListener {
            // 시작 버튼을 누를 때마다 기존 밴픽 상태 초기화
            BanPickChampion.currentPickIndex = 0
            BanPickChampionChoice.selectedChampions.clear()

            val selectedTimeOption = radioGroup.checkedRadioButtonId

            Log.d("타이머체크", "선택된 ID: $selectedTimeOption")
            Log.d("타이머체크", "TimeOn: ${R.id.TimeOn}, TimeOFF: ${R.id.TimeOFF}")

            val isTimerEnabled = when (selectedTimeOption) {
                R.id.TimeOn -> true
                R.id.TimeOFF -> false
                else -> false  // 기본은 false
            }

            Log.d("타이머체크", "타이머 활성화 여부: $isTimerEnabled")

            val blueTeamEdit = findViewById<EditText>(R.id.BlueTeamName)
            val redTeamEdit = findViewById<EditText>(R.id.RedTeamName)
            val blueTeamName = if (blueTeamEdit.text.toString().isBlank()) "1" else blueTeamEdit.text.toString()
            val redTeamName = if (redTeamEdit.text.toString().isBlank()) "2" else redTeamEdit.text.toString()

            val intent = Intent(this, BanPickChampion::class.java)
            intent.putExtra("timer_enabled", isTimerEnabled)
            intent.putExtra("blue_team_name", blueTeamName)
            intent.putExtra("red_team_name", redTeamName)
            startActivity(intent)
        }

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigationView.selectedItemId = R.id.category1  // '홈'을 기본 선택

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.category1 -> {
                    startActivity(Intent(this, MainpageActivity::class.java))
                    finish()
                    true
                }
                R.id.category2 -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }

                R.id.category3 -> true

                R.id.category4 -> {
                    startActivity(Intent(this, MyPageActivity::class.java))
                    finish()
                    true
                }

                else -> false
            }
        }
    }
}
