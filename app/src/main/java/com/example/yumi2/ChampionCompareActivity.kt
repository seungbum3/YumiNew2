package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.yumi2.model.Item
import com.google.firebase.firestore.FirebaseFirestore

class ChampionCompareActivity : AppCompatActivity(), ChampionSelectionDialog.ChampionSelectionListener {

    private val filterMapping = listOf(
        "체력" to "hp", "마나" to "mp", "공격력" to "attackdamage", "주문력" to "abilitypower",
        "방어력" to "armor", "마법 저항력" to "spellblock", "공격 속도" to "attackspeed",
        "스킬 가속" to "cooldownreduction", "치명타 확률" to "crit", "이동 속도" to "movespeed",
        "물리 관통력" to "attackpenetration", "방어구 관통력" to "armorpenetration", "마법 관통력" to "magicpenetration",
        "기본 체력 재생" to "hpregen", "기본 마나 재생" to "manaregen", "생명력 흡수" to "lifesteal",
        "체력 회복 및 보호막" to "has", "공격 사거리" to "attackrange", "강인함" to "tenacity"
    )
    private val percentLabels = setOf(
        "치명타 확률", "기본 체력 재생", "기본 마나 재생", "체력 회복 및 보호막", "생명력 흡수", "강인함"
    )

    private val db = FirebaseFirestore.getInstance()
    private var champion1Id: String? = null
    private var champion2Id: String? = null
    private var champion1Level = 1
    private var champion2Level = 1
    private var champion1Items: MutableList<Item?> = MutableList(6) { null }
    private var champion2Items: MutableList<Item?> = MutableList(6) { null }
    private var champion1Data: Map<String, Any>? = null
    private var champion2Data: Map<String, Any>? = null



    private var isSelectingLeft = true
    private var currentItemSelectIndex = -1 // 아이템 슬롯 클릭 시 구분용

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.champion_compare)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            val intent = Intent(this, Main3Activity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        // 챔피언1 UI 참조
        val champion1Image = findViewById<ImageView>(R.id.champion1Image)
        val champion1Name = findViewById<TextView>(R.id.champion1Name)
        val champion1LevelText = findViewById<TextView>(R.id.champion1LevelText)
        val champion1ItemsGrid = findViewById<GridLayout>(R.id.champion1Items)

        // 챔피언2 UI 참조
        val champion2Image = findViewById<ImageView>(R.id.champion2Image)
        val champion2Name = findViewById<TextView>(R.id.champion2Name)
        val champion2LevelText = findViewById<TextView>(R.id.champion2LevelText)
        val champion2ItemsGrid = findViewById<GridLayout>(R.id.champion2Items)

        // 이름/레벨 디폴트값 세팅
        champion1Name.text = "챔피언"
        champion2Name.text = "챔피언"
        champion1LevelText.text = "Lv. $champion1Level"
        champion2LevelText.text = "Lv. $champion2Level"

        findViewById<TextView>(R.id.champion1ImageHint).visibility = View.VISIBLE
        findViewById<TextView>(R.id.champion2ImageHint).visibility = View.VISIBLE

        // 비교 표
        val statsContainer = findViewById<LinearLayout>(R.id.compareStatsContainer)

        // 챔피언 선택 (이미지 클릭)
        champion1Image.setOnClickListener {
            isSelectingLeft = true
            ChampionSelectionDialog().show(supportFragmentManager, "Champion1Dialog")
        }
        champion2Image.setOnClickListener {
            isSelectingLeft = false
            ChampionSelectionDialog().show(supportFragmentManager, "Champion2Dialog")
        }

        champion1LevelText.setOnClickListener {
            showLevelSelectDialog(true)
        }
        champion2LevelText.setOnClickListener {
            showLevelSelectDialog(false)
        }

        // --- 아이템 6칸 세팅(빈칸 + 클릭 이벤트 추가) ---
        setItemSlots(champion1ItemsGrid, champion1Items, isLeft = true)
        setItemSlots(champion2ItemsGrid, champion2Items, isLeft = false)

        // 최초 표 초기화
        updateStatsCompareUI()
    }

    // 레벨 다이얼로그 (NumberPicker, 1~18)
    private fun showLevelSelectDialog(isLeft: Boolean) {
        val levels = (1..18).map { "Lv. $it" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("레벨 선택")
            .setItems(levels) { _, which ->
                val newLevel = which + 1
                if (isLeft) {
                    champion1Level = newLevel
                    findViewById<TextView>(R.id.champion1LevelText).text = "Lv. $champion1Level"
                } else {
                    champion2Level = newLevel
                    findViewById<TextView>(R.id.champion2LevelText).text = "Lv. $champion2Level"
                }
                updateStatsCompareUI()
            }
            .show()
    }

    override fun onChampionSelected(championId: String) {
        if (isSelectingLeft) {
            champion1Id = championId
            loadChampionData(championId, true)
            findViewById<TextView>(R.id.champion1ImageHint).visibility = View.GONE
        } else {
            champion2Id = championId
            loadChampionData(championId, false)
            findViewById<TextView>(R.id.champion2ImageHint).visibility = View.GONE
        }
    }

    // 챔피언 데이터 파이어스토어에서 불러오기 (좌/우 구분)
    private fun loadChampionData(championId: String, isLeft: Boolean) {
        db.collection("champions").document(championId).get()
            .addOnSuccessListener { doc ->
                val data = doc.data ?: return@addOnSuccessListener
                if (isLeft) {
                    champion1Data = data
                    findViewById<TextView>(R.id.champion1Name).text = data["name"] as? String ?: "챔피언 선택"
                    loadChampionIcon(data["name"] as? String, R.id.champion1Image)
                } else {
                    champion2Data = data
                    findViewById<TextView>(R.id.champion2Name).text = data["name"] as? String ?: "챔피언 선택"
                    loadChampionIcon(data["name"] as? String, R.id.champion2Image)
                }
                updateStatsCompareUI()
            }
    }

    private fun loadChampionIcon(name: String?, imageViewId: Int) {
        if (name.isNullOrEmpty()) return
        db.collection("champion_choice").whereEqualTo("name", name)
            .get().addOnSuccessListener { snap ->
                val iconUrl = snap.documents.firstOrNull()?.getString("iconUrl")
                if (!iconUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(iconUrl)
                        .into(findViewById(imageViewId))
                }
            }
    }

    // 아이템 6칸 표시(빈칸: ic_placeslot) + 클릭 이벤트(아이템 선택창)
    private fun setItemSlots(grid: GridLayout, items: MutableList<Item?>, isLeft: Boolean) {
        grid.removeAllViews()
        for (i in 0 until 6) {
            val iv = ImageView(this).apply {
                val size = (36 * resources.displayMetrics.density).toInt()
                layoutParams = ViewGroup.LayoutParams(size, size)
                scaleType = ImageView.ScaleType.FIT_CENTER

                if (items[i] != null) {
                    Glide.with(this@ChampionCompareActivity)
                        .load(items[i]?.imageUrl)
                        .into(this)
                } else {
                    setImageResource(R.drawable.ic_placeslot)
                }

                setOnClickListener {
                    if (items[i] != null) {
                        // 이미 아이템이 들어가 있으면 누르면 제거!
                        items[i] = null
                        setItemSlots(grid, items, isLeft)
                        updateStatsCompareUI()
                    } else {
                        // 아이템이 없을 때만 다이얼로그 띄워서 선택
                        val dialog = ItemSelectionDialog()
                        dialog.setListener(object : ItemSelectionDialog.ItemSelectionListener {
                            override fun onItemSelected(item: Item) {
                                items[i] = item
                                setItemSlots(grid, items, isLeft)
                                updateStatsCompareUI()
                            }
                        })
                        dialog.show(supportFragmentManager, "ItemDialog")
                    }
                }
            }
            grid.addView(iv)
        }
    }



    // 아이템 선택 결과 받기 (ItemSelectionActivity에서 setResult로 넘기면 됨)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val itemId = data.getStringExtra("selectedItemId")
            if (itemId != null) {
                db.collection("items").document(itemId).get()
                    .addOnSuccessListener { doc ->
                        val item = doc.toObject(Item::class.java)
                        if (item != null) {
                            if (requestCode == 1001) {
                                champion1Items[currentItemSelectIndex] = item
                                setItemSlots(findViewById(R.id.champion1Items), champion1Items, true)
                            } else {
                                champion2Items[currentItemSelectIndex] = item
                                setItemSlots(findViewById(R.id.champion2Items), champion2Items, false)
                            }
                            updateStatsCompareUI()
                        }
                    }
            }
        }
    }

    // --- 이하 기존 코드 동일 (계산, 표 등) ---
    private fun updateStatsCompareUI() {
        val statsContainer = findViewById<LinearLayout>(R.id.compareStatsContainer)
        statsContainer.removeAllViews()

        val statMap1 = calcChampionStats(champion1Data, champion1Level, champion1Items)
        val statMap2 = calcChampionStats(champion2Data, champion2Level, champion2Items)

        for ((label, key) in filterMapping) {
            val v1 = statMap1[key] ?: 0.0
            val v2 = statMap2[key] ?: 0.0
            statsContainer.addView(createCompareStatRow(label, v1, v2))
        }
    }

    private fun calcChampionStats(
        data: Map<String, Any>?,
        level: Int,
        items: List<Item?>
    ): Map<String, Double> {
        if (data == null) return emptyMap()
        val baseStats = data["base_stats"] as? Map<String, Number> ?: return emptyMap()
        val growthStats = data["growth_stats"] as? Map<String, Number> ?: emptyMap()
        val extraStats = data["item_stats"] as? Map<String, Number> ?: emptyMap()

        val itemBonus = mutableMapOf<String, Double>()
        items.filterNotNull().forEach { item ->
            parseItemStats(item.stats).forEach { (k, v) ->
                itemBonus[k] = (itemBonus[k] ?: 0.0) + v
            }
        }

        val result = mutableMapOf<String, Double>()
        for ((_, key) in filterMapping) {
            val b = baseStats[key]?.toDouble() ?: 0.0
            val g = growthStats[key]?.toDouble() ?: 0.0
            val e = extraStats[key]?.toDouble() ?: 0.0
            val i = itemBonus[key] ?: 0.0
            result[key] = b + g * (level - 1) + e + i
        }
        return result
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

    private fun createCompareStatRow(label: String, value1: Double, value2: Double): LinearLayout {
        val context = this
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(4, 8, 4, 8)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val maxVal = maxOf(value1, value2)
        val weight1 = if (maxVal == 0.0) 1f else (value1 / maxVal).toFloat() * 2f
        val weight2 = if (maxVal == 0.0) 1f else (value2 / maxVal).toFloat() * 2f

        val labelText = TextView(context).apply {
            text = label
            setTextColor(ContextCompat.getColor(context, R.color.black))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            gravity = android.view.Gravity.START
        }
        val leftValue = TextView(context).apply {
            text = if (label in percentLabels) "${value1.toInt()}%" else "${value1.toInt()}"
            setTextColor(ContextCompat.getColor(context, R.color.black))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.END
        }
        val barContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, 18.dp, 3.5f)
            setPadding(8, 0, 8, 0)
        }
        val bar1 = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 18.dp, weight1)
            setBackgroundColor(ContextCompat.getColor(context, R.color.yumi_blue))
        }
        val bar2 = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 18.dp, weight2)
            setBackgroundColor(ContextCompat.getColor(context, R.color.yumi_red))
        }
        if (value1 > value2) {
            leftValue.setTypeface(null, android.graphics.Typeface.BOLD)
            bar1.alpha = 1f; bar2.alpha = 0.4f
        } else if (value2 > value1) {
            bar2.alpha = 1f; bar1.alpha = 0.4f
        }
        barContainer.addView(bar1)
        barContainer.addView(bar2)
        val rightValue = TextView(context).apply {
            text = if (label in percentLabels) "${value2.toInt()}%" else "${value2.toInt()}"
            setTextColor(ContextCompat.getColor(context, R.color.black))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.END
        }
        // 순서: [이름][좌측값][막대][우측값]
        row.addView(labelText)
        row.addView(leftValue)
        row.addView(barContainer)
        row.addView(rightValue)
        return row
    }


    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
