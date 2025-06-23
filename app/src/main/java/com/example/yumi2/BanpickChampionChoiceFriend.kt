package com.example.yumi2

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.adapter.ChampionAdapter
import com.example.yumi2.adapter.ChampionAdapterFriend
import com.example.yumi2.model.ChampionData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class BanpickChampionChoiceFriend : DialogFragment() {

    companion object {
        var selectedChampions = MutableList(20) { "" }
        var allChampions = listOf<ChampionData>()
        var currentPickIndex = 0

        fun newInstance(
            roomId: String,
            currentTurn: String,
            myTeam: String,
            onChampionSelected: (ChampionData) -> Unit
        ): BanpickChampionChoiceFriend {
            val fragment = BanpickChampionChoiceFriend()
            fragment.roomId = roomId
            fragment.currentTurn = currentTurn
            fragment.onChampionSelected = onChampionSelected

            val args = Bundle()
            args.putString("myTeam", myTeam)
            fragment.arguments = args

            return fragment
        }

        fun resetSelections(size: Int = 20) {
            selectedChampions = MutableList(size) { "" }
        }
    }

    private var roomId: String? = null
    private var currentTurn: String? = null
    private var onChampionSelected: ((ChampionData) -> Unit)? = null
    private var currentRole: String? = null
    private var selectedChampion: ChampionData? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var championAdapter: ChampionAdapterFriend
    private lateinit var searchEditText: EditText

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val params = window.attributes
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.y = 140
            val rootView = dialog?.window?.decorView?.findViewById<View>(android.R.id.content)
            rootView?.setPadding(0, 0, 0, 0)
            window.attributes = params
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return inflater.inflate(R.layout.banpick_champion_choice_friend, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<ImageButton>(R.id.btn_close).setOnClickListener { dismiss() }

        view.findViewById<Button>(R.id.btn_confirm).setOnClickListener {
            selectedChampion?.let { champ ->
                if (roomId.isNullOrBlank() || currentTurn.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "방 정보가 없습니다", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val myTeam = arguments?.getString("myTeam") ?: "blue"
                val docRef = Firebase.firestore.collection("banpick_rooms").document(roomId!!)

                docRef.get().addOnSuccessListener { snapshot ->
                    if (!isAdded) return@addOnSuccessListener

                    // ✅ 내 턴인지 체크
                    val currentIndex = (snapshot.get("currentPickIndex") as? Long)?.toInt() ?: 0
                    val viewId = BanpickFriendChampionActivity.pickOrder.getOrNull(currentIndex)
                    val resName = viewId?.let { resId ->
                        requireContext().resources.getResourceEntryName(resId)
                    } ?: ""

                    val isMyTurn = (resName.startsWith("blue") && myTeam == "blue") ||
                            (resName.startsWith("red") && myTeam == "red")

                    if (!isMyTurn) {
                        Toast.makeText(requireContext(), "상대팀 차례입니다", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val bluePicks = (snapshot.get("blueTeam") as? Map<*, *>)?.get("picks") as? Map<*, *> ?: emptyMap<Any?, Any?>()
                    val redPicks  = (snapshot.get("redTeam") as? Map<*, *>)?.get("picks") as? Map<*, *> ?: emptyMap<Any?, Any?>()
                    val totalPicks = (bluePicks.values + redPicks.values).filterIsInstance<String>()

                    if (totalPicks.contains(champ.id)) {
                        Toast.makeText(requireContext(), "이미 선택된 챔피언입니다", Toast.LENGTH_SHORT).show()
                    } else {
                        docRef.update("${myTeam}Team.picks.$currentTurn", champ.id)
                            .addOnSuccessListener {
                                if (!isAdded) return@addOnSuccessListener

                                selectedChampions[currentPickIndex] = champ.id
                                championAdapter.notifyDataSetChanged()

                                Toast.makeText(requireContext(), "${champ.name} 선택 완료", Toast.LENGTH_SHORT).show()
                                onChampionSelected?.invoke(champ)
                                dismiss()
                            }
                            .addOnFailureListener {
                                Log.e("BanPickChoice", "선택 실패: ${it.message}", it)
                                Toast.makeText(requireContext(), "선택 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }.addOnFailureListener {
                    Log.e("BanPickChoice", "방 정보 조회 실패: ${it.message}", it)
                    Toast.makeText(requireContext(), "방 정보 조회 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(requireContext(), "챔피언을 선택해주세요", Toast.LENGTH_SHORT).show()
        }

        recyclerView = view.findViewById(R.id.rv_champion_list)
        recyclerView.layoutManager = GridLayoutManager(context, 5)

        championAdapter = ChampionAdapterFriend(
            selectedChampions = BanpickChampionChoiceFriend.selectedChampions
        ) { champion ->
            selectedChampion = champion
            Toast.makeText(requireContext(), "${champion.name} 선택됨", Toast.LENGTH_SHORT).show()
        }


        recyclerView.adapter = championAdapter

        searchEditText = view.findViewById(R.id.et_champion_search)
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterChampions(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        view.findViewById<Button>(R.id.btn_top).setOnClickListener { toggleRole("Top") }
        view.findViewById<Button>(R.id.btn_jungle).setOnClickListener { toggleRole("Jungle") }
        view.findViewById<Button>(R.id.btn_mid).setOnClickListener { toggleRole("Mid") }
        view.findViewById<Button>(R.id.btn_adc).setOnClickListener { toggleRole("ADC") }
        view.findViewById<Button>(R.id.btn_support).setOnClickListener { toggleRole("Sup") }

        loadChampionList()
    }

    private fun loadChampionList() {
        Firebase.firestore.collection("champion_choice")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener

                val list = mutableListOf<ChampionData>()
                for (doc in snapshot) {
                    val champ = ChampionData(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        iconUrl = doc.getString("iconUrl") ?: "",
                        splashUrl = doc.getString("splashUrl") ?: "",
                        loadingUrl = doc.getString("loadingUrl") ?: "",
                        title = doc.getString("title") ?: "",
                        tags = doc.get("tags") as? List<String> ?: emptyList()
                    )
                    list.add(champ)
                }

                allChampions = list
                championAdapter.submitList(allChampions)

                updateSelectedChampionsFromFirestore()
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                Log.e("BanPickChoice", "챔피언 로딩 실패: ${it.message}", it)
            }
    }

    private fun updateSelectedChampionsFromFirestore() {
        val myTeam = arguments?.getString("myTeam") ?: "blue"
        val enemyTeam = if (myTeam == "blue") "red" else "blue"

        Firebase.firestore.collection("banpick_rooms").document(roomId ?: return)
            .get()
            .addOnSuccessListener { snap ->
                val myPicks = (snap.get("${myTeam}Team.picks") as? Map<*, *>)?.values?.filterIsInstance<String>() ?: emptyList()
                val enemyPicks = (snap.get("${enemyTeam}Team.picks") as? Map<*, *>)?.values?.filterIsInstance<String>() ?: emptyList()

                val totalPicks = myPicks + enemyPicks
                for ((i, champId) in totalPicks.withIndex()) {
                    if (i < selectedChampions.size) {
                        selectedChampions[i] = champId
                    }
                }

                championAdapter.notifyDataSetChanged()
            }
    }

    private fun toggleRole(roleName: String) {
        currentRole = if (currentRole == roleName) null else roleName
        filterChampions()
    }

    private fun filterChampions(search: String = searchEditText.text.toString()) {
        val normalizedQuery = search.replace(" ", "").lowercase()

        val filtered = allChampions.filter { champ ->
            val normalizedChampName = champ.name.replace(" ", "").lowercase()
            val matchesName = normalizedChampName.contains(normalizedQuery)

            val matchesRole = currentRole?.let { role ->
                champ.tags.any { it.equals(role, ignoreCase = true) }
            } ?: true

            matchesName && matchesRole
        }

        championAdapter.submitList(filtered)
    }
}
