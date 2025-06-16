package com.example.yumi2

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.yumi2.model.ChampionData
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso

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

        roomId = intent.getStringExtra("roomCode") ?: return
        myTeam = intent.getStringExtra("team") ?: "blue"

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

            val viewId = pickOrder.getOrNull(currentPickIndex) ?: return@setOnClickListener
            val resName = resources.getResourceEntryName(viewId)
            val isMyTurn = (resName.startsWith("blue") && myTeam == "blue") ||
                    (resName.startsWith("red") && myTeam == "red")

            if (!isMyTurn) {
                Toast.makeText(this, "상대팀 차례입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showChampionChoiceDialog()
        }
    }

    private fun checkOpponentJoined() {
        FirebaseFirestore.getInstance()
            .collection("banpick_rooms").document(roomId)
            .addSnapshotListener { snap, _ ->
                val blue = (snap?.get("blueTeam") as? Map<*, *>)?.get("uid") as? String
                val red = (snap?.get("redTeam") as? Map<*, *>)?.get("uid") as? String

                if (!opponentJoined && !blue.isNullOrEmpty() && !red.isNullOrEmpty()) {
                    opponentJoined = true
                    championSelectButton.isEnabled = true // ✅ 버튼 활성화
                    Toast.makeText(this, "상대팀이 입장하였습니다. 벤픽을 시작합니다.", Toast.LENGTH_SHORT).show()
                    updatePickTitle()
                    banPickTimeText.visibility = TextView.VISIBLE
                    banPickTimeText.text = "20초"
                    startTimer(roomId)
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
                        Picasso.get().load(url).into(findViewById<ImageView>(resId))
                    }
                    BanpickChampionChoiceFriend.selectedChampions[index] = champIdStr
                }

                updatePickTitle()

                val viewId = pickOrder.getOrNull(currentPickIndex) ?: return@addSnapshotListener
                val resName = resources.getResourceEntryName(viewId)
                val isMyTurn = (resName.startsWith("blue") && myTeam == "blue") ||
                        (resName.startsWith("red") && myTeam == "red")

                if (isMyTurn && isTimerEnabled && opponentJoined) {
                    countDownTimer?.cancel()
                    banPickTimeText.text = "60초"
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
        countDownTimer = object : CountDownTimer(60000, 1000) {
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
}
