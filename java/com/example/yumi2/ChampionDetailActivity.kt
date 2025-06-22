package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.yumi2.util.RuneImageManager
import com.google.firebase.firestore.FirebaseFirestore

class ChampionDetailActivity : AppCompatActivity() {

    private lateinit var splashImage: ImageView
    private lateinit var nameText: TextView
    private lateinit var skillP: ImageView
    private lateinit var skillQ: ImageView
    private lateinit var skillW: ImageView
    private lateinit var skillE: ImageView
    private lateinit var skillR: ImageView
    private lateinit var rune1: ImageView
    private lateinit var rune2: ImageView
    private lateinit var rune3: ImageView
    private lateinit var rune4: ImageView
    private lateinit var rune5: ImageView
    private lateinit var rune6: ImageView
    private lateinit var spell1: ImageView
    private lateinit var spell2: ImageView
    private lateinit var startAtk1: ImageView
    private lateinit var startAtk2: ImageView
    private lateinit var boot1: ImageView
    private lateinit var core1: ImageView
    private lateinit var core2: ImageView
    private lateinit var core3: ImageView

    private lateinit var scrollContent: ScrollView
    private lateinit var tvNoData: TextView
    private lateinit var layoutNoData: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.champion_detail)

        // 1) 새로 추가한 뷰들 바인딩
        scrollContent = findViewById(R.id.scrollContent)
        tvNoData      = findViewById(R.id.tvNoData)
        layoutNoData = findViewById(R.id.layoutNoData)

        // 뒤로 가기 버튼
        val btnBack: Button = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            startActivity(Intent(this, ChampionTierVowelActivity::class.java))
        }

        findViewById<Button>(R.id.btnBackNoData).setOnClickListener {
            startActivity(Intent(this, ChampionTierVowelActivity::class.java))
        }

        // 룬 트리 텍스트뷰 바인딩
        val tvPrimaryRuneTree   = findViewById<TextView>(R.id.tvPrimaryRune)
        val tvSecondaryRuneTree = findViewById<TextView>(R.id.tvSubRune)

        // 룬 트리 ID → 한글 이름 매핑표
        val runeTreeNameMap = mapOf(
            8000 to "정밀",
            8100 to "지배",
            8200 to "마법",
            8300 to "영감",
            8400 to "결의"
        )

        // 나머지 뷰 바인딩
        splashImage = findViewById(R.id.imgChampionSplash)
        nameText    = findViewById(R.id.tvChampionName)
        skillP      = findViewById(R.id.imgSkillP)
        skillQ      = findViewById(R.id.imgSkillQ)
        skillW      = findViewById(R.id.imgSkillW)
        skillE      = findViewById(R.id.imgSkillE)
        skillR      = findViewById(R.id.imgSkillR)
        rune1       = findViewById(R.id.imgRune1)
        rune2       = findViewById(R.id.imgRune2)
        rune3       = findViewById(R.id.imgRune3)
        rune4       = findViewById(R.id.imgRune4)
        rune5       = findViewById(R.id.imgRune5)
        rune6       = findViewById(R.id.imgRune6)
        spell1      = findViewById(R.id.imgSpell1)
        spell2      = findViewById(R.id.imgSpell2)
        startAtk1   = findViewById(R.id.imgStartAtk1)
        startAtk2   = findViewById(R.id.imgStartAtk2)
        boot1       = findViewById(R.id.imgBoot1)
        core1       = findViewById(R.id.imgCore1)
        core2       = findViewById(R.id.imgCore2)
        core3       = findViewById(R.id.imgCore3)

        // 인텐트로부터 챔피언 이름 가져오기
        val championName = intent.getStringExtra("championName") ?: ""
        nameText.text    = championName

        // 룬 매핑 준비가 끝나면 데이터 로드
        RuneImageManager.loadRuneImages {
            loadChampionBuildData(championName, tvPrimaryRuneTree, tvSecondaryRuneTree, runeTreeNameMap)
        }
    }

    private fun loadChampionBuildData(
        championName: String,
        tvPrimary: TextView,
        tvSecondary: TextView,
        runeTreeNameMap: Map<Int, String>
    ) {
        val fixedName = championName.replace(" ", "")
        FirebaseFirestore.getInstance()
            .collection("champion_builds")
            .document(fixedName)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    // 문서가 없으면 스크롤 컨텐츠 숨기고 안내문만 보이게
                    scrollContent.visibility = View.GONE
                    layoutNoData.visibility  = View.VISIBLE
                    tvNoData.visibility      = View.VISIBLE
                    tvNoData.text = "$championName 챔피언은\n현재 데이터가 없습니다"
                    Log.w("ChampionDetail", "Firestore에 문서 없음: $championName")
                    return@addOnSuccessListener
                }

                // 문서가 있으면 기존 대로 컨텐츠 채우고, 안내문은 숨김
                scrollContent.visibility = View.VISIBLE
                layoutNoData.visibility  = View.GONE
                tvNoData.visibility      = View.GONE

                // 1) 룬 트리 라벨 세팅
                val primaryId   = (doc.getLong("runeTree") ?: 8000).toInt()
                val secondaryId = (doc.getLong("subTree")  ?: 8300).toInt()
                tvPrimary.text   = runeTreeNameMap[primaryId]   ?: "정밀"
                tvSecondary.text = runeTreeNameMap[secondaryId] ?: "영감"

                // 2) 아이콘 / 스킬 / 아이템 로딩
                val spellIds = doc.get("spells") as? List<Long> ?: emptyList()
                val runeIds  = doc.get("runes")  as? List<Long> ?: emptyList()
                val items    = doc.get("items")  as? Map<*, *> ?: emptyMap<String, List<Long>>()
                val splashUrl = doc.getString("splash_url") ?: ""
                val skills    = doc.get("skills") as? Map<*, *>

                // 스플래시
                if (splashUrl.isNotEmpty()) {
                    Glide.with(this).load(splashUrl).into(splashImage)
                }

                // 소환사 주문
                val spellBase = "https://ddragon.leagueoflegends.com/cdn/15.9.1/img/spell"
                val spellMap = mapOf(
                    1 to "SummonerBoost",
                    3 to "SummonerExhaust",
                    4 to "SummonerFlash",
                    6 to "SummonerHaste",
                    7 to "SummonerHeal",
                    11 to "SummonerSmite",
                    12 to "SummonerTeleport",
                    13 to "SummonerMana",
                    14 to "SummonerDot",
                    21 to "SummonerBarrier"
                )
                spellIds.getOrNull(0)?.toInt()?.let { id ->
                    spellMap[id]?.let { Glide.with(this).load("$spellBase/$it.png").into(spell1) }
                }
                spellIds.getOrNull(1)?.toInt()?.let { id ->
                    spellMap[id]?.let { Glide.with(this).load("$spellBase/$it.png").into(spell2) }
                }

                // 룬
                val runeViews = listOf(rune1, rune2, rune3, rune4, rune5, rune6)
                for (i in runeViews.indices) {
                    runeIds.getOrNull(i)?.toInt()?.let { id ->
                        RuneImageManager.runeMap[id]?.let { Glide.with(this).load(it).into(runeViews[i]) }
                    }
                }

                // 스킬
                (skills as? Map<String, String>)?.let { skillMap ->
                    skillMap["passive"]?.let { Glide.with(this).load(it).into(skillP) }
                    skillMap["q"]?.let       { Glide.with(this).load(it).into(skillQ) }
                    skillMap["w"]?.let       { Glide.with(this).load(it).into(skillW) }
                    skillMap["e"]?.let       { Glide.with(this).load(it).into(skillE) }
                    skillMap["r"]?.let       { Glide.with(this).load(it).into(skillR) }
                }

                // 아이템
                val itemBase = "https://ddragon.leagueoflegends.com/cdn/15.9.1/img/item"
                fun loadItem(list: List<Long>?, idx: Int, view: ImageView) {
                    list?.getOrNull(idx)?.toInt()?.let { Glide.with(this).load("$itemBase/$it.png").into(view) }
                }
                loadItem(items["start_items_atk"]  as? List<Long>, 0, startAtk1)
                loadItem(items["start_items_atk"]  as? List<Long>, 1, startAtk2)
                loadItem(items["boots"]            as? List<Long>, 0, boot1)
                loadItem(items["core"]             as? List<Long>, 0, core1)
                loadItem(items["core"]             as? List<Long>, 1, core2)
                loadItem(items["core"]             as? List<Long>, 2, core3)

            }
            .addOnFailureListener { e ->
                // 실패 시에도 안내문만 띄우기
                scrollContent.visibility = View.GONE
                tvNoData.visibility      = View.VISIBLE
                tvNoData.text = "데이터 가져오기 실패: ${e.message}"
                Log.e("ChampionDetail", "Firestore 불러오기 실패: ${e.message}")
                e.printStackTrace()
            }
    }
}
