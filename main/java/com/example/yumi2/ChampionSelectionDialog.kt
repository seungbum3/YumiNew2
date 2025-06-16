package com.example.yumi2

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.GridView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.firebase.firestore.FirebaseFirestore

class ChampionSelectionDialog : DialogFragment() {

    interface ChampionSelectionListener {
        fun onChampionSelected(championId: String)
    }

    private val db = FirebaseFirestore.getInstance()
    private lateinit var gridView: GridView
    private val championList = mutableListOf<Pair<String, String>>() // Pair<championId, championName>

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = LayoutInflater.from(context)
        val view: View = inflater.inflate(R.layout.dialog_champion_selection, null)
        gridView = view.findViewById(R.id.championGridView)

        loadChampionList()

        builder.setView(view as View)
            .setTitle("챔피언 선택")
        return builder.create()
    }

    private fun loadChampionList() {
        db.collection("champions").get()
            .addOnSuccessListener { querySnapshot ->
                championList.clear()
                for (document in querySnapshot) {
                    val champId = document.id
                    val champName = document.getString("name") ?: champId
                    championList.add(Pair(champId, champName))
                }
                championList.sortBy { it.second }
                val adapter = ChampionGridAdapter(requireContext(), championList)
                gridView.adapter = adapter
                gridView.setOnItemClickListener { _, _, position, _ ->
                    val selectedChampion = championList[position]
                    (activity as? ChampionSelectionListener)?.onChampionSelected(selectedChampion.first)
                    dismiss()
                }
            }
    }
}
