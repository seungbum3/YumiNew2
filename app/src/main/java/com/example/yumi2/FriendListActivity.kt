package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FriendListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var friendsAdapter: FriendsAdapter
    private lateinit var emptyText: TextView

    private val friendsList = mutableListOf<Map<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_list)

        recyclerView = findViewById(R.id.recyclerViewFriends)
        emptyText = findViewById(R.id.emptyText)

        recyclerView.layoutManager = LinearLayoutManager(this)
        friendsAdapter = FriendsAdapter(friendsList, friendsList, R.layout.item_friend_list)
        recyclerView.adapter = friendsAdapter

        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener { query ->
            friendsAdapter.filter.filter(query.toString())
        }

        findViewById<LinearLayout>(R.id.llFriendRequests)
            .setOnClickListener { startActivity(Intent(this, FriendRequestActivity::class.java)) }
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.tvFriendFind).setOnClickListener {
            startActivity(Intent(this, FindFriendActivity::class.java))
            findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        }
    }

    override fun onResume() {
        super.onResume()
        loadFriendList()
        loadFriendRequestsCount()

        val etSearch = findViewById<EditText>(R.id.etSearch)
        val query = etSearch.text.toString().trim()
        if(query.isNotEmpty()){
            friendsAdapter.filter.filter(query)
        }
    }

    private fun loadFriendRequestsCount() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("friend_requests")
            .get()
            .addOnSuccessListener { docs ->
                val count = docs.size()
                updateBadge(count)
            }
            .addOnFailureListener { e ->
                // 실패 시 기본값 0 또는 에러 처리
                updateBadge(0)
            }
    }

    private fun updateBadge(count: Int) {
        val tvBadge = findViewById<TextView>(R.id.tvBadge)
        if (count > 0) {
            tvBadge.text = count.toString()
            tvBadge.visibility = View.VISIBLE
        } else {
            tvBadge.visibility = View.GONE
        }
    }


    private fun loadFriendList() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("friends")
            .get()
            .addOnSuccessListener { docs ->
                friendsList.clear()
                val db = FirebaseFirestore.getInstance()
                val totalFriends = docs.size()
                if (totalFriends == 0) {
                    emptyText.visibility = android.view.View.VISIBLE
                    recyclerView.visibility = android.view.View.GONE
                    return@addOnSuccessListener
                }
                // 카운터: 모든 프로필 정보를 불러온 후 notifyDataSetChanged() 호출
                var profilesFetched = 0
                docs.forEach { doc ->
                    // 우선 친구 ID만 저장한 임시 맵 생성
                    val friendMap = HashMap<String, String>()
                    friendMap["id"] = doc.id

                    // user_profiles 컬렉션에서 추가 정보 조회
                    db.collection("user_profiles")
                        .document(doc.id)
                        .get()
                        .addOnSuccessListener { profileDoc ->
                            // 닉네임과 프로필 URL 업데이트, 없으면 기본값 설정
                            val nickname = profileDoc.getString("nickname") ?: "알 수 없음"
                            val profileImageUrl = profileDoc.getString("profileImageUrl") ?: "default"
                            friendMap["nickname"] = nickname
                            friendMap["profileImageUrl"] = profileImageUrl

                            friendsList.add(friendMap)
                            profilesFetched++
                            // 모든 친구의 프로필 정보를 불러왔으면 Adapter 업데이트
                            if (profilesFetched == totalFriends) {
                                emptyText.visibility = android.view.View.GONE
                                recyclerView.visibility = android.view.View.VISIBLE
                                friendsAdapter.notifyDataSetChanged()
                                friendsAdapter.filter.filter("")
                            }
                        }
                        .addOnFailureListener {
                            // 만약 profile 정보를 불러오지 못하면 기본값 사용
                            friendMap["nickname"] = "알 수 없음"
                            friendMap["profileImageUrl"] = "default"
                            friendsList.add(friendMap)
                            profilesFetched++
                            if (profilesFetched == totalFriends) {
                                emptyText.visibility = android.view.View.GONE
                                recyclerView.visibility = android.view.View.VISIBLE
                                friendsAdapter.notifyDataSetChanged()
                                friendsAdapter.filter.filter("")
                            }
                        }
                }
            }
    }
}
