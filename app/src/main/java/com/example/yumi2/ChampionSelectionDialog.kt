package com.example.yumi2

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView // 이 import 중요!!
import com.example.yumi2.adapter.ChampionRecyclerAdapter
import com.google.firebase.firestore.FirebaseFirestore

data class ChampionListItem(
    val korId: String,
    val korName: String,
    val engId: String,
    val iconUrl: String
)

class ChampionSelectionDialog : DialogFragment() {

    interface ChampionSelectionListener {
        fun onChampionSelected(championId: String)
    }

    private val db = FirebaseFirestore.getInstance()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChampionRecyclerAdapter
    private val championList = mutableListOf<ChampionListItem>()
    private var selectedIndex: Int = -1

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_champion_selection, null)
        recyclerView = view.findViewById(R.id.rv_champion_list)
        val btnConfirm = view.findViewById<Button>(R.id.btn_confirm)


        // 모달 창 닫기 버튼
        val btnClose = view.findViewById<ImageButton>(R.id.btn_close)

        btnClose.setOnClickListener {
            dismiss()
        }

        recyclerView.layoutManager = GridLayoutManager(context, 5)
        adapter = ChampionRecyclerAdapter(championList) { index ->
            selectedIndex = index
            adapter.setSelectedIndex(index)
        }
        recyclerView.adapter = adapter

        loadChampionList()

        btnConfirm.setOnClickListener {
            if (selectedIndex != -1) {
                (activity as? ChampionSelectionListener)?.onChampionSelected(championList[selectedIndex].korId)
                dismiss()
            }
        }

        builder.setView(view)
        return builder.create()
    }

    private fun loadChampionList() {
        db.collection("champions").get().addOnSuccessListener { champSnap ->
            val korIdToName = mutableMapOf<String, String>()
            for (document in champSnap) {
                val korId = document.id
                val korName = document.getString("name") ?: korId
                korIdToName[korId] = korName
            }
            db.collection("champion_choice").get().addOnSuccessListener { choiceSnap ->
                val engIdToIcon = mutableMapOf<String, String>()
                val engIdToKorName = mutableMapOf<String, String>()
                for (doc in choiceSnap) {
                    val engId = doc.id
                    val iconUrl = doc.getString("iconUrl") ?: ""
                    val korName = doc.getString("name") ?: ""
                    engIdToIcon[engId] = iconUrl
                    engIdToKorName[engId] = korName
                }

                val tempList = mutableListOf<ChampionListItem>()
                korIdToName.forEach { (korId, korName) ->
                    val foundEntry = engIdToKorName.entries.find { it.value.trim() == korId.trim() }
                    val engId = foundEntry?.key ?: ""
                    val iconUrl = if (engId.isNotEmpty()) engIdToIcon[engId] ?: "" else ""
                    tempList.add(ChampionListItem(korId, korName, engId, iconUrl))
                }
                tempList.sortBy { it.korName }
                championList.clear()
                championList.addAll(tempList)
                adapter.notifyDataSetChanged()
            }
        }
    }
}
