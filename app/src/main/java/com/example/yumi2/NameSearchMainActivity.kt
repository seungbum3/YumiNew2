package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.yumi2.adapter.ChampionStatsAdapter
import com.example.yumi2.adapter.MatchHistoryAdapter
import com.example.yumi2.model.MatchHistoryItem
import com.example.yumi2.model.Participant
import com.example.yumi2.model.Player
import com.example.yumi2.model.SummonerResponse
import com.example.yumi2.viewmodel.SummonerViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NameSearchMainActivity : AppCompatActivity() {

    private val viewModel: SummonerViewModel by viewModels()

    // 매치 히스토리 어댑터
    private lateinit var matchHistoryAdapter: MatchHistoryAdapter

    // 챔피언 통계 어댑터
    private lateinit var championStatsAdapter: ChampionStatsAdapter

    private var isFavorite: Boolean = false
    private lateinit var nameFavoriteButton: Button

    private lateinit var headerScroll: HorizontalScrollView
    private lateinit var bodyScroll: HorizontalScrollView

    // 소환사 puuid와 별도의 큐 변수:
    // - currentQueueStats : 상단 챔피언 통계 관련
    // - currentQueueRecent : 하단 매치 히스토리 관련
    private var currentPuuid: String? = null
    private var currentQueueStats: Int? = 420
    private var currentQueueRecent: Int? = 420

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.name_search_main)

        // ─────────────────────────────────────
        // RecyclerView 설정 (매치 히스토리)
        // ─────────────────────────────────────

        val gameName = intent.getStringExtra("gameName") ?: ""
        val tagLine = intent.getStringExtra("tagLine") ?: ""

        val fullSummonerName = "$gameName#$tagLine"
        matchHistoryAdapter = MatchHistoryAdapter(emptyList(), fullSummonerName)

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (gameName.isNotBlank() && tagLine.isNotBlank()) {
            viewModel.searchSummoner(gameName, tagLine, currentUid)
        }


        val recyclerMatchHistory = findViewById<RecyclerView>(R.id.recyclerMatchHistory)
        recyclerMatchHistory.layoutManager = LinearLayoutManager(this)
        recyclerMatchHistory.adapter = matchHistoryAdapter

        // ─────────────────────────────────────
        // 스크롤뷰 설정 (Header & Body 동기화)
        // ─────────────────────────────────────
        headerScroll = findViewById(R.id.headerScroll)
        bodyScroll = findViewById(R.id.bodyScroll)
        headerScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            bodyScroll.scrollTo(scrollX, 0)
        }
        bodyScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            headerScroll.scrollTo(scrollX, 0)
        }

        // ─────────────────────────────────────
        // 챔피언 통계 RecyclerView (상단)
        // ─────────────────────────────────────
        val championStatsRecyclerView = findViewById<RecyclerView>(R.id.recyclerChampionStats)
        championStatsRecyclerView.layoutManager = LinearLayoutManager(this)
        championStatsAdapter = ChampionStatsAdapter(emptyList())
        championStatsRecyclerView.adapter = championStatsAdapter

        // 스크롤 끝에 도달하면 다음 4개 데이터를 불러옴
        championStatsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val layoutManager = rv.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 1) {
                    viewModel.loadNextPage()
                }
            }
        })

        // ─────────────────────────────────────
        // 상단 버튼 (챔피언 통계용)
        // ─────────────────────────────────────
        // 기존 XML의 버튼 id:btnSolo, btnFlex
        val btnSoloTop = findViewById<Button>(R.id.btnSolo)
        val btnFlexTop = findViewById<Button>(R.id.btnFlex)

        btnSoloTop.setOnClickListener {
            currentQueueStats = 420
            updateTopButtonUI(R.id.btnSolo) // 🔹 추가
            viewModel.resetStats()
            currentPuuid?.let { puuid ->
                viewModel.loadTop3ChampionStatsQuick(puuid, 420)
            }
        }

        btnFlexTop.setOnClickListener {
            currentQueueStats = 440
            updateTopButtonUI(R.id.btnFlex) // 🔹 추가
            viewModel.resetStats()
            currentPuuid?.let { puuid ->
                viewModel.loadTop3ChampionStatsQuick(puuid, 440)
            }
        }




        // ─────────────────────────────────────
        // 하단 버튼 (매치 히스토리용)
        // ─────────────────────────────────────
        // 기존 XML의 버튼 id: btnAll_bottom, btnSolo_bottom, btnFlex_bottom, btnGeneral_bottom, btnKall_bottom
        val btnAllBottom = findViewById<Button>(R.id.btnAll_bottom)
        val btnSoloBottom = findViewById<Button>(R.id.btnSolo_bottom)
        val btnFlexBottom = findViewById<Button>(R.id.btnFlex_bottom)
        val btnKallBottom = findViewById<Button>(R.id.btnKall_bottom)

        btnAllBottom.setOnClickListener {
            currentQueueRecent = null
            currentPuuid?.let { puuid ->
                viewModel.loadRecentMatches(puuid, currentQueueRecent)
            }
            updateBottomButtonUI(R.id.btnAll_bottom)
        }

        btnSoloBottom.setOnClickListener {
            currentQueueRecent = 420
            currentPuuid?.let { puuid ->
                viewModel.loadRecentMatches(puuid, currentQueueRecent)
            }
            updateBottomButtonUI(R.id.btnSolo_bottom)
        }
        btnFlexBottom.setOnClickListener {
            currentQueueRecent = 440
            currentPuuid?.let { puuid ->
                viewModel.loadRecentMatches(puuid, currentQueueRecent)
            }
            updateBottomButtonUI(R.id.btnFlex_bottom)
        }

        btnKallBottom.setOnClickListener {
            currentQueueRecent = 450
            currentPuuid?.let { puuid ->
                viewModel.loadRecentMatches(puuid, currentQueueRecent)
            }
            updateBottomButtonUI(R.id.btnKall_bottom)
        }

        // ─────────────────────────────────────
        // 기타 UI 요소 및 소환사 정보 처리
        // ─────────────────────────────────────

        val summonerNameText = findViewById<TextView>(R.id.summonerName)
        val summonerRankText = findViewById<TextView>(R.id.summonerRank)
        val summonerIcon = findViewById<ImageView>(R.id.summonerIcon)
        val summonerLevelText = findViewById<TextView>(R.id.summonerLevel)
        val backButton = findViewById<Button>(R.id.PageBack)

        val rankCard = findViewById<View>(R.id.rankCard)
        val rankType = findViewById<TextView>(R.id.rankType)
        val rankTier = findViewById<TextView>(R.id.rankTier)
        val rankLP = findViewById<TextView>(R.id.rankLP)
        val rankWinLoss = findViewById<TextView>(R.id.rankWinLoss)
        val rankTierImage = findViewById<ImageView>(R.id.rankTierImage)

        val rankCard1 = findViewById<View>(R.id.rankCard1)
        val rankType1 = findViewById<TextView>(R.id.rankType1)
        val rankTier1 = findViewById<TextView>(R.id.rankTier1)
        val rankLP1 = findViewById<TextView>(R.id.rankLP1)
        val rankWinLoss1 = findViewById<TextView>(R.id.rankWinLoss1)
        val rankTierImage1 = findViewById<ImageView>(R.id.rankTierImage1)

        // 즐겨찾기 버튼 설정
        nameFavoriteButton = findViewById(R.id.NameFavorite)
        nameFavoriteButton.setOnClickListener {
            val favoriteNickname = summonerNameText.text.toString()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            if (uid.isEmpty()) return@setOnClickListener

            if (!isFavorite) {
                nameFavoriteButton.setBackgroundResource(R.drawable.name_favorite_save)
                addFavoriteToFirestore(favoriteNickname, uid)
                isFavorite = true
            } else {
                nameFavoriteButton.setBackgroundResource(R.drawable.name_favorite)
                removeFavoriteFromFirestore(favoriteNickname, uid)
                isFavorite = false
            }
        }

        backButton.setOnClickListener {
            startActivity(Intent(this, NameSearchActivity::class.java))
        }

        summonerNameText.text = "$gameName#$tagLine"

        // 소환사 정보 검색
        viewModel.searchSummoner(gameName, tagLine, FirebaseAuth.getInstance().currentUser?.uid ?: "")

        // ─────────────────────────────────────
        // 소환사 정보 관찰
        // ─────────────────────────────────────
        lifecycleScope.launch {
            viewModel.summonerInfo.collect { summoner: SummonerResponse? ->
                Log.d("NameSearchMainActivity", "수집된 summonerInfo: $summoner")
                if (summoner != null) {
                    val latestVersion = withContext(Dispatchers.IO) {
                        viewModel.getLatestLolVersion() ?: "14.1.1"
                    }
                    val iconUrl = "https://ddragon.leagueoflegends.com/cdn/$latestVersion/img/profileicon/${summoner.profileIconId}.png"

                    withContext(Dispatchers.Main) {
                        val displayGameName = summoner.gameName ?: gameName
                        val displayTagLine = summoner.tagLine ?: tagLine

                        summonerNameText.text = "$displayGameName#$displayTagLine"
                        summonerRankText.text = "Puuid: ${summoner.puuid}"
                        summonerLevelText.text = "레벨: ${summoner.summonerLevel}"

                        Glide.with(this@NameSearchMainActivity)
                            .load(iconUrl)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .error(R.drawable.error_image)
                            .into(summonerIcon)

                        // 랭크 카드 UI 처리
                        var hasRankData = false
                        if (summoner.soloRank != null) {
                            val rank = summoner.soloRank!!
                            val totalGames = rank.wins + rank.losses
                            rankType.text = "개인/2인전"
                            rankTier.text = "${rank.tier} ${rank.rank}"
                            rankLP.text = "${rank.leaguePoints} LP"
                            rankWinLoss.text = "${totalGames}전 ${rank.wins}승 ${rank.losses}패"
                            rankTierImage.setImageResource(getTierDrawable(rank.tier))
                            rankTier.setTextColor(getTierColor(rank.tier))
                            rankCard.visibility = View.VISIBLE
                            hasRankData = true
                        } else {
                            rankType.text = "개인/2인전"
                            rankTier.text = "Unranked"
                            rankLP.text = "-"
                            rankWinLoss.text = "?승 ?패"
                            rankTierImage.setImageResource(R.drawable.unranked)
                            rankTier.setTextColor(getTierColor("unranked"))
                            rankCard.visibility = View.VISIBLE
                        }

                        if (summoner.flexRank != null) {
                            val rank = summoner.flexRank!!
                            val totalGames = rank.wins + rank.losses
                            rankType1.text = "자유 5대5 대전"
                            rankTier1.text = "${rank.tier} ${rank.rank}"
                            rankLP1.text = "${rank.leaguePoints} LP"
                            rankWinLoss1.text = "${totalGames}전 ${rank.wins}승 ${rank.losses}패"
                            rankTierImage1.setImageResource(getTierDrawable(rank.tier))
                            rankTier1.setTextColor(getTierColor(rank.tier))
                            rankCard1.visibility = View.VISIBLE
                            hasRankData = true
                        } else {
                            rankType1.text = "자유 5대5 대전"
                            rankTier1.text = "Unranked"
                            rankLP1.text = "-"
                            rankWinLoss1.text = "?승 ?패"
                            rankTierImage1.setImageResource(R.drawable.unranked)
                            rankTier1.setTextColor(getTierColor("unranked"))
                            rankCard1.visibility = View.VISIBLE
                        }

                        if (!hasRankData) {
                            rankCard.visibility = View.VISIBLE
                            rankCard1.visibility = View.VISIBLE
                        }

                        // 즐겨찾기 상태 체크
                        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        checkFavoriteStatus("$displayGameName#$displayTagLine", currentUid)
                    }

                    currentPuuid = summoner.puuid

                    // 🔹 상단 통계: 솔로랭크 top3 챔피언 빠르게 불러오기
                    currentQueueStats = 420
                    updateTopButtonUI(R.id.btnSolo)  // 선택된 버튼 UI 반영
                    viewModel.resetStats()
                    viewModel.loadTop3ChampionStatsQuick(summoner.puuid, 420)

                    // 🔹 하단 매치: 전체 매치 히스토리
                    currentQueueRecent = null
                    updateBottomButtonUI(R.id.btnAll_bottom) // 🔹 버튼 UI 업데이트
                    viewModel.loadRecentMatches(summoner.puuid, currentQueueRecent)

                } else {
                    withContext(Dispatchers.Main) {
                        summonerNameText.text = "소환사 정보를 불러올 수 없습니다."
                    }
                }
            }
        }


        // ─────────────────────────────────────
        // LiveData/StateFlow 관찰
        // ─────────────────────────────────────
        lifecycleScope.launchWhenStarted {
            viewModel.championStats.collect { stats ->
                championStatsAdapter.setItems(stats)
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.matchHistoryList.collect { matchList ->
                Log.d("NameSearchMainActivity", "matchHistoryList.collect => size=${matchList.size}")
                matchHistoryAdapter.setItems(matchList)
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.recentMatchesStats.collect { stats ->
                if (stats != null) {
                    // 매치 히스토리 리스트 업데이트
                    matchHistoryAdapter.setItems(stats.matches)

                    // 통합 통계 업데이트
                    val totalMatches = stats.matches.size
                    val totalWins = stats.totalWins
                    val totalLosses = stats.totalLosses
                    val winRate = if (totalMatches > 0) (totalWins * 100 / totalMatches) else 0

                    val tvWinRate = findViewById<TextView>(R.id.tvWinRate)
                    val tvTotalGames = findViewById<TextView>(R.id.tvTotalGames)
                    val tvWins = findViewById<TextView>(R.id.tvWins)
                    val tvLosses = findViewById<TextView>(R.id.tvLosses)

                    tvWinRate.text = "승률 ${winRate}%"
                    tvTotalGames.text = "${totalMatches}전 "
                    tvWins.text = "승 $totalWins"
                    tvLosses.text = " 패 $totalLosses"

                    val tvScore = findViewById<TextView>(R.id.tvScore)
                    val averageKDA = String.format("%.2f", stats.averageKDA)
                    tvScore.text = "평점 $averageKDA"

                    val tvKills = findViewById<TextView>(R.id.tvKills)
                    val tvDeaths = findViewById<TextView>(R.id.tvDeaths)
                    val tvAssists = findViewById<TextView>(R.id.tvAssists)

                    val killsStr = String.format("%.1f", stats.averageKills)
                    val deathsStr = String.format("%.1f", stats.averageDeaths)
                    val assistsStr = String.format("%.1f", stats.averageAssists)

                    tvKills.text = killsStr
                    tvDeaths.text = deathsStr
                    tvAssists.text = assistsStr
                }
            }
        }

        // 디버그용 테스트 함수 호출 (필요 시 주석 처리)
        testParticipantAndPlayer()
    }

    private fun updateBottomButtonUI(selectedButtonId: Int) {
        val buttons = listOf(
            R.id.btnAll_bottom,
            R.id.btnSolo_bottom,
            R.id.btnFlex_bottom,
            R.id.btnKall_bottom
        )

        for (id in buttons) {
            val button = findViewById<Button>(id)
            if (id == selectedButtonId) {
                button.setBackgroundResource(R.drawable.rounded_button_selected) // 선택된 버튼 스타일
            } else {
                button.setBackgroundResource(R.drawable.rounded_button) // 기본 버튼 스타일
            }
        }
    }


    private fun callGetMatchDetailWithNames(matchId: String) {
        val data = hashMapOf("matchId" to matchId)
        FirebaseFunctions.getInstance()
            .getHttpsCallable("getMatchDetailWithNames")
            .call(data)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val result = task.result?.getData()
                    Log.d("CloudFunctionResponse", "응답 데이터: $result")
                } else {
                    Log.e("CloudFunctionResponse", "에러 발생", task.exception)
                }
            }
    }

    private fun checkFavoriteStatus(nickname: String, uid: String) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("favorites")
            .whereEqualTo("summonerName", nickname)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.size() > 0) {
                    isFavorite = true
                    nameFavoriteButton.setBackgroundResource(R.drawable.name_favorite_save)
                } else {
                    isFavorite = false
                    nameFavoriteButton.setBackgroundResource(R.drawable.name_favorite)
                }
            }
            .addOnFailureListener { e ->
                Log.e("NameSearchMainActivity", "즐겨찾기 상태 확인 실패: $e")
            }
    }

    private fun removeFavoriteFromFirestore(nickname: String, uid: String) {
        val favoritesCollection = FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("favorites")
        favoritesCollection
            .whereEqualTo("summonerName", nickname)
            .get()
            .addOnSuccessListener { querySnapshot ->
                for (document in querySnapshot.documents) {
                    favoritesCollection.document(document.id)
                        .delete()
                        .addOnSuccessListener {
                            Log.d("NameSearchMainActivity", "즐겨찾기 제거 성공!")
                        }
                        .addOnFailureListener { e ->
                            Log.e("NameSearchMainActivity", "즐겨찾기 제거 실패: $e")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("NameSearchMainActivity", "즐겨찾기 쿼리 실패: $e")
            }
    }

    private fun addFavoriteToFirestore(nickname: String, uid: String) {
        val favoriteData = mapOf("summonerName" to nickname)
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("favorites")
            .add(favoriteData)
            .addOnSuccessListener {
                Log.d("NameSearchMainActivity", "즐겨찾기 저장 성공!")
            }
            .addOnFailureListener { e ->
                Log.e("NameSearchMainActivity", "즐겨찾기 저장 실패: $e")
            }
    }

    private fun getTierDrawable(tier: String): Int {
        return when (tier.uppercase()) {
            "IRON" -> R.drawable.iron
            "BRONZE" -> R.drawable.bronze
            "SILVER" -> R.drawable.silver
            "GOLD" -> R.drawable.gold
            "PLATINUM" -> R.drawable.platinum
            "EMERALD" -> R.drawable.emerald
            "DIAMOND" -> R.drawable.diamond
            "MASTER" -> R.drawable.master
            "GRANDMASTER" -> R.drawable.grandmaster
            "CHALLENGER" -> R.drawable.challenger
            else -> R.drawable.unranked
        }
    }

    private fun getTierColor(tier: String): Int {
        return when (tier.uppercase()) {
            "IRON" -> getColor(R.color.tier_iron)
            "BRONZE" -> getColor(R.color.tier_bronze)
            "SILVER" -> getColor(R.color.tier_silver)
            "GOLD" -> getColor(R.color.tier_gold)
            "PLATINUM" -> getColor(R.color.tier_platinum)
            "EMERALD" -> getColor(R.color.tier_emerald)
            "DIAMOND" -> getColor(R.color.tier_diamond)
            "MASTER" -> getColor(R.color.tier_master)
            "GRANDMASTER" -> getColor(R.color.tier_grandmaster)
            "CHALLENGER" -> getColor(R.color.tier_challenger)
            else -> getColor(R.color.black)
        }
    }

    private fun updateTopButtonUI(selectedButtonId: Int) {
        val topButtons = listOf(
            R.id.btnSolo,
            R.id.btnFlex
        )

        for (id in topButtons) {
            val button = findViewById<Button>(id)
            if (id == selectedButtonId) {
                button.setBackgroundResource(R.drawable.rounded_button_selected)
            } else {
                button.setBackgroundResource(R.drawable.rounded_button)
            }
        }
    }



    // ===== 디버깅용: Participant와 Player 객체 매핑 테스트 함수 =====
    private fun testParticipantAndPlayer() {
        // 테스트용 Participant 객체 생성 (예시 데이터)
        val participant = com.example.yumi2.model.Participant(
            summonerName = "Hide on bush", // 실제 API에서 빈 문자열일 경우를 가정
            puuid = "TEST_PUUID",
            championId = 55,
            kills = 10,
            deaths = 2,
            assists = 8,
            teamId = 100,
            win = true,
            summoner1Id = 4,
            summoner2Id = 14,
            item0 = 3001,
            item1 = 3047,
            item2 = 3071,
            item3 = 3057,
            item4 = 3020,
            item5 = 3031,
            item6 = 3363,
            totalMinionsKilled = 200,
            neutralMinionsKilled = 50,
            goldEarned = 15000
        )

        // 임의의 게임 시간 (초) 설정, 예를 들어 1000초
        val gameDurationSeconds = 1000L
        val cs = participant.totalMinionsKilled + participant.neutralMinionsKilled
        val csPerMin = if (gameDurationSeconds > 0) cs / (gameDurationSeconds / 60.0) else 0.0

        // Participant를 기반으로 Player 객체 생성 (championEngName은 예시로 "Jhin" 사용)
        val player = com.example.yumi2.model.Player(
            summonerName = if (participant.summonerName.isBlank()) "Unknown" else participant.summonerName,
            championId = participant.championId,
            championEngName = "Jhin",
            kills = participant.kills,
            deaths = participant.deaths,
            assists = participant.assists,
            teamId = participant.teamId,
            spell1Id = participant.summoner1Id,
            spell2Id = participant.summoner2Id,
            cs = cs,
            csPerMin = csPerMin,
            gold = participant.goldEarned,
            itemIds = listOf(participant.item0, participant.item1, participant.item2, participant.item3, participant.item4, participant.item5),
            isWin = participant.win
        )

        Log.d("Test", "Participant: $participant")
        Log.d("Test", "Player: $player")
    }
}