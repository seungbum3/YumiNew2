package com.example.yumi2

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.adapter.ItemAdapter
import com.example.yumi2.model.Item
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ItemSelectionActivity : AppCompatActivity() {

    private lateinit var itemRecyclerView: RecyclerView
    private lateinit var itemAdapter: ItemAdapter
    private lateinit var slotRecyclerView: RecyclerView
    private val selectedFilters = mutableSetOf<String>()

    lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_selection)

        // 현재 로그인한 사용자 UID 출력 예시
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Toast.makeText(this, "로그인 정보가 없습니다!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d("UserUID", "현재 로그인한 사용자의 UID: $uid")
        itemRecyclerView = findViewById(R.id.itemRecyclerView)
        slotRecyclerView = findViewById(R.id.slotRecyclerView)
        firestore = FirebaseFirestore.getInstance()

        val btnBack: ImageView = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        // 상단 6칸 슬롯 RecyclerView 설정
        setupSlotRecyclerView()

        // 전체 아이템 목록 RecyclerView 설정
        itemRecyclerView.layoutManager = GridLayoutManager(this, 5)
        fetchItemsFromFirestore()

        val searchView = findViewById<SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                itemAdapter.filter.filter(newText ?: "")
                return true
            }
        })

        // 저장, 불러오기 버튼 참조
        val btnSaveSlots = findViewById<Button>(R.id.btnSaveSlots)
        val btnLoadSlots = findViewById<Button>(R.id.btnLoadSlots)

        btnSaveSlots.setOnClickListener { saveConfiguration(uid) }
        btnLoadSlots.setOnClickListener { showLoadConfigurationsDialog(uid) }

        fetchItemsFromFirestore()

        val filterAttack = findViewById<ImageButton>(R.id.filter_attack)
        val filterCritical = findViewById<ImageButton>(R.id.filter_Critical)
        val filterAttack_speed = findViewById<ImageButton>(R.id.filter_attack_speed)
        val filterOnhit = findViewById<ImageButton>(R.id.filter_Onhit)
        val filterArmor_p = findViewById<ImageButton>(R.id.filter_Armor_p)
        val filterAvility = findViewById<ImageButton>(R.id.filter_Avility)
        val filterMana = findViewById<ImageButton>(R.id.filter_Mana)
        val filterMagic_p = findViewById<ImageButton>(R.id.filter_Magic_p)
        val filterHealth = findViewById<ImageButton>(R.id.filter_Health)
        val filterArmor = findViewById<ImageButton>(R.id.filter_Armor)
        val filterMagic_r = findViewById<ImageButton>(R.id.filter_Magic_r)
        val filterCooldown = findViewById<ImageButton>(R.id.filter_cooldown)
        val filterMovement = findViewById<ImageButton>(R.id.filter_Movement)
        val filterOmnivamp = findViewById<ImageButton>(R.id.filter_Omnivamp)

        setupFilterButtons(
            filterAttack to "공격력",
            filterCritical to "치명타 확률",
            filterAttack_speed to "공격 속도",
            filterOnhit to "적중 시 효과",
            filterArmor_p to "물리 관통력",
            filterAvility to "주문력",
            filterMana to "마나 및 재생",
            filterMagic_p to "마법 관통력",
            filterHealth to "체력 및 재생",
            filterArmor to "방어력",
            filterMagic_r to "마법 저항력",
            filterCooldown to "스킬 가속",
            filterMovement to "이동 속도",
            filterOmnivamp to "생명력 흡수 및 흡혈"
        )
    }

    private fun setupFilterButtons(vararg filters: Pair<ImageButton, String>) {
        for ((button, category) in filters) {
            button.setOnClickListener {
                if (selectedFilters.contains(category)) {
                    selectedFilters.remove(category)
                    button.setBackgroundResource(R.drawable.filter_default) // 선택 해제
                } else {
                    selectedFilters.add(category)
                    button.setBackgroundResource(R.drawable.filter_selected) // 선택됨
                }
                applyFilters() // 🔥 필터 적용
            }
        }
    }

    private fun applyFilters() {
        Log.d("FILTER", "현재 선택된 필터: $selectedFilters")
        if (selectedFilters.isEmpty()) {
            itemAdapter.resetFilters() // 모든 필터 해제 시 전체 목록 표시
        } else {
            itemAdapter.filterByMultipleCategories(selectedFilters)
        }
    }

    // 저장된 구성 데이터 클래스 (6칸 슬롯)
    data class SavedConfiguration(
        val configName: String = "",  // 구성 이름(문서 ID로 활용)
        val slots: List<Item?> = List(6) { null }  // 6칸 슬롯 데이터
    )

    // 저장 기능: 최소 1개 이상의 아이템이 선택되어야 저장
    private fun saveConfiguration(uid: String) {
        val adapter = slotRecyclerView.adapter as? SlotAdapter ?: return
        val currentSlots: List<Item?> = adapter.getSlotItems()

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
                "slots" to currentSlots.map { it?.id }  // 여기서 각 slotItem이 Item으로 인식되어야 함
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

    inner class SavedBuildAdapter(
        private val context: ItemSelectionActivity,
        private val configList: MutableList<SavedConfiguration>
    ) : BaseAdapter() {
        override fun getCount(): Int = configList.size
        override fun getItem(position: Int): Any = configList[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_saved_build, parent, false)
            val tvBuildName = view.findViewById<TextView>(R.id.tvBuildName)
            val ivDelete = view.findViewById<ImageView>(R.id.ivDelete)

            val config = configList[position]
            tvBuildName.text = config.configName

            // 리스트 아이템 클릭 시 구성 불러오기
            view.setOnClickListener {
                val adapter = slotRecyclerView.adapter as? SlotAdapter
                adapter?.setSlots(config.slots)
                Toast.makeText(context, "구성 불러오기 성공", Toast.LENGTH_SHORT).show()
                // 다이얼로그 닫기
                (parent as? ListView)?.let { listView ->
                    (listView.parent as? AlertDialog)?.dismiss()
                }
            }

            // 삭제 버튼 클릭 시 해당 구성 삭제
            ivDelete.setOnClickListener {
                val userId = context.getUserId()
                if (userId.isNotEmpty()) {
                    firestore.collection("users")
                        .document(userId)
                        .collection("savedConfigurations")
                        .document(config.configName)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(context, "구성 삭제 성공", Toast.LENGTH_SHORT).show()
                            configList.removeAt(position)
                            notifyDataSetChanged()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "구성 삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            return view
        }
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
                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, configList.map { it.first })
                listView.adapter = adapter

                val dialog = AlertDialog.Builder(this)
                    .setTitle("불러올 구성을 선택하세요")
                    .setView(listView)
                    .setNegativeButton("취소", null)
                    .create()

                listView.setOnItemClickListener { _, _, position, _ ->
                    val selectedConfig = configList[position]
                    val slotAdapter = slotRecyclerView.adapter as? SlotAdapter
                    val slotsList: List<Item?> = selectedConfig.second.orEmpty().mapNotNull { id: String? ->
                        id?.let { Item(it, "", "", emptyList(), 0) }
                    }
                    slotAdapter?.setSlots(slotsList)
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

    private fun setupSlotRecyclerView() {
        slotRecyclerView.layoutManager = GridLayoutManager(this, 6)
        slotRecyclerView.adapter = SlotAdapter()
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

                    itemList.add(Item(id, name, imageUrl, tags, cost))
                }
                itemList.sortBy { it.cost }

                itemAdapter = ItemAdapter(itemList) { item ->
                    addItemToSlot(item)
                }
                itemRecyclerView.adapter = itemAdapter
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "아이템 가져오기 실패", e)
            }
    }

    private fun addItemToSlot(item: Item) {
        val adapter = slotRecyclerView.adapter as? SlotAdapter
        adapter?.addItemToSlot(item)
    }

    // 저장 기능: 현재 슬롯에 들어있는 아이템들을 Firestore의 "savedConfigurations" 하위 컬렉션에 저장
    private fun saveSlots() {
        val adapter = slotRecyclerView.adapter as? SlotAdapter ?: return
        val slots = adapter.getSlotItems() // SlotAdapter에서 추가할 메소드
        val userId = getUserId()  // 로그인한 유저 ID 가져오기
        if (userId.isEmpty()) {
            Log.e("SaveSlots", "저장할 유저 ID가 없습니다.")
            return
        }
        val savedItemsCollection = firestore.collection("users")
            .document(userId)
            .collection("savedConfigurations")

        // 각 슬롯 인덱스에 대해 저장 (빈 슬롯은 삭제)
        for (index in slots.indices) {
            val slotItem = slots[index]
            val docId = "slot$index"
            if (slotItem != null) {
                // slotItem을 명시적으로 Item으로 캐스팅
                val item = slotItem as? Item
                if (item != null) {
                    val data = hashMapOf(
                        "id" to item.id,
                        "name" to item.name,
                        "imageUrl" to item.imageUrl,
                        "slotIndex" to index
                    )
                    savedItemsCollection.document(docId).set(data)
                        .addOnSuccessListener {
                            Log.d("SaveSlots", "Slot $index 저장 성공")
                        }
                        .addOnFailureListener { e ->
                            Log.e("SaveSlots", "Slot $index 저장 실패", e)
                        }
                }
            } else {
                savedItemsCollection.document(docId).delete()
                    .addOnSuccessListener {
                        Log.d("SaveSlots", "Slot $index 삭제 성공")
                    }
                    .addOnFailureListener { e ->
                        Log.e("SaveSlots", "Slot $index 삭제 실패", e)
                    }
            }
        }
    }

    // 불러오기 기능: Firestore에서 "savedConfigurations" 데이터를 불러와 슬롯 상태 업데이트
    private fun loadSlots() {
        val userId = getUserId()
        if (userId.isEmpty()) {
            Log.e("LoadSlots", "불러올 유저 ID가 없습니다.")
            return
        }
        val savedItemsCollection = firestore.collection("users")
            .document(userId)
            .collection("savedConfigurations")
        savedItemsCollection.get()
            .addOnSuccessListener { querySnapshot ->
                val loadedSlots = MutableList<Item?>(6) { null }
                for (document in querySnapshot.documents) {
                    val slotIndex = document.getLong("slotIndex")?.toInt() ?: continue
                    if (slotIndex in 0..5) {
                        val item = document.toObject(Item::class.java)
                        loadedSlots[slotIndex] = item
                    }
                }
                val adapter = slotRecyclerView.adapter as? SlotAdapter
                adapter?.setSlots(loadedSlots)
            }
            .addOnFailureListener { e ->
                Log.e("LoadSlots", "저장된 슬롯 불러오기 실패", e)
            }
    }

    // SharedPreferences에서 로그인한 유저 ID를 가져오는 함수
    private fun getUserId(): String {
        val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        return sharedPref.getString("loggedInUserId", "") ?: ""
    }
}
