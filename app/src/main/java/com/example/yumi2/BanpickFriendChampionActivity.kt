package com.example.yumi2

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.ContextThemeWrapper
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.yumi2.model.ChampionData
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BanpickFriendChampionActivity : AppCompatActivity() {

    companion object {
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

    private var isTimerEnabled = true
    private lateinit var banPickTimeText: TextView
    private lateinit var banpickTitle: TextView
    private lateinit var championSelectButton: Button // ✅ 추가
    private var countDownTimer: CountDownTimer? = null
    private var opponentJoined = false
    private var roomId: String = ""
    private var myTeam: String = "blue"
    private var currentPickIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.banpick_friend_champion)
        ensureSelectionsInit()

        // ✅ 1. intent 값 받아오는 구간
        roomId = intent.getStringExtra("roomCode") ?: return
        myTeam = intent.getStringExtra("team") ?: "blue"

        // ✅ 2. 타이머 설정 여부 받기 (여기에 추가!)
        isTimerEnabled = intent.getBooleanExtra("isTimerEnabled", true)

        // 타이머 설정 Firestore에서 가져오기 (추가)
        fetchTimerSetting {
            promptTeamNameIfNeeded()
            listenToTeamNameChanges()
        }

        // ✅ 방 코드 TextView에 표시
        val codeView = findViewById<TextView>(R.id.Code)
        findViewById<TextView>(R.id.Code).text = "방코드: $roomId"
        codeView.setTextColor(Color.parseColor("#6C6C6C"))

        findViewById<Button>(R.id.PageBack).setOnClickListener { finish() }

        banPickTimeText = findViewById(R.id.BanPickTime)
        banpickTitle = findViewById(R.id.BanpickTitle)
        championSelectButton = findViewById(R.id.Champion) // ✅ 버튼 초기화
        championSelectButton.isEnabled = false // ✅ 상대방 입장 전까지 비활성화

        findViewById<TextView>(R.id.BlueTeam).text = intent.getStringExtra("blue_team_name") ?: "블루팀"
        findViewById<TextView>(R.id.RedTeam).text = intent.getStringExtra("red_team_name") ?: "레드팀"

        banPickTimeText.visibility = TextView.GONE
        banpickTitle.text = "상대방을 기다리는 중..."

        checkOpponentJoined()
        listenToPickChanges()

        championSelectButton.setOnClickListener {
            if (!opponentJoined) {
                Toast.makeText(this, "상대방이 입장해야 챔피언을 선택할 수 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showChampionChoiceDialog()
        }

        findViewById<Button>(R.id.WhoWin).setOnClickListener {
            // 1) 방 문서에서 picks 맵을 통째로 가져온다
            val roomRef = FirebaseFirestore.getInstance()
                .collection("banpick_rooms")
                .document(roomId)
            roomRef.get()
                .addOnSuccessListener { doc ->
                    // 2) Firestore 에 저장된 nested map 꺼내기
                    val bluePicksMap = (doc.get("blueTeam.picks") as? Map<*, *>)?.mapKeys { it.key as String }?.mapValues { it.value as String }
                        ?: emptyMap()
                    val redPicksMap  = (doc.get("redTeam.picks")  as? Map<*, *>)?.mapKeys { it.key as String }?.mapValues { it.value as String }
                        ?: emptyMap()

                    // 3) 순서 고정된 key 리스트
                    val blueKeys = listOf("blue1","blue2","blue3","blue4","blue5")
                    val redKeys  = listOf("red1", "red2", "red3", "red4", "red5")

                    // 4) key 순서대로 champion ID 리스트 생성
                    val blueTeamPicks = blueKeys.mapNotNull { bluePicksMap[it] }
                    val redTeamPicks  = redKeys.mapNotNull  { redPicksMap[it]  }

                    // 5) 제대로 5개씩 뽑혔는지 체크
                    if (blueTeamPicks.size != 5 || redTeamPicks.size != 5) {
                        Toast.makeText(this, "밴픽이 완전히 끝난 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    // 6) 예측 화면으로 전달
                    val intent = Intent(this, BanpickFriendPredictionActivity::class.java).apply {
                        putStringArrayListExtra("blue_team", ArrayList(blueTeamPicks))
                        putStringArrayListExtra("red_team",  ArrayList(redTeamPicks))
                        putExtra("blue_team_name", findViewById<TextView>(R.id.BlueTeam).text.toString())
                        putExtra("red_team_name",  findViewById<TextView>(R.id.RedTeam).text.toString())
                    }
                    startActivity(intent)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "밴픽 정보를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun fetchTimerSetting(onComplete: () -> Unit) {
        FirebaseFirestore.getInstance().collection("banpick_rooms")
            .document(roomId)
            .get()
            .addOnSuccessListener { doc ->
                isTimerEnabled = doc.getBoolean("isTimerEnabled") ?: true
                onComplete()
            }
            .addOnFailureListener {
                isTimerEnabled = true
                onComplete()
            }
    }


    private fun checkOpponentJoined() {
        FirebaseFirestore.getInstance()
            .collection("banpick_rooms").document(roomId)
            .addSnapshotListener { snap, _ ->
                val blueMap = snap?.get("blueTeam") as? Map<*, *>
                val redMap  = snap?.get("redTeam") as? Map<*, *>
                val blue = blueMap?.get("uid") as? String
                val red  = redMap?.get("uid")  as? String
                val blueName = blueMap?.get("name") as? String
                val redName  = redMap?.get("name")  as? String

                // ✅ UID와 name 모두 있는 경우만 시작
                if (!opponentJoined && !blue.isNullOrEmpty() && !red.isNullOrEmpty()
                    && !blueName.isNullOrEmpty() && !redName.isNullOrEmpty()
                ) {
                    opponentJoined = true
                    championSelectButton.isEnabled = true

                    updatePickTitle()
                    findViewById<TextView>(R.id.Code).visibility = TextView.GONE
                    banPickTimeText.visibility = TextView.VISIBLE

                    if (isTimerEnabled) {
                        banPickTimeText.text = "30초"
                        startTimer(roomId)
                    } else {
                        banPickTimeText.text = "시간 무제한"
                    }

                    Toast.makeText(this, "밴픽을 시작합니다!", Toast.LENGTH_SHORT).show()
                }
            }
    }


    private fun listenToPickChanges() {
        FirebaseFirestore.getInstance().collection("banpick_rooms")
            .document(roomId)
            .addSnapshotListener { snapshot, _ ->
                currentPickIndex = (snapshot?.get("currentPickIndex") as? Long)?.toInt() ?: 0
                val bluePicks = (snapshot?.get("blueTeam") as? Map<*, *>)?.get("picks") as? Map<*, *> ?: emptyMap<Any?, Any?>()
                val redPicks = (snapshot?.get("redTeam") as? Map<*, *>)?.get("picks") as? Map<*, *> ?: emptyMap<Any?, Any?>()


                val allPicks = bluePicks + redPicks
                for ((key, champId) in allPicks) {
                    val champIdStr = champId as? String ?: continue
                    val resId = resources.getIdentifier(key.toString(), "id", packageName)
                    if (resId == 0) continue
                    val index = pickOrder.indexOf(resId)
                    if (index == -1 || BanpickChampionChoiceFriend.selectedChampions[index].isNotEmpty()) continue

                    val champ = BanpickChampionChoiceFriend.allChampions.find { it.id == champIdStr } ?: continue
                    val url = if (key.toString().contains("ban")) champ.iconUrl else champ.splashUrl ?: champ.iconUrl
                    if (!url.isNullOrBlank()) {
                        lifecycleScope.launch {
                            delay(100)
                            Picasso.get().load(url).into(findViewById<ImageView>(resId))
                        }
                    }
                    BanpickChampionChoiceFriend.selectedChampions[index] = champIdStr
                }

                updatePickTitle()

                val viewId = pickOrder.getOrNull(currentPickIndex) ?: return@addSnapshotListener
                val resName = resources.getResourceEntryName(viewId)

                // ✅ red5 픽이 완료됐을 때 밴픽 종료
                val red5Index = pickOrder.indexOf(R.id.red5)
                if (red5Index != -1 && BanpickChampionChoiceFriend.selectedChampions.getOrNull(red5Index)?.isNotBlank() == true) {
                    banpickTitle.text = "밴픽이 끝났습니다."
                    banPickTimeText.visibility = TextView.GONE
                    championSelectButton.isEnabled = false
                    return@addSnapshotListener
                }



                val isMyTurn = (resName.startsWith("blue") && myTeam == "blue") ||
                        (resName.startsWith("red") && myTeam == "red")

                if (isMyTurn && isTimerEnabled && opponentJoined) {
                    countDownTimer?.cancel()
                    banPickTimeText.text = "30초"
                    banPickTimeText.visibility = TextView.VISIBLE
                    startTimer(roomId)
                }
            }
    }

    private fun updatePickTitle() {
        for (id in pickOrder) {
            findViewById<ImageView>(id).background = ColorDrawable(Color.parseColor("#434343"))
        }

        val resId = pickOrder.getOrNull(currentPickIndex) ?: return
        val isBan = resources.getResourceEntryName(resId).contains("ban")
        val team = when {
            resources.getResourceEntryName(resId).startsWith("blue") -> "블루팀"
            resources.getResourceEntryName(resId).startsWith("red") -> "레드팀"
            else -> ""
        }

        banpickTitle.text = if (isBan) "$team 금지 챔피언 선택해주세요" else "$team 챔피언 선택해주세요"
        findViewById<ImageView>(resId).setBackgroundResource(
            if (isBan) R.drawable.banpick_ban_highlight else R.drawable.banpick_highlight
        )
    }

    private fun startTimer(roomId: String) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                banPickTimeText.text = "${millisUntilFinished / 1000}초"
            }

            override fun onFinish() {
                banPickTimeText.text = "0초"
                val viewId = pickOrder.getOrNull(currentPickIndex) ?: return
                val resName = resources.getResourceEntryName(viewId)

                val unused = BanpickChampionChoiceFriend.allChampions.filterNot {
                    BanpickChampionChoiceFriend.selectedChampions.contains(it.id)
                }

                if (unused.isNotEmpty()) {
                    val champ = unused.random()
                    val url = if (resName.contains("ban")) champ.iconUrl else champ.splashUrl ?: champ.iconUrl
                    if (!url.isNullOrBlank()) {
                        Picasso.get().load(url).into(findViewById<ImageView>(viewId))
                    }

                    BanpickChampionChoiceFriend.selectedChampions[currentPickIndex] = champ.id

                    val pickKey = getCurrentTurnKey()
                    val fieldPath = if (resName.startsWith("blue")) "blueTeam.picks.$pickKey"
                    else "redTeam.picks.$pickKey"

                    FirebaseFirestore.getInstance()
                        .collection("banpick_rooms")
                        .document(roomId)
                        .update(
                            mapOf(
                                fieldPath to champ.id,
                                "currentPickIndex" to currentPickIndex + 1
                            )
                        )
                }
            }
        }.start()
    }

    private fun processChampionSelection(championId: String, splashUrl: String?, iconUrl: String?) {
        if (championId.isNotEmpty() && currentPickIndex < pickOrder.size) {
            val viewId = pickOrder[currentPickIndex]
            val resName = resources.getResourceEntryName(viewId)
            val url = if (resName.contains("ban")) iconUrl else splashUrl ?: iconUrl

            if (!url.isNullOrBlank()) {
                Picasso.get().load(url).into(findViewById<ImageView>(viewId))
            }

            BanpickChampionChoiceFriend.selectedChampions[currentPickIndex] = championId

            val pickKey = getCurrentTurnKey()
            val fieldPath = if (resName.startsWith("blue")) "blueTeam.picks.$pickKey"
            else "redTeam.picks.$pickKey"

            FirebaseFirestore.getInstance()
                .collection("banpick_rooms")
                .document(roomId)
                .update(
                    mapOf(
                        fieldPath to championId,
                        "currentPickIndex" to currentPickIndex + 1
                    )
                )
        }
    }

    private fun showChampionChoiceDialog() {
        val dialog = BanpickChampionChoiceFriend.newInstance(
            roomId = roomId,
            currentTurn = getCurrentTurnKey(),
            myTeam = myTeam,
            onChampionSelected = { champ ->
                processChampionSelection(champ.id, champ.splashUrl, champ.iconUrl)
            }
        )
        dialog.show(supportFragmentManager, "FriendChampionDialog")
    }

    private fun getCurrentTurnKey(): String {
        val resId = pickOrder.getOrNull(currentPickIndex) ?: return "unknown"
        return resources.getResourceEntryName(resId)
    }

    private fun ensureSelectionsInit() {
        if (BanpickChampionChoiceFriend.selectedChampions.size != pickOrder.size) {
            BanpickChampionChoiceFriend.selectedChampions = MutableList(pickOrder.size) { "" }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    private fun promptTeamNameIfNeeded() {
        val db = FirebaseFirestore.getInstance()
        val teamPath = if (myTeam == "blue") "blueTeam.name" else "redTeam.name"
        val teamLabel = if (myTeam == "blue") "블루팀" else "레드팀"

        db.collection("banpick_rooms").document(roomId).get()
            .addOnSuccessListener { doc ->
                val name = (doc.get(if (myTeam == "blue") "blueTeam" else "redTeam") as? Map<*, *>)?.get("name") as? String

                if (name.isNullOrEmpty()) {
                    val dialogView = layoutInflater.inflate(R.layout.dialog_team_name, null)
                    val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
                    val editText = dialogView.findViewById<EditText>(R.id.dialogEditText)

                    // ✅ 스타일 적용
                    titleView.text = "$teamLabel 이름 설정"
                    titleView.textSize = 16f
                    titleView.textAlignment = TextView.TEXT_ALIGNMENT_CENTER

                    editText.hint = "$teamLabel 이름을 입력해주세요"
                    editText.textSize = 14f

                    // ✅ AlertDialog 생성 및 버튼 스타일 수동 적용
                    val alertDialog = AlertDialog.Builder(ContextThemeWrapper(this, R.style.CustomAlertDialog))
                        .setView(dialogView)
                        .setCancelable(false)
                        .setPositiveButton("확인") { _, _ ->
                            val inputName = editText.text.toString().ifBlank { teamLabel }
                            db.collection("banpick_rooms").document(roomId)
                                .update(teamPath, inputName)
                            updateTeamNameUI(teamLabel, inputName)
                            checkStartCondition()
                        }
                        .create()

                    alertDialog.show()

                    // ✅ 버튼 색상 수동 적용
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                        setTextColor(Color.BLACK)
                        setBackgroundColor(Color.parseColor("#E7EBED"))
                    }

                } else {
                    updateTeamNameUI(teamLabel, name)
                    checkStartCondition()
                }
            }
    }


    private fun updateTeamNameUI(teamLabel: String, name: String) {
        if (teamLabel == "블루팀") {
            findViewById<TextView>(R.id.BlueTeam).text = name
        } else {
            findViewById<TextView>(R.id.RedTeam).text = name
        }
    }

    private fun checkStartCondition() {
        val db = FirebaseFirestore.getInstance()
        db.collection("banpick_rooms").document(roomId).get()
            .addOnSuccessListener { doc ->
                val blueName = (doc.get("blueTeam") as? Map<*, *>)?.get("name") as? String
                val redName  = (doc.get("redTeam")  as? Map<*, *>)?.get("name") as? String
                val blueUid  = (doc.get("blueTeam") as? Map<*, *>)?.get("uid") as? String
                val redUid   = (doc.get("redTeam")  as? Map<*, *>)?.get("uid") as? String

                if (!blueName.isNullOrEmpty() && !redName.isNullOrEmpty()
                    && !blueUid.isNullOrEmpty() && !redUid.isNullOrEmpty()
                ) {
                    updatePickTitle()
                    banPickTimeText.visibility = TextView.VISIBLE
                    if (isTimerEnabled) {
                        banPickTimeText.text = "30초"
                        startTimer(roomId)
                    } else {
                        banPickTimeText.text = "시간 무제한"
                    }
                    championSelectButton.isEnabled = true
                    findViewById<TextView>(R.id.Code).visibility = TextView.GONE
                    Toast.makeText(this, "밴픽을 시작합니다!", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun listenToTeamNameChanges() {
        FirebaseFirestore.getInstance().collection("banpick_rooms")
            .document(roomId)
            .addSnapshotListener { snapshot, _ ->
                val blueName = (snapshot?.get("blueTeam") as? Map<*, *>)?.get("name") as? String
                val redName  = (snapshot?.get("redTeam")  as? Map<*, *>)?.get("name") as? String

                blueName?.let {
                    findViewById<TextView>(R.id.BlueTeam).text = it
                }

                redName?.let {
                    findViewById<TextView>(R.id.RedTeam).text = it
                }
            }
    }


}
