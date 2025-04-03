package com.example.opggyumi

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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.opggyumi.adapter.ChampionStatsAdapter
import com.example.opggyumi.model.SummonerResponse
import com.example.opggyumi.viewmodel.SummonerViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NameSearchMainActivity : AppCompatActivity() {
    private val viewModel: SummonerViewModel by viewModels()
    private lateinit var championStatsAdapter: ChampionStatsAdapter

    private var isFavorite: Boolean = false
    private lateinit var nameFavoriteButton: Button

    private lateinit var headerScroll: HorizontalScrollView
    private lateinit var bodyScroll: HorizontalScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.name_search_main) // ✅ 수정된 XML 파일 적용

        headerScroll = findViewById(R.id.headerScroll)
        bodyScroll = findViewById(R.id.bodyScroll)

        // 헤더 스크롤이 변경될 때 -> bodyScroll도 동일한 scrollX로 맞춤
        headerScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            bodyScroll.scrollTo(scrollX, 0)
        }

        // 바디 스크롤이 변경될 때 -> headerScroll도 동일한 scrollX로 맞춤
        bodyScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            headerScroll.scrollTo(scrollX, 0)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerChampionStats)
        recyclerView.layoutManager = LinearLayoutManager(this)
        championStatsAdapter = ChampionStatsAdapter(emptyList())
        recyclerView.adapter = championStatsAdapter


        val gameName = intent.getStringExtra("gameName") ?: ""
        val tagLine = intent.getStringExtra("tagLine") ?: ""

        val summonerNameText = findViewById<TextView>(R.id.summonerName)
        val summonerRankText = findViewById<TextView>(R.id.summonerRank)
        val summonerIcon = findViewById<ImageView>(R.id.summonerIcon)
        val summonerLevelText = findViewById<TextView>(R.id.summonerLevel)
        val refreshButton = findViewById<Button>(R.id.refreshMatchData)
        val backButton = findViewById<Button>(R.id.PageBack)

        // ✅ 솔로랭크 UI 요소
        val rankCard = findViewById<View>(R.id.rankCard)
        val rankType = findViewById<TextView>(R.id.rankType)
        val rankTier = findViewById<TextView>(R.id.rankTier)
        val rankLP = findViewById<TextView>(R.id.rankLP)
        val rankWinLoss = findViewById<TextView>(R.id.rankWinLoss)
        val rankTierImage = findViewById<ImageView>(R.id.rankTierImage)

        // ✅ 자유랭크 UI 요소
        val rankCard1 = findViewById<View>(R.id.rankCard1)
        val rankType1 = findViewById<TextView>(R.id.rankType1)
        val rankTier1 = findViewById<TextView>(R.id.rankTier1)
        val rankLP1 = findViewById<TextView>(R.id.rankLP1)
        val rankWinLoss1 = findViewById<TextView>(R.id.rankWinLoss1)
        val rankTierImage1 = findViewById<ImageView>(R.id.rankTierImage1)

        // 즐겨찾기 버튼 처리
        nameFavoriteButton = findViewById(R.id.NameFavorite)
        // 초기 상태는 false. 단, 아래에서 summoner 정보 로드 후 Firestore로 상태를 확인할 예정.
        nameFavoriteButton.setOnClickListener {
            val favoriteNickname = summonerNameText.text.toString()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            if (uid.isEmpty()) return@setOnClickListener

            // 토글 상태에 따라 즐겨찾기 추가 또는 제거
            if (!isFavorite) {
                // 즐겨찾기 추가: 버튼 배경을 변경하고 Firestore에 저장
                nameFavoriteButton.setBackgroundResource(R.drawable.name_favorite_save)
                addFavoriteToFirestore(favoriteNickname, uid)
                isFavorite = true
            } else {
                // 즐겨찾기 제거: 버튼 배경을 원래 상태로 변경하고 Firestore에서 삭제
                nameFavoriteButton.setBackgroundResource(R.drawable.name_favorite)
                removeFavoriteFromFirestore(favoriteNickname, uid)
                isFavorite = false
            }
        }

        backButton.setOnClickListener {
            val intent = Intent(this, NameSearchActivity::class.java)
            startActivity(intent)
        }

        summonerNameText.text = "$gameName#$tagLine"

        viewModel.searchSummoner(gameName, tagLine, com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "")

        refreshButton.setOnClickListener {
            viewModel.searchSummoner(gameName, tagLine, com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "")
        }

        lifecycleScope.launchWhenStarted {
            viewModel.summonerInfo.collect { summoner ->
                if (summoner != null) {
                    viewModel.loadChampionStats(summoner.puuid)
                }
            }
        }

        // ▼ 챔피언 전적 관찰
        lifecycleScope.launchWhenStarted {
            viewModel.championStats.collect { stats ->
                championStatsAdapter.setItems(stats)
            }
        }

        lifecycleScope.launch {
            viewModel.summonerInfo.collect { summoner: SummonerResponse? ->
                if (summoner != null) {
                    val latestVersion = withContext(Dispatchers.IO) {
                        viewModel.getLatestLolVersion() ?: "14.1.1"
                    }
                    val iconUrl =
                        "https://ddragon.leagueoflegends.com/cdn/$latestVersion/img/profileicon/${summoner.profileIconId}.png"

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

                        var hasRankData = false // ✅ 랭크 정보 있는지 체크

                        // ✅ 솔로랭크 정보 적용
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
                            // ✅ Unranked 처리
                            rankType.text = "개인/2인전"
                            rankTier.text = "Unranked"
                            rankLP.text = "-"
                            rankWinLoss.text = "?승 ?패"
                            rankTierImage.setImageResource(R.drawable.unranked)
                            rankTier.setTextColor(getTierColor("unranked"))
                            rankCard.visibility = View.VISIBLE
                        }

                        // ✅ 자유랭크 정보 적용
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
                            // ✅ Unranked 처리
                            rankType1.text = "자유 5대5 대전"
                            rankTier1.text = "Unranked"
                            rankLP1.text = "-"
                            rankWinLoss1.text = "?승 ?패"
                            rankTierImage1.setImageResource(R.drawable.unranked)
                            rankTier1.setTextColor(getTierColor("unranked"))
                            rankCard1.visibility = View.VISIBLE
                        }

                        // ✅ 만약 솔로랭크/자유랭크 둘 다 없으면 카드 숨김 처리 안 하고 유지
                        if (!hasRankData) {
                            rankCard.visibility = View.VISIBLE
                            rankCard1.visibility = View.VISIBLE
                        }

                        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        checkFavoriteStatus("$displayGameName#$displayTagLine", currentUid)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        summonerNameText.text = "소환사 정보를 불러올 수 없습니다."
                    }
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
                    // 즐겨찾기에 이미 등록되어 있다면
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
        // 쿼리로 해당 닉네임을 가진 즐겨찾기 문서를 찾습니다.
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


    // 즐겨찾기 저장 함수 추가
    private fun addFavoriteToFirestore(nickname: String, uid: String) {
        val favoriteData = mapOf("summonerName" to nickname)
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("favorites")
            .add(favoriteData)
            .addOnSuccessListener {
                Log.d("NameSearchMainActivity", "즐겨찾기 저장 성공!")
                // 즐겨찾기 목록 업데이트(필요하면 FavoritesAdapter를 통해 RecyclerView 갱신)
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
            else -> getColor(R.color.black) // 기본 검정색
        }
    }

}
