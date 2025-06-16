package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.yumi2.comment.MainActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BanPickMain : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.banpick_main)

        // 타이머 라디오 그룹 초기화
        val radioGroup = findViewById<RadioGroup>(R.id.TimeCheckBtn)
        radioGroup.check(R.id.TimeOn)

        // 팀명 입력
        val blueTeamEdit = findViewById<EditText>(R.id.BlueTeamName)
        val redTeamEdit  = findViewById<EditText>(R.id.RedTeamName)

        // 밴픽 시작 버튼 (혼자 모드)
        val BanPickStartBtn = findViewById<TextView>(R.id.BanPickStartBtn)
        BanPickStartBtn.setOnClickListener {
            BanPickChampion.currentPickIndex = 0
            BanPickChampionChoice.selectedChampions.clear()

            val isTimerEnabled = when (radioGroup.checkedRadioButtonId) {
                R.id.TimeOn  -> true
                R.id.TimeOFF -> false
                else         -> false
            }

            val blueTeamName = blueTeamEdit.text.toString().ifBlank { "1" }
            val redTeamName  = redTeamEdit.text.toString().ifBlank  { "2" }

            Intent(this, BanPickChampion::class.java).apply {
                putExtra("timer_enabled",  isTimerEnabled)
                putExtra("blue_team_name", blueTeamName)
                putExtra("red_team_name",  redTeamName)
                startActivity(this)
            }
        }

        // 방 만들기 버튼 (친구 모드)
        findViewById<Button>(R.id.BanPick_Friend_StartBtn).setOnClickListener {
            createBanPickRoom()
        }

        // 방 들어가기용 EditText, Button
        val etRoomCode = findViewById<EditText>(R.id.etRoomCode)
        findViewById<Button>(R.id.BanPick_Friend_GoBtn).setOnClickListener {
            val roomCode = etRoomCode.text.toString().trim()
            if (roomCode.isEmpty()) {
                Toast.makeText(this, "방 코드를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val db  = FirebaseFirestore.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) {
                Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val roomRef = db.collection("banpick_rooms").document(roomCode)
            roomRef.get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        Toast.makeText(this, "존재하지 않는 방 코드입니다.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val blueTeamUid = (doc["blueTeam"] as Map<*, *>)["uid"] as? String
                    val redTeamUid  = (doc["redTeam"]  as Map<*, *>)["uid"] as? String

                    val assignToField = when {
                        blueTeamUid.isNullOrBlank() -> "blueTeam"
                        redTeamUid .isNullOrBlank() -> "redTeam"
                        else -> {
                            Toast.makeText(this, "이미 양 팀이 모두 찼습니다.", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }
                    }

                    roomRef.update("$assignToField.uid", uid)
                        .addOnSuccessListener {
                            val team = if (assignToField == "blueTeam") "blue" else "red"
                            Toast.makeText(
                                this,
                                "✅ ${if (team == "blue") "블루팀" else "레드팀"} 으로 입장했습니다",
                                Toast.LENGTH_SHORT
                            ).show()

                            Intent(this, BanpickFriendChampionActivity::class.java).apply {
                                putExtra("roomCode", roomCode)
                                putExtra("team",     team)
                                startActivity(this)
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "팀 배정 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // 하단 네비게이션
        findViewById<BottomNavigationView>(R.id.bottomNavigation).apply {
            selectedItemId = R.id.category3
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.category1 -> { startActivity(Intent(this@BanPickMain, MainpageActivity::class.java)); finish(); true }
                    R.id.category2 -> { startActivity(Intent(this@BanPickMain, MainActivity::class.java)); finish(); true }
                    R.id.category3 -> true
                    R.id.category4 -> { startActivity(Intent(this@BanPickMain, MyPageActivity::class.java)); finish(); true }
                    else -> false
                }
            }
        }
    }

    /** 4자리 랜덤 방 코드 생성 */
    private fun generateRoomCode(): String {
        val chars = ('A'..'Z') + ('0'..'9')
        return (1..4).map { chars.random() }.joinToString("")
    }

    /** Firestore에 새 방 문서 생성 */
    private fun createBanPickRoom() {
        val roomCode = generateRoomCode()
        val uid      = FirebaseAuth.getInstance().currentUser?.uid
            ?: return Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()

        val roomData = mapOf(
            "status"    to "waiting",
            "createdBy" to uid,
            "blueTeam"  to mapOf(
                "uid"   to uid,
                "picks" to emptyList<String>(),
                "bans"  to emptyList<String>()
            ),
            "redTeam" to mapOf(
                "uid"   to null,
                "picks" to emptyList<String>(),
                "bans"  to emptyList<String>()
            ),
            "turnIndex" to 0,
            "timestamp" to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance()
            .collection("banpick_rooms")
            .document(roomCode)
            .set(roomData)
            .addOnSuccessListener {
                Log.d("BanPick", "✅ 방 생성 완료: $roomCode")
                // **토스트로 팀 안내**
                Toast.makeText(this, "당신은 블루팀 입니다", Toast.LENGTH_SHORT).show()

                Intent(this, BanpickFriendChampionActivity::class.java).apply {
                    putExtra("roomCode", roomCode)
                    putExtra("team",     "blue")
                    startActivity(this)
                }
            }
            .addOnFailureListener { e ->
                Log.e("BanPick", "❌ 방 생성 실패: ${e.message}")
                Toast.makeText(this, "방 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
