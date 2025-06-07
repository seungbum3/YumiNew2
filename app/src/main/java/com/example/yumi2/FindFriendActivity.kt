package com.example.yumi2

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class FindFriendActivity : AppCompatActivity(), FindFriendAdapter.FriendRequestListener {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: FindFriendAdapter
    private val users = mutableListOf<Map<String, String>>()
    private val sentRequests = mutableSetOf<String>()
    private val friendListIDs = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_friend)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        adapter = FindFriendAdapter(users, sentRequests, this)
        val rv = findViewById<RecyclerView>(R.id.recyclerViewSearchResults)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val searchView = findViewById<MaterialAutoCompleteTextView>(R.id.etSearchUser)
        val btnSearch = findViewById<ImageButton>(R.id.btnSearch)

        searchView.inputType = android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        searchView.setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT)
        searchView.threshold = 0
        searchView.setDropDownHeight(400)

        searchView.post {
            if (searchView.adapter != null && searchView.adapter.count > 0)
                searchView.showDropDown()
        }

        searchView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && searchView.adapter != null && searchView.adapter.count > 0) {
                searchView.post { searchView.showDropDown() }
            }
            false
        }

        searchView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && searchView.adapter != null && searchView.adapter.count > 0) {
                searchView.post { searchView.showDropDown() }
            }
        }

        btnSearch.setOnClickListener {
            val query = searchView.text.toString().trim()
            if (query.isNotEmpty()) {
                searchUsers(query)
            }
        }
        searchView.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchUsers(query)
                }
                true
            } else false
        }

        loadSentRequests()
        loadFriendListIDs()
    }

    private fun loadFriendListIDs() {
        val uid = FirebaseAuth.getInstance().uid!!
        db.collection("users").document(uid).collection("friends")
            .get().addOnSuccessListener { docs ->
                friendListIDs.clear()
                docs.forEach { doc ->
                    friendListIDs.add(doc.id)
                }
            }
    }

    private fun searchUsers(query: String) {
        val uid = FirebaseAuth.getInstance().uid!!
        db.collection("user_profiles")
            .whereGreaterThanOrEqualTo("nickname", query)
            .whereLessThanOrEqualTo("nickname", query + "\uf8ff")
            .get().addOnSuccessListener { docs ->
                users.clear()
                docs.forEach { doc ->
                    if (doc.id != uid && !friendListIDs.contains(doc.id)) {
                        val profileUrl = doc.getString("profileImageUrl")?.takeIf { it.isNotBlank() }
                            ?: "default"
                        users.add(
                            mapOf(
                                "id" to doc.id,
                                "nickname" to doc.getString("nickname").orEmpty(),
                                "profileImageUrl" to profileUrl
                            )
                        )
                    }
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun loadSentRequests() {
        val uid = FirebaseAuth.getInstance().uid!!
        db.collection("users").document(uid).collection("sent_requests")
            .get().addOnSuccessListener { docs ->
                sentRequests.clear()
                docs.forEach { doc -> sentRequests.add(doc.id) }
                adapter.notifyDataSetChanged()
            }
    }

    override fun onSendRequest(userId: String) {
        val uid = FirebaseAuth.getInstance().uid!!
        val senderUid = uid
        val receiverUid = userId

        val mySentRef = db.collection("users").document(uid)
            .collection("sent_requests").document(userId)
        val theirReqRef = db.collection("users").document(userId)
            .collection("friend_requests").document(uid)
        val myReqRef = db.collection("users").document(uid)
            .collection("friend_requests").document(userId)
        Log.d("FirestoreDebug", "보내는 데이터: senderUid=$senderUid, receiverUid=$receiverUid")
        db.collection("users").document(uid)
            .collection("friend_requests").document(userId)
            .get().addOnSuccessListener { document ->
                if (document.exists()) {
                    db.runBatch { batch ->
                        batch.set(
                            db.collection("users").document(uid)
                                .collection("friends").document(userId),
                            mapOf("id" to userId)
                        )
                        batch.set(
                            db.collection("users").document(userId)
                                .collection("friends").document(uid),
                            mapOf("id" to uid)
                        )
                        batch.delete(myReqRef)
                        batch.delete(
                            db.collection("users").document(userId)
                                .collection("sent_requests").document(uid)
                        )
                    }.addOnSuccessListener {
                        // 친구 수락 후 처리 가능
                    }.addOnFailureListener {
                        // 에러 처리
                    }
                } else {
                    if (sentRequests.contains(userId)) {
                        db.runBatch { batch ->
                            batch.delete(mySentRef)
                            batch.delete(theirReqRef)
                        }.addOnSuccessListener {
                            sentRequests.remove(userId)
                            adapter.notifyDataSetChanged()
                        }
                    } else {
                        db.runBatch { batch ->
                            batch.set(mySentRef, mapOf(
                                "to" to receiverUid,
                                "status" to "pending",
                                "senderUid" to senderUid,
                                "receiverUid" to receiverUid
                            ))
                            batch.set(theirReqRef, mapOf(
                                "from" to senderUid,
                                "status" to "pending",
                                "senderUid" to senderUid,
                                "receiverUid" to receiverUid
                            ))
                        }.addOnSuccessListener {
                            sentRequests.add(userId)
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            }
    }
}
