package com.example.yumi2

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.yumi2.model.Item
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


data class SavedConfig(val name: String, val slots: List<String?>)
class ChampcalActivity : AppCompatActivity(), ChampionSelectionDialog.ChampionSelectionListener {

    private lateinit var championSelector: FrameLayout
    private lateinit var championNameText: TextView
    private lateinit var levelText: TextView
    private lateinit var btnLevelMinus: Button
    private lateinit var btnLevelPlus: Button
    private lateinit var statsTable: TableLayout
    private lateinit var btnLoadFavoriteItems: Button
    private lateinit var itemSlotContainer: LinearLayout

    private val db = FirebaseFirestore.getInstance()
    private var currentChampionId: String? = null
    private var currentLevel: Int = 1
    // 즐겨찾기에서 불러온 아이템 (최대 6개, 읽기 전용)
    private var favoriteItems: MutableList<Item?> = MutableList(6) { null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_champcal)

        championSelector = findViewById(R.id.championSelector)
        championNameText = findViewById(R.id.championName)
        levelText = findViewById(R.id.levelText)
        btnLevelMinus = findViewById(R.id.btnLevelMinus)
        btnLevelPlus = findViewById(R.id.btnLevelPlus)
        statsTable = findViewById(R.id.statsTable)
        btnLoadFavoriteItems = findViewById(R.id.btnLoadItems)
        itemSlotContainer = findViewById(R.id.itemSlotContainer)

        currentLevel = 1
        levelText.text = "레벨: $currentLevel"

        // 미리 6개의 빈 슬롯 생성
        createBlankItemSlots()

        championSelector.setOnClickListener {
            ChampionSelectionDialog()
                .show(supportFragmentManager, "ChampionSelectionDialog")
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        btnLevelMinus.setOnClickListener {
            if (currentLevel > 1) {
                currentLevel--
                levelText.text = "레벨: $currentLevel"
                currentChampionId?.let { loadChampionData(it, currentLevel) }
            }
        }
        btnLevelPlus.setOnClickListener {
            if (currentLevel < 18) {
                currentLevel++
                levelText.text = "레벨: $currentLevel"
                currentChampionId?.let { loadChampionData(it, currentLevel) }
            }
        }

        btnLoadFavoriteItems.text = "아이템 불러오기"
        btnLoadFavoriteItems.setOnClickListener {
            showLoadConfigurationsDialog()
        }

        val btnResetItems = findViewById<Button>(R.id.btnResetItems).apply {
            text = "전체 초기화"
            setOnClickListener {
                // 아이템 초기화
                for (i in favoriteItems.indices) {
                    favoriteItems[i] = null
                }
                updateItemSlotsUI()

                // 레벨 초기화 추가
                currentLevel = 1
                levelText.text = "레벨: $currentLevel"

                // 챔피언 데이터 갱신
                currentChampionId?.let { loadChampionData(it, currentLevel) }
            }
        }

    }

    private fun createBlankItemSlots() {
        itemSlotContainer.removeAllViews()
        repeat(6) {
            val iv = ImageView(this).apply {
                // 1) weight 기반으로 너비 분할
                val params = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).apply {
                    // 2) 각 슬롯 사이에 4dp 마진 추가
                    val marginDp = 4f
                    val marginPx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        marginDp,
                        resources.displayMetrics
                    ).toInt()
                    setMargins(marginPx, 0, marginPx, 0)
                }
                layoutParams = params

                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(R.drawable.placeholder_image)
            }
            itemSlotContainer.addView(iv)
        }
    }

    private fun loadChampionData(championId: String, level: Int) {
        db.collection("champions").document(championId).get()
            .addOnSuccessListener { doc ->
                val data = doc.data ?: run {
                    Toast.makeText(this, "챔피언 데이터를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // ──────────────── attack_speed_table 읽어서 캐시에 저장
                (data["attack_speed_table"] as? Map<*, *>)?.let { raw ->
                    val map = raw.entries.associate { (k, v) ->
                        k.toString() to ((v as? Number)?.toDouble() ?: 0.0)
                    }
                    FirebaseCache.attackSpeedData[championId] = map
                }

                currentChampionId = championId
                championNameText.text = data["name"] as? String ?: ""

                (data["portrait_url"] as? String)?.takeIf { it.isNotEmpty() }?.let { url ->
                    championSelector.findViewById<TextView>(R.id.championSelectorText)?.visibility = View.GONE

                    var iv = championSelector.findViewById<ImageView>(R.id.championImage)
                    if (iv == null) {
                        val sizeDp = 120
                        val sizePx = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            sizeDp.toFloat(),
                            resources.displayMetrics
                        ).toInt()

                        iv = ImageView(this).apply {
                            id = R.id.championImage
                            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
                            scaleType = ImageView.ScaleType.FIT_XY  // 어차피 crop으로 자르기 때문에 자유롭게 설정 가능
                            adjustViewBounds = false
                        }
                        championSelector.addView(iv)
                    }

                    // ✅ 여기에 적용!
                    Glide.with(this)
                        .load(url)
                        .transform(TopCropTransformation())  // 👈 여기가 핵심!
                        .into(iv)
                }


                val baseStats = data["base_stats"] as? Map<String, Number>
                val growthStats = data["growth_stats"] as? Map<String, Number>
                val extraStats = data["item_stats"] as? Map<String, Number>
                // level 18 기준 Raw AS (items 제외)
                val finalAS = (data["final_attack_speed"] as? Number)?.toDouble()
                updateStats(level, baseStats, growthStats, extraStats, finalAS)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "데이터 불러오기 오류: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun parseItemStats(statsString: String): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        statsString.trim().removePrefix("{").removeSuffix("}")
            .split(",")
            .map { it.split("=") }
            .filter { it.size == 2 }
            .forEach { (rawKey, rawVal) ->
                val key = when (val k = rawKey.trim()) {
                    "기본 마나 재생" -> "manaregen"
                    "체력 회복 및 보호막" -> "has"
                    "기본 체력 재생" -> "hpregen"
                    "공격력" -> "attackdamage"
                    "주문력" -> "abilitypower"
                    "방어력" -> "armor"
                    "마법 저항력" -> "spellblock"
                    "공격 속도" -> "attackspeed"
                    "스킬 가속" -> "cooldownreduction"
                    "치명타 확률" -> "crit"
                    "이동 속도" -> "movespeed"
                    "물리 관통력" -> "attackpenetration"
                    "방어구 관통력" -> "armorpenetration"
                    "마법 관통력" -> "magicpenetration"
                    "체력" -> "hp"
                    "마나" -> "mp"
                    "생명력 흡수" -> "lifesteal"
                    "공격 사거리" -> "attackrange"
                    "강인함" -> "tenacity"
                    else -> k
                }
                val v = rawVal.trim().replace("%", "").toDoubleOrNull() ?: 0.0
                result[key] = (result[key] ?: 0.0) + v
            }
        return result
    }

    private fun updateStats(
        level: Int,
        baseStats: Map<String, Number>?,
        growthStats: Map<String, Number>?,
        extraStats: Map<String, Number>?,
        finalAttackSpeed: Number? = null
    ) {
        // 1) 테이블 초기화
        statsTable.removeAllViews()
        if (baseStats == null || growthStats == null || currentChampionId == null) return

        // 2) 아이템 보너스 합산 (% 기준)
        val itemBonus = mutableMapOf<String, Double>()
        favoriteItems.filterNotNull().forEach { item ->
            parseItemStats(item.stats).forEach { (k, v) ->
                itemBonus[k] = (itemBonus[k] ?: 0.0) + v
            }
        }

        // 3) 스탯 레이블 ↔ 내부 키 매핑
        val filterMapping = listOf(
            "체력" to "hp",
            "마나" to "mp",
            "공격력" to "attackdamage",
            "주문력" to "abilitypower",
            "방어력" to "armor",
            "마법 저항력" to "spellblock",
            "공격 속도" to "attackspeed",
            "스킬 가속" to "cooldownreduction",
            "치명타 확률" to "crit",
            "이동 속도" to "movespeed",
            "물리 관통력" to "attackpenetration",
            "방어구 관통력" to "armorpenetration",
            "마법 관통력" to "magicpenetration",
            "기본 체력 재생" to "hpregen",
            "기본 마나 재생" to "manaregen",
            "생명력 흡수" to "lifesteal",
            "체력 회복 및 보호막" to "has",
            "공격 사거리" to "attackrange",
            "강인함" to "tenacity"
        )
        val percentLabels = setOf(
            "치명타 확률",
            "기본 체력 재생",
            "기본 마나 재생",
            "체력 회복 및 보호막",
            "생명력 흡수",
            "강인함"
        )

        // 4) 스탯 계산 & 테이블에 표시
        filterMapping.forEach { (label, key) ->
            // 4-1) “총합(rawTotal)” (성장 + extraStats + 아이템)
            val rawTotal: Double = if (key == "attackspeed") {
                val asAtLevel = FirebaseCache.attackSpeedData[currentChampionId]!![level.toString()]
                    ?: baseStats["attackspeed"]!!.toDouble()
                val asAt1 = FirebaseCache.attackSpeedData[currentChampionId]!!["1"]
                    ?: baseStats["attackspeed"]!!.toDouble()
                asAtLevel + asAt1 * (itemBonus["attackspeed"] ?: 0.0) / 100.0
            } else {
                val b = baseStats[key]?.toDouble() ?: 0.0
                val g = growthStats[key]?.toDouble() ?: 0.0
                b + g * (level - 1) +
                        (extraStats?.get(key)?.toDouble() ?: 0.0) +
                        (itemBonus[key] ?: 0.0)
            }

            // 4-2) 레벨1 기준 베이스값
            val baseAtLevel1: Double = if (key == "attackspeed") {
                FirebaseCache.attackSpeedData[currentChampionId]!!["1"]
                    ?: baseStats["attackspeed"]!!.toDouble()
            } else {
                baseStats[key]?.toDouble() ?: 0.0
            }

            // 4-3) 레벨1 대비 증가량
            val delta = rawTotal - baseAtLevel1

            // 4-4) 반올림 처리
            val dispTotal = if (key == "attackspeed")
                kotlin.math.round(rawTotal * 1000) / 1000.0 else rawTotal
            val dispDelta = if (key == "attackspeed")
                kotlin.math.round(delta * 1000) / 1000.0 else delta

            // 4-5) 표시 문자열 구성: “현재(레벨+아이템) / (+레벨1 대비 증가량)”
            val displayText = when {
                key == "attackspeed" ->
                    String.format("%.3f / (+%.3f)", dispTotal, dispDelta)
                label in percentLabels ->
                    String.format("%.0f%% / (+%.0f%%)", dispTotal, dispDelta)
                else ->
                    String.format("%.0f / (+%.0f)", dispTotal, dispDelta)
            }

            // 4-6) TableRow inflate & 바인딩
            val rowView = LayoutInflater.from(this)
                .inflate(R.layout.champrow_stat, statsTable, false) as TableRow
            val iconView  = rowView.findViewById<ImageView>(R.id.statIcon)
            val nameView  = rowView.findViewById<TextView>(R.id.statName)
            val valueView = rowView.findViewById<TextView>(R.id.statValue)

            // 아이콘 매핑
            val statIconMap = mapOf(
                "체력" to R.drawable.lol_stat_hp,
                "마나" to R.drawable.lol_stat_mana,
                "공격력" to R.drawable.lol_stat_attack,
                "주문력" to R.drawable.lol_stat_magic,
                "방어력" to R.drawable.lol_stat_armor,
                "마법 저항력" to R.drawable.lol_stat_magic_r,
                "공격 속도" to R.drawable.lol_stat_attack_speed,
                "이동 속도" to R.drawable.lol_filter_movement_speed,
                "기본 체력 재생" to R.drawable.lol_stat_hpregen,
                "기본 마나 재생" to R.drawable.lol_stat_manaregen,
                "치명타 확률" to R.drawable.lol_stat_crit_chance,
                "생명력 흡수" to R.drawable.lol_stat_life_steal,
                "스킬 가속" to R.drawable.lol_stat_skill_time,
                "물리 관통력" to R.drawable.lol_stat_armor_p,
                "방어구 관통력" to R.drawable.lol_stat_armor_p,
                "마법 관통력" to R.drawable.lol_stat_magic_p,
                "체력 회복 및 보호막" to R.drawable.lol_stat_has,
                "공격 사거리" to R.drawable.lol_stat_range,
                "강인함" to R.drawable.lol_stat_tenacity
            )

            iconView.setImageResource(statIconMap[label] ?: R.drawable.yumi_icon)
            nameView.text  = label
            valueView.text = displayText

            statsTable.addView(rowView)
        }
    }







    private fun loadSavedConfiguration(savedIds: List<String?>) {
        val itemIds = savedIds.filterNotNull()
        if (itemIds.isEmpty()) return
        db.collection("items").whereIn("id", itemIds)
            .get()
            .addOnSuccessListener { documents ->
                val itemsMap = mutableMapOf<String, Item>()
                for (doc in documents) {
                    val id = doc.getString("id") ?: continue
                    val name = doc.getString("name") ?: "알 수 없음"
                    val imageUrl = doc.getString("imageUrl") ?: ""
                    val tags = doc.get("tags") as? List<String> ?: emptyList()
                    val cost = doc.getLong("cost")?.toInt() ?: 0
                    val stats = doc.get("stats")?.toString() ?: "능력치 정보 없음"
                    val effect = doc.getString("plaintext") ?: "효과 정보 없음"
                    val description = doc.getString("description") ?: "설명 없음"
                    itemsMap[id] = Item(id, name, imageUrl, tags, cost, stats, effect, description)
                }
                // 저장된 구성 순서대로 최대 6칸에 favoriteItems에 채우기
                for (i in 0 until 6) {
                    favoriteItems[i] = if (i < itemIds.size) itemsMap[itemIds[i]] else null
                }
                updateItemSlotsUI()
                currentChampionId?.let { loadChampionData(it, currentLevel) }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "구성 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateItemSlotsUI() {
        itemSlotContainer.removeAllViews()
        for (item in favoriteItems) {
            val imageView = ImageView(this)
            // 1) params 정의부를 위와 동일하게
            val params = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply {
                val marginDp = 4f
                val marginPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    marginDp,
                    resources.displayMetrics
                ).toInt()
                setMargins(marginPx, 0, marginPx, 0)
            }
            imageView.layoutParams = params

            imageView.adjustViewBounds = true
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            if (item != null) {
                Glide.with(this).load(item.imageUrl).into(imageView)
            } else {
                imageView.setImageResource(R.drawable.placeholder_image)
            }
            itemSlotContainer.addView(imageView)
        }
    }

    private fun showLoadConfigurationsDialog() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Toast.makeText(this, "로그인 정보가 없습니다!", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users")
            .document(uid)
            .collection("savedConfigurations")
            .get()
            .addOnSuccessListener { snapshot ->
                val configs = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("configName") ?: return@mapNotNull null
                    val raw = doc.get("slots") as? List<*>
                    val slots = raw?.map { it as? String } ?: emptyList()
                    SavedConfig(name, slots)
                }
                if (configs.isEmpty()) {
                    Toast.makeText(this, "저장된 구성이 없습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // 다이얼로그 레이아웃 inflate
                val view = LayoutInflater.from(this)
                    .inflate(R.layout.dialog_load_configurations, null)
                val listView = view.findViewById<ListView>(R.id.listViewConfigurations)
                val btnEdit = view.findViewById<Button>(R.id.btnEditItemSet)

                // 리스트뷰 어댑터
                listView.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_activated_1,
                    configs.map { it.name }
                )

                // 커스텀 타이틀 뷰
                val titleView = TextView(this).apply {
                    text = "불러올 아이템 구성을 선택하세요"
                    setTextColor(Color.parseColor("#80929F"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setPadding(24, 24, 24, 12)
                }

                // 다이얼로그 빌더 & create
                val dialog = AlertDialog.Builder(this)
                    .setCustomTitle(titleView)
                    .setView(view)
                    .setNegativeButton("취소", null)
                    .create()

                dialog.setOnShowListener {
                    // 전체 배경
                    dialog.window
                        ?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#E7EBED")))

                    // 리스트뷰 스타일
                    listView.divider = ColorDrawable(Color.parseColor("#B1C1CE"))
                    listView.dividerHeight = 1
                    listView.selector = ColorDrawable(Color.parseColor("#80929F"))
                    listView.choiceMode = ListView.CHOICE_MODE_SINGLE

                    // 취소 버튼 색
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        ?.setTextColor(Color.parseColor("#80929F"))
                }

                // 리스트 클릭
                listView.setOnItemClickListener { _, _, pos, _ ->
                    loadSavedConfiguration(configs[pos].slots)
                    dialog.dismiss()
                }
                // 편집 버튼 클릭
                btnEdit.setOnClickListener {
                    dialog.dismiss()
                    startActivity(Intent(this, ItemSelectionActivity::class.java))
                }

                dialog.show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "구성 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }



    override fun onChampionSelected(championId: String) {
        currentChampionId = championId
        loadChampionData(championId, currentLevel)
    }
}
