package com.example.yumi2

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.yumi2.model.Item
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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

        // 미리 6개의 빈 슬롯 생성 (보여주기 전용)
        createBlankItemSlots()

        championSelector.setOnClickListener {
            val dialog = ChampionSelectionDialog()
            dialog.show(supportFragmentManager, "ChampionSelectionDialog")
        }

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

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

        // "불러오기" 버튼: 저장된 구성 불러오기 모달 호출
        btnLoadFavoriteItems.text = "불러오기"
        btnLoadFavoriteItems.setOnClickListener {
            showLoadConfigurationsDialog()
        }
    }

    private fun createBlankItemSlots() {
        itemSlotContainer.removeAllViews()
        for (i in 0 until 6) {
            val imageView = ImageView(this)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            imageView.layoutParams = params
            imageView.adjustViewBounds = true
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.setImageResource(R.drawable.placeholder_image)
            itemSlotContainer.addView(imageView)
        }
    }

    private fun loadChampionData(championId: String, level: Int) {
        db.collection("champions").document(championId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.data != null) {
                    val championData = document.data!!
                    val name = championData["name"] as? String ?: ""
                    val baseStats = championData["base_stats"] as? Map<String, Number>
                    val growthStats = championData["growth_stats"] as? Map<String, Number>
                    val extraStats = championData["item_stats"] as? Map<String, Number>
                    // Firestore에 저장한 레벨 18 기준 최종 공격속도 읽기
                    val finalAttackSpeed = championData["final_attack_speed"] as? Number
                    val portraitUrl = championData["portrait_url"] as? String

                    championNameText.text = name

                    if (!portraitUrl.isNullOrEmpty()) {
                        val selectorText = championSelector.findViewById<TextView>(R.id.championSelectorText)
                        selectorText?.visibility = View.GONE
                        var imageView = championSelector.findViewById<ImageView>(R.id.championImage)
                        if (imageView == null) {
                            imageView = ImageView(this)
                            imageView.id = R.id.championImage
                            imageView.layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                            championSelector.addView(imageView)
                        }
                        Glide.with(this).load(portraitUrl).into(imageView)
                    }
                    // 여기서 finalAttackSpeed를 전달합니다.
                    updateStats(level, baseStats, growthStats, extraStats, finalAttackSpeed)
                } else {
                    Toast.makeText(this, "챔피언 데이터를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "데이터 불러오기 오류: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // updateStats: 챔피언의 기본+성장 stat, extraStats, 즐겨찾기 아이템 보너스를 합산하여 표시
    private fun updateStats(
        level: Int,
        baseStats: Map<String, Number>?,
        growthStats: Map<String, Number>?,
        extraStats: Map<String, Number>?,
        finalAttackSpeed: Number? = null
    ) {
        statsTable.removeAllViews()
        if (baseStats == null || growthStats == null) return

        // filterMapping: label -> 내부 키 (챔피언 API와 아이템 stat에 사용)
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

        // percentLabels: 값 뒤에 "%" 붙여야 하는 항목
        val percentLabels = setOf("치명타 확률", "기본 체력 재생", "기본 마나 재생", "체력 회복 및 보호막", "생명력 흡수", "강인함")

        // 챔피언 기본+성장 능력치 계산 함수 (없으면 0)
        fun calculateChampionStat(internalKey: String): Double {
            return if (internalKey == "attackspeed") {
                val baseAttackSpeed = baseStats["attackspeed"]?.toDouble() ?: 0.0
                if (finalAttackSpeed != null) {
                    val finalAS = finalAttackSpeed.toDouble()
                    val multiplier = finalAS / baseAttackSpeed  // 예: 1.013 / 0.625 ≒ 1.6208
                    // 보간 시, 선형 대신 x^p 형태의 비선형 보간 (p=1.124)
                    val interpolatedMultiplier = 1 + (multiplier - 1) * Math.pow((level - 1) / 17.0, 1.124)
                    baseAttackSpeed * interpolatedMultiplier
                } else {
                    // fallback: 기존 계산법 사용
                    val shownAS = baseStats["attackspeed"]?.toDouble() ?: 0.0
                    val growth = growthStats["attackspeed"]?.toDouble() ?: 0.0
                    val ratio = baseStats["attackspeedratio"]?.toDouble() ?: 1.0
                    val rawAS = shownAS / ratio
                    val scaledAS = rawAS * (1 + growth * (level - 1) * 0.0175)
                    scaledAS * ratio
                }
            } else {
                val base = baseStats[internalKey]?.toDouble() ?: 0.0
                val growth = growthStats[internalKey]?.toDouble() ?: 0.0
                base + growth * (level - 1)
            }
        }




        // extraStats 값 (없으면 0)
        fun getExtraStat(internalKey: String): Double {
            return extraStats?.get(internalKey)?.toDouble() ?: 0.0
        }

        // 즐겨찾기 아이템 보너스 계산: favoriteItems의 stat 문자열 파싱
        val itemBonusStats = mutableMapOf<String, Double>()
        for (item in favoriteItems) {
            item?.let {
                val bonusMap = parseItemStats(it.stats)
                for ((key, bonusValue) in bonusMap) {
                    itemBonusStats[key] = (itemBonusStats[key] ?: 0.0) + bonusValue
                }
            }
        }

        // filterMapping에 정의된 stat들에 대해 챔피언 능력치, extraStats, 아이템 보너스를 합산하여 표시
        for ((label, internalKey) in filterMapping) {
            val championValue = calculateChampionStat(internalKey)
            val extraValue = getExtraStat(internalKey)
            val bonusValue = itemBonusStats[internalKey] ?: 0.0
            val total = championValue + extraValue + bonusValue

            Log.d("UpdateStats", "$label: champ=$championValue, extra=$extraValue, bonus=$bonusValue, total=$total")
            // champrow_stat.xml 인플레이트하여 행 생성
            val rowView = LayoutInflater.from(this).inflate(R.layout.champrow_stat, statsTable, false) as TableRow
            val iconView = rowView.findViewById<ImageView>(R.id.statIcon)
            val statNameTextView = rowView.findViewById<TextView>(R.id.statName)
            val statValueTextView = rowView.findViewById<TextView>(R.id.statValue)

            // statIconMap: label -> drawable id
            val statIconMap = mapOf(
                "공격력" to R.drawable.lol_stat_attack,
                "주문력" to R.drawable.lol_stat_magic,
                "방어력" to R.drawable.lol_stat_armor,
                "마법 저항력" to R.drawable.lol_stat_magic_r,
                "공격 속도" to R.drawable.lol_stat_attack_speed,
                "이동 속도" to R.drawable.lol_filter_movement_speed,
                "기본 마나 재생" to R.drawable.lol_stat_manaregen,
                "기본 체력 재생" to R.drawable.lol_stat_hpregen,
                "치명타 확률" to R.drawable.lol_stat_crit_chance,
                "생명력 흡수" to R.drawable.lol_stat_life_steal,
                "스킬 가속" to R.drawable.lol_stat_skill_time,
                "방어구 관통력" to R.drawable.lol_stat_armor_p,
                "물리 관통력" to R.drawable.lol_stat_armor_p,
                "마법 관통력" to R.drawable.lol_stat_magic_p,
                "체력 회복 및 보호막" to R.drawable.lol_stat_has,
                "공격 사거리" to R.drawable.lol_stat_range,
                "강인함" to R.drawable.lol_stat_tenacity,
                "체력" to R.drawable.lol_stat_hp,
                "마나" to R.drawable.lol_stat_mana
            )

            val iconResId = statIconMap[label] ?: R.drawable.yumi_icon
            iconView.setImageResource(iconResId)


            statNameTextView.text = label
            statValueTextView.text = when {
                label == "공격 속도" -> String.format("%.3f", total)
                label in percentLabels -> String.format("%.0f%%", total)
                else -> String.format("%.0f", total)
            }

            statsTable.addView(rowView)
        }
    }

    // parseItemStats: 아이템 stat 문자열 파싱 및 내부 키 변환
    private fun parseItemStats(statsString: String): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        val cleaned = statsString.trim().removePrefix("{").removeSuffix("}")
        val pairs = cleaned.split(",")
        for (pair in pairs) {
            val keyValue = pair.split("=")
            if (keyValue.size == 2) {
                var key = keyValue[0].trim()
                // 내부 키 변환: 아이템 stat 문자열에 따라 조정
                key = when (key) {
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
                    "기본 체력 재생" -> "hpregen"
                    "기본 마나 재생" -> "manaregen"
                    "체력" -> "hp"
                    "마나" -> "mp"
                    "생명력 흡수" -> "lifesteal"
                    "공격 사거리" -> "attackrange"
                    "강인함" -> "tenacity"
                    else -> key
                }
                val value = keyValue[1].trim().replace("%", "").toDoubleOrNull() ?: 0.0
                result[key] = result.getOrDefault(key, 0.0) + value
            }
        }
        return result
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
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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
        db.collection("users").document(uid).collection("savedConfigurations")
            .get()
            .addOnSuccessListener { snapshot ->
                val configList = snapshot.documents.mapNotNull { document ->
                    val configName = document.getString("configName")
                    val slots = document.get("slots") as? List<String?>
                    if (configName != null && slots != null) {
                        Pair(configName, slots)
                    } else null
                }
                if (configList.isEmpty()) {
                    Toast.makeText(this, "저장된 구성이 없습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val customView = LayoutInflater.from(this).inflate(R.layout.dialog_load_configurations, null)
                val listView = customView.findViewById<ListView>(R.id.listViewConfigurations)
                val btnEditSet = customView.findViewById<Button>(R.id.btnEditItemSet)
                val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, configList.map { it.first })
                listView.adapter = adapter
                val dialog = AlertDialog.Builder(this)
                    .setTitle("불러올 구성을 선택하세요")
                    .setView(customView)
                    .setNegativeButton("취소", null)
                    .create()
                listView.setOnItemClickListener { _, _, position, _ ->
                    val selectedConfig = configList[position]
                    loadSavedConfiguration(selectedConfig.second)
                    dialog.dismiss()
                }
                btnEditSet.setOnClickListener {
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
