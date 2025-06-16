package com.example.yumi2

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore



class FriendRequestActivity : AppCompatActivity(), FriendRequestAdapter.ActionListener {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: FriendRequestAdapter
    private val requests = mutableListOf<Map<String,String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_request)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        adapter = FriendRequestAdapter(requests, this)
        val rv = findViewById<RecyclerView>(R.id.recyclerViewRequests)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        loadRequests()
    }

    private fun loadRequests() {
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        db.collection("users").document(uid).collection("friend_requests")
            .get()
            .addOnSuccessListener { docs ->
                requests.clear()
                val totalRequests = docs.size()
                if (totalRequests == 0) {
                    // 요청이 하나도 없으면 바로 emptyRequestsText 보이기
                    findViewById<TextView>(R.id.emptyRequestsText).visibility = View.VISIBLE
                    return@addOnSuccessListener
                }
                var processedCount = 0
                docs.forEach { d ->
                    val requesterId = d.id
                    db.collection("user_profiles").document(requesterId)
                        .get()
                        .addOnSuccessListener { userDoc ->
                            val nickname = userDoc.getString("nickname") ?: "알 수 없음"
                            val profileImageUrl = userDoc.getString("profileImageUrl") ?: "default"
                            val map = hashMapOf(
                                "id" to requesterId,
                                "nickname" to nickname,
                                "profileImageUrl" to profileImageUrl
                            )
                            requests.add(map)
                            processedCount++
                            // 모든 요청 처리 완료 후에 UI 갱신
                            if (processedCount == totalRequests) {
                                updateRequestsUI()
                            }
                        }
                        .addOnFailureListener {
                            processedCount++
                            if (processedCount == totalRequests) {
                                updateRequestsUI()
                            }
                        }
                }
            }
            .addOnFailureListener {
                // 요청 가져오기 실패 시 처리
                findViewById<TextView>(R.id.emptyRequestsText).visibility = View.VISIBLE
            }
    }

    private fun updateRequestsUI() {
        if (requests.isEmpty()) {
            findViewById<TextView>(R.id.emptyRequestsText).visibility = View.VISIBLE
        } else {
            findViewById<TextView>(R.id.emptyRequestsText).visibility = View.GONE
        }
        adapter.notifyDataSetChanged()
    }



    override fun onAccept(requesterId: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser!!.uid
        val batch = db.batch()

        // 🔥 친구 목록에 추가 (양쪽)
        batch.set(db.collection("users").document(currentUid)
            .collection("friends").document(requesterId), mapOf("id" to requesterId))

        batch.set(db.collection("users").document(requesterId)
            .collection("friends").document(currentUid), mapOf("id" to currentUid))

        // 🔥 요청 목록에서 삭제
        batch.delete(db.collection("users").document(currentUid)
            .collection("friend_requests").document(requesterId))

        batch.delete(db.collection("users").document(requesterId)
            .collection("sent_requests").document(currentUid))

        batch.commit().addOnSuccessListener { loadRequests()
            sendFriendRequestResultNotification(requesterId, "accept")}
    }

    override fun onReject(requesterId: String) {
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        db.collection("users").document(uid)
            .collection("friend_requests").document(requesterId)
            .delete().addOnSuccessListener { loadRequests()
                sendFriendRequestResultNotification(requesterId, "reject")}
    }



    override fun onBlock(requesterId: String) {
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        val batch = db.batch()

        // 차단 목록 추가
        batch.set(db.collection("users").document(uid)
            .collection("blocked").document(requesterId), mapOf("id" to requesterId))

        // 친구 요청 컬렉션에서 해당 요청 삭제
        batch.delete(db.collection("users").document(uid)
            .collection("friend_requests").document(requesterId))

        batch.commit().addOnSuccessListener { loadRequests() }
    }
    // 친구 요청 결과 알림 보내기 (수락/거절)
    private fun sendFriendRequestResultNotification(toUid: String, result: String) {
        val myUid = FirebaseAuth.getInstance().currentUser!!.uid
        db.collection("user_profiles").document(myUid).get()
            .addOnSuccessListener { doc ->
                val myNickname = doc.getString("nickname") ?: "알 수 없음"
                val message = if (result == "accept") {
                    "$myNickname 님이 친구요청을 수락하였습니다."
                } else {
                    "$myNickname 님이 친구요청을 거절하였습니다."
                }
                db.collection("users").document(toUid)
                    .collection("notifications")
                    .add(
                        mapOf(
                            "type" to "friend_request_result",
                            "senderUid" to myUid,
                            "senderNickname" to myNickname,
                            "message" to message,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
    }


}