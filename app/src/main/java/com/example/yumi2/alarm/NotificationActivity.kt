package com.example.yumi2.alarm

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.R
import com.example.yumi2.model.NotificationItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NotificationActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var rv: RecyclerView
    private val list = mutableListOf<NotificationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        rv = findViewById(R.id.notificationRecyclerView)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = NotificationAdapter(list)

        fetchNotifications()
    }

    private fun fetchNotifications() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        firestore.collection("users")
            .document(uid)
            .collection("notifications")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snaps ->
                list.clear()
                for (doc in snaps.documents) {
                    val item = doc.toObject(NotificationItem::class.java)
                    if (item != null) list.add(item)
                }
                rv.adapter?.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Log.e("NotificationActivity", "알림 불러오기 실패: ${e.message}")
            }
    }
}