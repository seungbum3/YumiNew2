package com.example.yumi2.adapter

import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.yumi2.NameSearchMainActivity
import com.example.yumi2.R
import com.example.yumi2.model.MatchHistoryItem
import com.example.yumi2.model.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

class MatchHistoryAdapter(
    private var items: List<MatchHistoryItem>,
    private val mySummonerName: String
) : RecyclerView.Adapter<MatchHistoryAdapter.MatchHistoryViewHolder>() {

    // var로 선언하여 최신 버전 업데이트 가능 (fallback 값)
    private var latestVersion: String = "15.10.1"

    private val idToKeyMap = mutableMapOf<String, String>()

    private val championEngNameOverride = mapOf(
        "오로라"    to "Aurora",
        "암베사"    to "Ambessa",
        "흐웨이"    to "Hwei",
        "멜"       to "Mel",
        "닐라"     to "Nilah",
        "스몰더"    to "Smolder",
        "제리"     to "Zeri"
    )

    private val summonerSpellMap = mapOf(
        1 to "SummonerBoost", 3 to "SummonerExhaust", 4 to "SummonerFlash",
        6 to "SummonerHaste", 7 to "SummonerHeal", 11 to "SummonerSmite",
        12 to "SummonerTeleport", 14 to "SummonerDot", 21 to "SummonerBarrier", 32 to "SummonerSnowball"
    )

    init {
        // 앱 구동 시 최신 버전 → champion.json 순으로 가져와 매핑 구축
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1) versions.json 호출
                val versionsJson = URL("https://ddragon.leagueoflegends.com/api/versions.json")
                    .readText()
                latestVersion = JSONArray(versionsJson).getString(0)
                // 2) champion.json 호출
                val champJsonText = URL(
                    "https://ddragon.leagueoflegends.com/cdn/" +
                            "$latestVersion/data/en_US/champion.json"
                ).readText()
                val dataObj = JSONObject(champJsonText).getJSONObject("data")
                dataObj.keys().forEach { engKey ->
                    val champObj = dataObj.getJSONObject(engKey)
                    val id = champObj.getString("key") // e.g. "266"
                    idToKeyMap[id] = engKey            // e.g. "Aatrox"
                }
                // 3) 매핑 준비 완료 → UI 갱신
                withContext(Dispatchers.Main) {
                    notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Log.w("MatchHistoryAdapter", "champion.json 매핑 실패", e)
            }
        }
    }

    inner class MatchHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val matchItemRoot: ConstraintLayout = itemView.findViewById(R.id.matchItemRoot)
        val ivChampionIcon: ImageView = itemView.findViewById(R.id.ivChampionIcon)
        val tvMatchResult: TextView = itemView.findViewById(R.id.tvMatchResult)
        val tvQueueType: TextView = itemView.findViewById(R.id.tvQueueType)
        val tvKDA: TextView = itemView.findViewById(R.id.tvKDA)
        val tvKDADetail: TextView = itemView.findViewById(R.id.tvKDADetail)
        val ivSummonerSpell1: ImageView = itemView.findViewById(R.id.ivSummonerSpell1)
        val ivSummonerSpell2: ImageView = itemView.findViewById(R.id.ivSummonerSpell2)
        val downBtn: ImageView = itemView.findViewById(R.id.downBtn)
        val itemContainer: LinearLayout = itemView.findViewById(R.id.itemContainer)
        val expandedLayout: LinearLayout = itemView.findViewById(R.id.expandedLayout)
        val redTeamContainer: LinearLayout = itemView.findViewById(R.id.redTeamContainer)
        val blueTeamContainer: LinearLayout = itemView.findViewById(R.id.blueTeamContainer)
        val ivItemList = listOf<ImageView>(
            itemView.findViewById(R.id.ivItem0),
            itemView.findViewById(R.id.ivItem1),
            itemView.findViewById(R.id.ivItem2),
            itemView.findViewById(R.id.ivItem3),
            itemView.findViewById(R.id.ivItem4),
            itemView.findViewById(R.id.ivItem5)
        )
        val tvCS: TextView = itemView.findViewById(R.id.tvCS)
        val tvGold: TextView = itemView.findViewById(R.id.tvGold)
        val tvGameTime: TextView = itemView.findViewById(R.id.tvGameTime)
        val tvTimeAgo: TextView = itemView.findViewById(R.id.tvTimeAgo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.match_history_item, parent, false)
        return MatchHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MatchHistoryViewHolder, position: Int) {
        val match = items[position]

        // ① championId(숫자) → engKey → 예외 매핑 → fallback
        val engKey = idToKeyMap[match.championId.toString()]
            ?: championEngNameOverride[match.championEngName]
            ?: match.championEngName

        // ② Glide 로드
        val iconUrl = "https://ddragon.leagueoflegends.com/cdn/" +
                "$latestVersion/img/champion/$engKey.png"
        Glide.with(holder.itemView)
            .load(iconUrl)
            .error(R.drawable.error_image)
            .into(holder.ivChampionIcon)

        holder.tvQueueType.text = getQueueTypeLabel(match.queueId)

        holder.tvKDADetail.text = match.kdaString

        val deaths = match.kdaString.replace(" ", "").split("/").getOrNull(1)?.toIntOrNull() ?: -1

        if (deaths == 0) {
            holder.tvKDA.text = "PERFECT"
            holder.tvKDA.setTextColor(Color.parseColor("#B41500"))
        } else {
            holder.tvKDA.text = match.kdaRatioString

            val kdaValue = match.kdaRatioString.toFloatOrNull() ?: 0f
            val kdaColor = when {
                kdaValue < 2.0f -> "#A0A0A0"
                kdaValue < 3.0f -> "#20C525"
                kdaValue < 4.0f -> "#3C4EC3"
                else -> "#B41500"
            }
            holder.tvKDA.setTextColor(Color.parseColor(kdaColor))
            Log.d("KDA_DEBUG", "kdaString: ${match.kdaString}, parsed deaths: $deaths")
        }

        holder.tvMatchResult.text = if (match.isWin) "승리" else "패배"
        holder.tvMatchResult.setTextColor(if (match.isWin) Color.BLUE else Color.RED)
        holder.matchItemRoot.setBackgroundColor(
            if (match.isWin) Color.parseColor("#C8E8F4") else Color.parseColor("#F4C8C8")
        )

        // 소환사 주문 이미지
        summonerSpellMap[match.summonerSpell1]?.let {
            Glide.with(holder.itemView)
                .load("https://ddragon.leagueoflegends.com/cdn/$latestVersion/img/spell/$it.png")
                .error(R.drawable.error_image)
                .into(holder.ivSummonerSpell1)
        }
        summonerSpellMap[match.summonerSpell2]?.let {
            Glide.with(holder.itemView)
                .load("https://ddragon.leagueoflegends.com/cdn/$latestVersion/img/spell/$it.png")
                .error(R.drawable.error_image)
                .into(holder.ivSummonerSpell2)
        }

        // 아이템 6개 이미지
        match.itemIds.take(6).forEachIndexed { i, itemId ->
            val itemUrl = "https://ddragon.leagueoflegends.com/cdn/$latestVersion/img/item/$itemId.png"
            Glide.with(holder.itemView)
                .load(itemUrl)
                .error(R.drawable.item_error)
                .into(holder.ivItemList[i])
        }

        // CS, 골드, 게임 시간, 시간 경과
        val csPerMin = if (match.gameDuration > 0) match.cs / (match.gameDuration / 60.0) else 0.0
        holder.tvCS.text = "CS ${match.cs} (${String.format("%.1f", csPerMin)})"
        holder.tvGold.text = "골드 ${match.gold}"
        holder.tvGameTime.text = "${match.gameDuration / 60}분 ${match.gameDuration % 60}초"

        val elapsed = System.currentTimeMillis() - match.gameCreation
        val elapsedMin = elapsed / 1000 / 60
        val elapsedHour = elapsedMin / 60
        holder.tvTimeAgo.text = when {
            elapsedHour >= 24 -> "${elapsedHour / 24}일 전"
            elapsedHour > 0 -> "${elapsedHour}시간 전"
            elapsedMin > 0 -> "${elapsedMin}분 전"
            else -> "방금 전"
        }

        // 펼침 레이아웃 토글
        holder.itemContainer.setOnClickListener {
            holder.expandedLayout.visibility =
                if (holder.expandedLayout.visibility == View.GONE) View.VISIBLE else View.GONE
        }

        // 팀 목록 설정
        setTeamList(holder.redTeamContainer, match.redTeamParticipants)
        setTeamList(holder.blueTeamContainer, match.blueTeamParticipants)

        // 팀 승패 헤더
        val redHeader = holder.itemView.findViewById<TextView>(R.id.tvRedTeamHeader)
        val blueHeader = holder.itemView.findViewById<TextView>(R.id.tvBlueTeamHeader)
        val myIsRed = match.redTeamParticipants.any { it.summonerName.equals(mySummonerName, true) }

        if (myIsRed) {
            redHeader.text = if (match.isWin) "승리 (레드팀)" else "패배 (레드팀)"
            redHeader.setTextColor(if (match.isWin) Color.BLUE else Color.RED)
            blueHeader.text = if (match.isWin) "패배 (블루팀)" else "승리 (블루팀)"
            blueHeader.setTextColor(if (match.isWin) Color.RED else Color.BLUE)
        } else {
            blueHeader.text = if (match.isWin) "승리 (블루팀)" else "패배 (블루팀)"
            blueHeader.setTextColor(if (match.isWin) Color.BLUE else Color.RED)
            redHeader.text = if (match.isWin) "패배 (레드팀)" else "승리 (레드팀)"
            redHeader.setTextColor(if (match.isWin) Color.RED else Color.BLUE)
        }
    }

    private fun getQueueTypeLabel(queueId: Int?): String {
        return when (queueId) {
            420 -> "솔로랭크"
            440 -> "자유랭크"
            430, 400, 490, 700 -> "일반"     // ← 일반 큐 타입 추가
            450 -> "칼바람"
            830, 840, 850 -> "봇전"
            900 -> "우르프"
            1900 -> "돌격 넥서스"
            null -> "알 수 없음"
            else -> "기타"
        }
    }

    private fun setTeamList(container: LinearLayout, players: List<Player>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)

        for (p in players) {
            Log.d("DebugPlayer", "name=${p.summonerName}, champ=${p.championEngName}")
            val view = inflater.inflate(R.layout.player_item, container, false)

            val backgroundView = view.findViewById<View>(R.id.backgroundView)
            val bgColor = if (p.isWin)
                Color.parseColor("#C8E8F4")
            else
                Color.parseColor("#F4C8C8")
            backgroundView.setBackgroundColor(bgColor)

            val engKey = idToKeyMap[p.championId.toString()]
                ?: championEngNameOverride[p.championEngName]
                ?: p.championEngName

            val iconUrl = "https://ddragon.leagueoflegends.com/cdn/$latestVersion/img/champion/$engKey.png"

            Glide.with(view.context)
                .load(iconUrl)
                .error(R.drawable.error_image)
                .into(view.findViewById<ImageView>(R.id.ivChampionIcon))

            val nameView = view.findViewById<TextView>(R.id.tvSummonerName)
            val fullName = if (p.gameName.isNotBlank() && p.tagLine.isNotBlank())
                "${p.gameName}#${p.tagLine}" else p.summonerName
            nameView.text = if (fullName.isBlank()) "Unknown" else fullName

            // ✅ 클릭 시 해당 소환사 상세로 이동
            nameView.setOnClickListener {
                val context = it.context
                val intent = Intent(context, NameSearchMainActivity::class.java).apply {
                    putExtra("gameName", p.gameName)
                    putExtra("tagLine", p.tagLine)
                }
                context.startActivity(intent)
            }

            view.findViewById<TextView>(R.id.tvKda).text = "KDA ${p.kills}/${p.deaths}/${p.assists}"
            view.findViewById<TextView>(R.id.tvCs).text = "CS ${p.cs} (${String.format("%.1f", p.csPerMin)})"
            view.findViewById<TextView>(R.id.tvGold).text = "골드 ${p.gold}"

            summonerSpellMap[p.spell1Id]?.let {
                Glide.with(view.context)
                    .load("https://ddragon.leagueoflegends.com/cdn/$latestVersion/img/spell/$it.png")
                    .error(R.drawable.error_image)
                    .into(view.findViewById(R.id.ivSpell1))
            }
            summonerSpellMap[p.spell2Id]?.let {
                Glide.with(view.context)
                    .load("https://ddragon.leagueoflegends.com/cdn/$latestVersion/img/spell/$it.png")
                    .error(R.drawable.error_image)
                    .into(view.findViewById(R.id.ivSpell2))
            }

            val itemIds = p.itemIds
            val itemViews = listOf(
                view.findViewById<ImageView>(R.id.ivItem0),
                view.findViewById<ImageView>(R.id.ivItem1),
                view.findViewById<ImageView>(R.id.ivItem2),
                view.findViewById<ImageView>(R.id.ivItem3),
                view.findViewById<ImageView>(R.id.ivItem4),
                view.findViewById<ImageView>(R.id.ivItem5)
            )

            for (i in 0 until 6) {
                val itemId = itemIds.getOrNull(i) ?: 0
                Glide.with(view.context)
                    .load("https://ddragon.leagueoflegends.com/cdn/$latestVersion/img/item/$itemId.png")
                    .error(R.drawable.item_error)
                    .into(itemViews[i])
            }

            container.addView(view)
        }
    }


    override fun getItemCount(): Int = items.size

    fun setItems(newItems: List<MatchHistoryItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    // 최신 버전을 갱신할 수 있도록 공개 함수 추가
    fun setLatestVersion(version: String) {
        latestVersion = version
    }
}
