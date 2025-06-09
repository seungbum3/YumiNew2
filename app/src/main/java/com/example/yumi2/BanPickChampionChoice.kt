package com.example.yumi2

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.adapter.ChampionAdapter
import com.example.yumi2.model.ChampionData
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class BanPickChampionChoice : DialogFragment() {

    companion object {
        var selectedChampions = MutableList(20) { "" }
        var allChampions = listOf<ChampionData>()

        fun newInstance(onChampionSelected: (ChampionData) -> Unit): BanPickChampionChoice {
            val fragment = BanPickChampionChoice()
            fragment.onChampionSelected = onChampionSelected
            return fragment
        }
        fun resetSelections() {
            if (selectedChampions.size != BanPickChampion.pickOrder.size) {
                selectedChampions = MutableList(BanPickChampion.pickOrder.size) { "" }
            } else {
                for (i in selectedChampions.indices) selectedChampions[i] = ""
            }
        }
    }

    private var onChampionSelected: ((ChampionData) -> Unit)? = null
    private var currentRole: String? = null
    private var selectedChampion: ChampionData? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var championAdapter: ChampionAdapter
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
        return inflater.inflate(R.layout.banpick_champion_choice, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<ImageButton>(R.id.btn_close).setOnClickListener { dismiss() }

        view.findViewById<Button>(R.id.btn_confirm).setOnClickListener {
            selectedChampion?.let { champ ->
                val idx = BanPickChampion.currentPickIndex
                if (selectedChampions.contains(champ.id)) {
                    Toast.makeText(requireContext(), "이미 선택된 챔피언입니다", Toast.LENGTH_SHORT).show()
                } else if (idx < selectedChampions.size) {
                    selectedChampions[idx] = champ.id
                    onChampionSelected?.invoke(champ)
                    dismiss()
                }
            } ?: Toast.makeText(requireContext(), "챔피언을 선택해주세요", Toast.LENGTH_SHORT).show()
        }

        recyclerView = view.findViewById(R.id.rv_champion_list)
        recyclerView.layoutManager = GridLayoutManager(context, 5)
        championAdapter = ChampionAdapter { champion ->
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

        view.findViewById<Button>(R.id.btn_top).setOnClickListener {
            toggleRole("Top", listOf("Top"))
        }
        view.findViewById<Button>(R.id.btn_jungle).setOnClickListener {
            toggleRole("Jungle", listOf("Jungle"))
        }
        view.findViewById<Button>(R.id.btn_mid).setOnClickListener {
            toggleRole("Mid", listOf("Mid"))
        }
        view.findViewById<Button>(R.id.btn_adc).setOnClickListener {
            toggleRole("ADC", listOf("ADC"))
        }
        view.findViewById<Button>(R.id.btn_support).setOnClickListener {
            toggleRole("Sup", listOf("Sup"))
        }

        loadChampionList()
    }

    private fun loadChampionList() {
        Firebase.firestore.collection("champion_choice")
            .get()
            .addOnSuccessListener { snapshot ->
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
            }
            .addOnFailureListener {
                Log.e("BanPickChoice", "챔피언 로딩 실패: ${it.message}", it)
            }
    }

    private fun toggleRole(roleName: String, tags: List<String>) {
        currentRole = if (currentRole == roleName) null else roleName
        filterChampions()
    }

    private fun filterChampions(search: String = searchEditText.text.toString()) {
        val normalizedQuery = search.replace(" ", "").lowercase()

        val filtered = allChampions.filter { champ ->
            val normalizedChampName = champ.name.replace(" ", "").lowercase()
            val matchesName = normalizedChampName.contains(normalizedQuery)

            val matchesRole = currentRole?.let { role ->
                when (role) {
                    "Top" -> champ.tags.any { it == "Top" }
                    "Jungle" -> champ.tags.any { it == "Jungle" }
                    "Mid" -> champ.tags.any { it == "Mid" }
                    "ADC" -> champ.tags.any { it == "ADC" }
                    "Sup" -> champ.tags.any { it == "Sup" }
                    else -> champ.tags.contains(role)
                }
            } ?: true

            matchesName && matchesRole
        }

        championAdapter.submitList(filtered)
    }
}
