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
import com.example.yumi2.model.SummonerResponse
import com.example.yumi2.viewmodel.SummonerViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NameSearchMainActivity : AppCompatActivity() {

    private val viewModel: SummonerViewModel by viewModels()

    // ▼ 매치 히스토리 어댑터
    private lateinit var matchHistoryAdapter: MatchHistoryAdapter

    // ▼ 챔피언 통계 어댑터
    private lateinit var championStatsAdapter: ChampionStatsAdapter

    private var isFavorite: Boolean = false
    private lateinit var nameFavoriteButton: Button

    private lateinit var headerScroll: HorizontalScrollView
    private lateinit var bodyScroll: HorizontalScrollView

    // 현재 소환사 PUUID와 선택된 큐
    private var currentPuuid: String? = null
    private var currentQueue: Int? = 420  // 기본: 솔로랭크


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.name_search_main)

        // RecyclerView 세팅
        val recyclerMatchHistory = findViewById<RecyclerView>(R.id.recyclerMatchHistory)
        recyclerMatchHistory.layoutManager = LinearLayoutManager(this)
        matchHistoryAdapter = MatchHistoryAdapter(emptyList<MatchHistoryItem>(), "")
        recyclerMatchHistory.adapter = matchHistoryAdapter


        val matchHistoryRecyclerView = findViewById<RecyclerView>(R.id.recyclerMatchHistory)
        matchHistoryRecyclerView.layoutManager = LinearLayoutManager(this)
        matchHistoryAdapter = MatchHistoryAdapter(emptyList<MatchHistoryItem>(), "")


        matchHistoryRecyclerView.adapter = matchHistoryAdapter

        headerScroll = findViewById(R.id.headerScroll)
        bodyScroll = findViewById(R.id.bodyScroll)

        // 두 스크롤뷰를 동기화 (질문 코드 그대로 유지)
        headerScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            bodyScroll.scrollTo(scrollX, 0)
        }
        bodyScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            headerScroll.scrollTo(scrollX, 0)
        }

        val championStatsRecyclerView = findViewById<RecyclerView>(R.id.recyclerChampionStats)
        championStatsRecyclerView.layoutManager = LinearLayoutManager(this)
        championStatsAdapter = ChampionStatsAdapter(emptyList())
        championStatsRecyclerView.adapter = championStatsAdapter

        // 챔피언 통계용 스크롤 끝에서 다음 4개 로딩 (질문 코드 그대로)
        championStatsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val layoutManager = rv.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 1) {
                    // 다음 4개 로드
                    viewModel.loadNextPage()
                }
            }
        })

        val btnAll = findViewById<Button>(R.id.btnAll)
        val btnSolo = findViewById<Button>(R.id.btnSolo)
        val btnFlex = findViewById<Button>(R.id.btnFlex)

        val gameName = intent.getStringExtra("gameName") ?: ""
        val tagLine = intent.getStringExtra("tagLine") ?: ""

        val summonerNameText = findViewById<TextView>(R.id.summonerName)
        val summonerRankText = findViewById<TextView>(R.id.summonerRank)
        val summonerIcon = findViewById<ImageView>(R.id.summonerIcon)
        val summonerLevelText = findViewById<TextView>(R.id.summonerLevel)
        val refreshButton = findViewById<Button>(R.id.refreshMatchData)
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

        // 즐겨찾기 버튼
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


        // 1) 소환사 정보 검색
        viewModel.searchSummoner(gameName, tagLine, FirebaseAuth.getInstance().currentUser?.uid ?: "")

        // 2) 리프레시 버튼
        refreshButton.setOnClickListener {
            viewModel.searchSummoner(gameName, tagLine, FirebaseAuth.getInstance().currentUser?.uid ?: "")
        }

        // 3) 소환사 정보 관찰
        lifecycleScope.launch {
            viewModel.summonerInfo.collect { summoner: SummonerResponse? ->
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

                        // (랭크 카드 UI 표시)
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

                    // (E) 소환사 정보가 있으면 puuid 저장 & 기본 큐=솔로(420)로 설정
                    currentPuuid = summoner.puuid
                    currentQueue = 420

                    // 1) 챔피언 통계 (4개씩 페이지네이션)
                    viewModel.resetStats()
                    viewModel.loadChampionStatsAll(summoner.puuid, 420)

                    // 2) 매치 히스토리 (한 번에)
                    //    - 여기서 420(솔로랭크)만 가져오고 싶다면:
                    viewModel.loadRecentMatches(summoner.puuid, 420)
                    //    - 전체 큐를 가져오고 싶다면 queue=null

                    // (F) 버튼 클릭 시 챔피언 통계 갱신
                    btnAll.setOnClickListener {
                        currentQueue = null
                        viewModel.resetStats()
                        viewModel.loadChampionStatsAll(summoner.puuid, null)
                        // 매치 히스토리도 전체 큐로 보고 싶으면:
                        viewModel.loadRecentMatches(summoner.puuid, null)
                    }
                    btnSolo.setOnClickListener {
                        currentQueue = 420
                        viewModel.resetStats()
                        viewModel.loadChampionStatsAll(summoner.puuid, 420)
                        // 매치 히스토리도 솔로랭크만:
                        viewModel.loadRecentMatches(summoner.puuid, 420)
                    }
                    btnFlex.setOnClickListener {
                        currentQueue = 440
                        viewModel.resetStats()
                        viewModel.loadChampionStatsAll(summoner.puuid, 440)
                        // 매치 히스토리도 자유랭크만:
                        viewModel.loadRecentMatches(summoner.puuid, 440)
                    }

                } else {
                    withContext(Dispatchers.Main) {
                        summonerNameText.text = "소환사 정보를 불러올 수 없습니다."
                    }
                }
            }
        }


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
                    // 1) 매치 히스토리 리스트 (RecyclerView)
                    matchHistoryAdapter.setItems(stats.matches)

                    // 2) 통합 통계 (승률, 평점, etc.)
                    val totalMatches = stats.matches.size
                    val totalWins = stats.totalWins
                    val totalLosses = stats.totalLosses
                    val winRate = if (totalMatches > 0) (totalWins * 100 / totalMatches) else 0

                    // 예: tvWinRate, tvTotalGames, tvWins, tvLosses
                    val tvWinRate = findViewById<TextView>(R.id.tvWinRate)
                    val tvTotalGames = findViewById<TextView>(R.id.tvTotalGames)
                    val tvWins = findViewById<TextView>(R.id.tvWins)
                    val tvLosses = findViewById<TextView>(R.id.tvLosses)

                    tvWinRate.text = "승률 ${winRate}%"
                    tvTotalGames.text = "${totalMatches}전 "
                    tvWins.text = "승 $totalWins"
                    tvLosses.text = " 패 $totalLosses"

                    // 평점(averageKDA)
                    val tvScore = findViewById<TextView>(R.id.tvScore)
                    val averageKDA = String.format("%.2f", stats.averageKDA) // 소수점 2자리
                    tvScore.text = "평점 $averageKDA"

                    // 평균 K/D/A
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
}
