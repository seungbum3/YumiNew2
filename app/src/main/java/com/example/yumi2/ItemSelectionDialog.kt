package com.example.yumi2

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.model.Item
import com.google.firebase.firestore.FirebaseFirestore

class ItemSelectionDialog : DialogFragment() {

    interface ItemSelectionListener {
        fun onItemSelected(item: Item)
    }

    private var listener: ItemSelectionListener? = null
    fun setListener(l: ItemSelectionListener) {
        listener = l
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private var itemList = mutableListOf<Item>()
    private lateinit var adapter: ItemGridAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_item_selection)

        val rv = dialog.findViewById<RecyclerView>(R.id.rv_item_list)
        val etSearch = dialog.findViewById<EditText>(R.id.et_item_search)

        // 닫기버튼
        val btnClose = dialog.findViewById<View>(R.id.btn_close)
        btnClose.setOnClickListener { dismiss() }

        adapter = ItemGridAdapter(itemList) { item ->
            listener?.onItemSelected(item)
            dismiss()
        }
        rv.layoutManager = GridLayoutManager(context, 6) // 6개씩 그리드
        rv.adapter = adapter

        // 파이어스토어에서 아이템 전체 불러오기
        FirebaseFirestore.getInstance().collection("items")
            .get()
            .addOnSuccessListener { snapshot ->
                itemList.clear()
                for (doc in snapshot) {
                    val item = doc.toObject(Item::class.java)
                    itemList.add(item)
                }
                adapter.notifyDataSetChanged()
            }

        // 검색 기능
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()?.lowercase() ?: ""
                val filtered = itemList.filter { it.name.lowercase().contains(query) }
                adapter.updateItems(filtered)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        return dialog
    }
}
