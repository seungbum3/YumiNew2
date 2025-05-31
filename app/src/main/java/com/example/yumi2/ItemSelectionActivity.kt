package com.example.yumi2

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.yumi2.adapter.ItemAdapter
import com.example.yumi2.model.Item
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ItemSelectionActivity : AppCompatActivity() {

    private lateinit var itemRecyclerView: RecyclerView
    private lateinit var slotRecyclerView: RecyclerView
    private lateinit var toggleQuickSlot: ToggleButton
    private lateinit var itemAdapter: ItemAdapter
    private val selectedFilters = mutableSetOf<String>()

    lateinit var firestore: FirebaseFirestore
    private var isQuickSlotMode = false  // 퀵 슬롯 모드 여부

    private val statIconMap = mapOf(
        "공격력" to R.drawable.lol_stat_attack,
        "주문력" to R.drawable.lol_stat_magic ,
        "방어력" to R.drawable.lol_stat_armor,
        "마법 저항력" to R.drawable.lol_stat_magic_r,
        "공격 속도" to R.drawable.lol_stat_attack_speed,
        "이동 속도" to R.drawable.lol_filter_movement_speed,
        "기본 마나 재생" to R.drawable.lol_stat_manaregen,
        "기본 체력 재생" to R.drawable.lol_stat_hpregen,
        "체력" to R.drawable.lol_stat_hp,
        "마나" to R.drawable.lol_stat_mana,
        "치명타 확률" to R.drawable.lol_stat_crit_chance,
        "생명력 흡수" to R.drawable.lol_stat_life_steal,
        "스킬 가속" to R.drawable.lol_stat_skill_time,
        "방어구 관통력" to R.drawable.lol_stat_armor_p,
        "물리 관통력" to R.drawable.lol_stat_armor_p,
        "마법 관통력" to R.drawable.lol_stat_magic_p,
        "강인함" to R.drawable.lol_stat_tenacity,
        "초당 골드" to R.drawable.lol_stat_coin,
        "체력 회복 및 보호막" to R.drawable.lol_stat_has
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_selection)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Toast.makeText(this, "로그인 정보가 없습니다!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d("UserUID", "현재 로그인한 사용자의 UID: $uid")

        itemRecyclerView = findViewById(R.id.itemRecyclerView)
        slotRecyclerView = findViewById(R.id.slotRecyclerView)
        toggleQuickSlot = findViewById(R.id.toggleQuickSlot)
        firestore = FirebaseFirestore.getInstance()

        val btnBack: ImageView = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        // 퀵 슬롯 모드 토글
        toggleQuickSlot.setOnCheckedChangeListener { _, isChecked ->
            isQuickSlotMode = isChecked
        }

        // 상단 6칸 슬롯 RecyclerView 설정
        setupSlotRecyclerView()

        // 전체 아이템 목록 RecyclerView 설정
        itemRecyclerView.layoutManager = GridLayoutManager(this, 5)
        fetchItemsFromFirestore()

        // 필터 및 검색 기능 유지
        setupSearchAndFilters()

        // 저장/불러오기 버튼 설정
        setupSaveAndLoadButtons(uid)
    }



    private fun setupSearchAndFilters() {
        val searchView = findViewById<SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                itemAdapter.filter.filter(newText ?: "")
                return true
            }
        })

        val filters = listOf(
            R.id.filter_attack to "공격력",
            R.id.filter_Critical to "치명타 확률",
            R.id.filter_attack_speed to "공격 속도",
            R.id.filter_Onhit to "적중 시 효과",
            R.id.filter_Armor_p to "물리 관통력",
            R.id.filter_Avility to "주문력",
            R.id.filter_Mana to "마나 및 재생",
            R.id.filter_Magic_p to "마법 관통력",
            R.id.filter_Health to "체력 및 재생",
            R.id.filter_Armor to "방어력",
            R.id.filter_Magic_r to "마법 저항력",
            R.id.filter_cooldown to "스킬 가속",
            R.id.filter_Movement to "이동 속도",
            R.id.filter_Omnivamp to "생명력 흡수 및 흡혈"
        )

        filters.forEach { (id, category) ->
            val button = findViewById<ImageButton>(id)
            button.setOnClickListener {
                if (selectedFilters.contains(category)) {
                    selectedFilters.remove(category)
                    button.setBackgroundResource(R.drawable.filter_default)
                } else {
                    selectedFilters.add(category)
                    button.setBackgroundResource(R.drawable.filter_selected)
                }
                applyFilters()
            }
        }
    }

    private fun applyFilters() {
        if (selectedFilters.isEmpty()) {
            itemAdapter.resetFilters()
        } else {
            itemAdapter.filterByMultipleCategories(selectedFilters)
        }
    }

    private fun fetchItemsFromFirestore() {
        firestore.collection("items")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                val itemList = mutableListOf<Item>()
                for (document in documents) {
                    val id = document.getString("id") ?: ""
                    val name = document.getString("name") ?: "알 수 없음"
                    val imageUrl = document.getString("imageUrl") ?: ""
                    val tags = document.get("tags") as? List<String> ?: emptyList()
                    val cost = document.getLong("cost")?.toInt() ?: 0
                    val stats = document.get("stats")?.toString() ?: "능력치 정보 없음"
                    val effect = document.getString("plaintext") ?: "효과 정보 없음"
                    val description = document.getString("description") ?: "설명 없음"

                    itemList.add(Item(id, name, imageUrl, tags, cost, stats, effect, description))
                }
                itemList.sortBy { it.cost }

                itemAdapter = ItemAdapter(itemList) { item ->
                    onItemClicked(item)
                }
                itemRecyclerView.adapter = itemAdapter
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "아이템 가져오기 실패", e)
            }
    }

    private fun onItemClicked(item: Item) {
        if (isQuickSlotMode) {
            addItemToSlot(item)
        } else {
            showItemDetailDialog(item)
        }
    }

    private fun parseStats(statsString: String): List<Pair<String, String>> {
        val cleanedString = statsString.trim().removePrefix("{").removeSuffix("}")
        return cleanedString.split(",").mapNotNull {
            val parts = it.split("=")
            if (parts.size == 2) Pair(parts[0].trim(), parts[1].trim()) else null
        }
    }



    private fun showItemDetailDialog(item: Item) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_item_detail, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // 기존 UI 요소
        val itemName = dialogView.findViewById<TextView>(R.id.dialogItemName)
        val itemImage = dialogView.findViewById<ImageView>(R.id.dialogItemImage)
        val itemCost = dialogView.findViewById<TextView>(R.id.dialogItemCost)
        // 능력치는 TextView 대신 statsContainer 컨테이너를 사용
        val statsContainer = dialogView.findViewById<LinearLayout>(R.id.statsContainer)
        val itemEffect = dialogView.findViewById<TextView>(R.id.dialogItemEffect)
        val itemDescription = dialogView.findViewById<TextView>(R.id.dialogItemDescription)
        val btnAddToSlot = dialogView.findViewById<Button>(R.id.btnAddToSlot)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        itemName.text = item.name
        itemCost.text = "가격: ${item.cost} 골드"
        itemEffect.text = "효과: ${item.effect}"
        itemDescription.text = "설명: ${item.description}"

        Glide.with(this)
            .load(item.imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.error_image)
            .into(itemImage)

        // stats 문자열을 파싱해서 statsContainer에 동적으로 추가
        statsContainer.removeAllViews()  // 기존 뷰 초기화
        val statList = parseStats(item.stats)
        for ((key, value) in statList) {
            // 여기서 각 능력치 키와 값을 로그로 출력합니다.
            Log.d("ItemStats", "능력치 키: $key, 값: $value")

            // item_stat.xml 레이아웃을 동적으로 인플레이트
            val statItemView =
                LayoutInflater.from(this).inflate(R.layout.item_stat, statsContainer, false)
            val statIcon = statItemView.findViewById<ImageView>(R.id.statIcon)
            val statValue = statItemView.findViewById<TextView>(R.id.statValue)

            // statIconMap에서 아이콘을 찾아 설정 (없으면 기본 아이콘 사용)
            val iconResId = statIconMap[key] ?: R.drawable.yumi_icon
            statIcon.setImageResource(iconResId)
            statValue.text = value

            statsContainer.addView(statItemView)
        }

        btnAddToSlot.setOnClickListener {
            addItemToSlot(item)
            dialog.dismiss()
        }
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun addItemToSlot(item: Item) {
        val adapter = slotRecyclerView.adapter as? SlotAdapter
        adapter?.addItemToSlot(item)
    }

    private fun setupSlotRecyclerView() {
        slotRecyclerView.layoutManager = GridLayoutManager(this, 6)
        slotRecyclerView.adapter = SlotAdapter()
    }

    private fun setupSaveAndLoadButtons(uid: String) {
        val btnSaveSlots = findViewById<Button>(R.id.btnSaveSlots)
        val btnLoadSlots = findViewById<Button>(R.id.btnLoadSlots)

        btnSaveSlots.setOnClickListener { saveConfiguration(uid) }
        btnLoadSlots.setOnClickListener { showLoadConfigurationsDialog(uid) }
    }

    private fun saveConfiguration(uid: String) {
        val adapter = slotRecyclerView.adapter as? SlotAdapter ?: return
        val currentSlots = adapter.getSlotItems()

        if (currentSlots.all { it == null }) {
            Toast.makeText(this, "최소 하나 이상의 아이템을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val customView = layoutInflater.inflate(R.layout.custom_save_dialog, null)
        val editText = customView.findViewById<EditText>(R.id.editTextDialogName)
        val btnSave = customView.findViewById<Button>(R.id.btnSave)
        val btnCancel = customView.findViewById<Button>(R.id.btnCancel)

        val dialog = AlertDialog.Builder(this)
            .setView(customView)
            .create()

        btnSave.setOnClickListener {
            val customName = editText.text.toString().trim()
            if (customName.isEmpty()) {
                Toast.makeText(this, "구성 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val config = hashMapOf(
                "configName" to customName,
                "slots" to currentSlots.map { it?.id }
            )

            firestore.collection("users")
                .document(uid)
                .collection("savedConfigurations")
                .document(customName)
                .set(config)
                .addOnSuccessListener {
                    Toast.makeText(this, "구성 저장 성공", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Log.e("SaveConfig", "구성 저장 실패", e)
                    Toast.makeText(this, "구성 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showLoadConfigurationsDialog(uid: String) {
        val configCollection = firestore.collection("users")
            .document(uid)
            .collection("savedConfigurations")

        configCollection.get()
            .addOnSuccessListener { snapshot ->
                val configList = snapshot.documents.mapNotNull { document ->
                    document.getString("configName")?.let { configName ->
                        configName to document.get("slots") as? List<String?>
                    }
                }.toMutableList()

                if (configList.isEmpty()) {
                    Toast.makeText(this, "저장된 구성이 없습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val listView = ListView(this)
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    configList.map { it.first })
                listView.adapter = adapter

                val dialog = AlertDialog.Builder(this)
                    .setTitle("불러올 구성을 선택하세요")
                    .setView(listView)
                    .setNegativeButton("취소", null)
                    .create()

                listView.setOnItemClickListener { _, _, position, _ ->
                    val selectedConfig = configList[position]
                    val slotAdapter = slotRecyclerView.adapter as? SlotAdapter

                    val fullItems = itemAdapter.getItemList()

                    // 불러올 슬롯 개수(최대 6개)에 맞춰서 비어있는 칸도 포함하여 리스트 구성
                    val loadedSlots = selectedConfig.second?.map { id ->
                        fullItems.find { it.id == id }
                    } ?: emptyList()

                    // 기존 슬롯 초기화 후 다시 아이템 설정
                    slotAdapter?.clearSlots()  // 슬롯 초기화 필수
                    slotAdapter?.setSlots(loadedSlots)

                    Toast.makeText(this, "구성 불러오기 성공", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }

                dialog.show()
            }
            .addOnFailureListener { e ->
                Log.e("LoadConfig", "구성 불러오기 실패", e)
                Toast.makeText(this, "구성 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

}
