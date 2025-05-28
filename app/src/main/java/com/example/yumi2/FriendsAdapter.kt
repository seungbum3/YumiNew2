package com.example.yumi2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import com.google.firebase.storage.FirebaseStorage

class FriendsAdapter(private val friendsList: List<Map<String, String>>) :
    RecyclerView.Adapter<FriendsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val friendProfileImage: ImageView = itemView.findViewById(R.id.friendProfileImage)
        val friendName: TextView = itemView.findViewById(R.id.friendName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val friend = friendsList[position]
        val name = friend["nickname"] ?: "알 수 없음"
        val imageUrl = friend["profileImageUrl"] ?: ""
        val friendId = friend["id"] ?: "" // 친구 ID 가져오기

        holder.friendName.text = name

        // gs:// 형식이면 HTTP URL로 변환하여 로드
        if (imageUrl.startsWith("gs://")) {
            FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl)
                .downloadUrl
                .addOnSuccessListener { uri ->
                    Glide.with(holder.itemView.context)
                        .load(uri.toString())
                        .circleCrop()
                        .into(holder.friendProfileImage)
                }
                .addOnFailureListener {
                }
        } else {
            Glide.with(holder.itemView.context)
                .load(imageUrl)
                .circleCrop()
                .into(holder.friendProfileImage)
        }

        // 친구 목록에서 클릭 시 채팅방 이동
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val friendId = friend["id"]

            // 로그 추가하여 id 값 확인
            Log.d("FriendsAdapter", "✅ friend 데이터: $friend") // 전체 friend 객체 출력
            Log.d("FriendsAdapter", "✅ friendId 값: $friendId") // id 값만 출력

            if (friendId.isNullOrEmpty()) {
                Log.e("FriendsAdapter", "❌ 친구 ID가 없음! 채팅을 시작할 수 없습니다.")
                return@setOnClickListener
            }

            getOrCreateChatRoom(currentUserId, friendId) { chatId ->
                Log.d("FriendsAdapter", "✅ 친구 선택됨 - ID: $friendId, 닉네임: ${friend["nickname"]}")
                val intent = Intent(context, ChatActivity::class.java).apply {
                    putExtra("chatId", chatId)
                    putExtra("friendId", friendId)
                    putExtra("friendNickname", friend["nickname"])
                }
                context.startActivity(intent)
            }
        }

    }

    override fun getItemCount(): Int = friendsList.size

    private fun getOrCreateChatRoom(userA: String, userB: String, callback: (String) -> Unit) {
        if (userB.isEmpty()) {
            Log.e("ChatActivity", "❌ 채팅 상대 UID가 비어 있음!")
            return
        }

        val db = FirebaseFirestore.getInstance()
        val chatsRef = db.collection("chats")

        chatsRef.whereArrayContains("users", userA)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val users = document.get("users") as List<String>
                    if (users.contains(userB)) {
                        Log.d("ChatActivity", "✅ 기존 채팅방 찾음: ${document.id}")
                        callback(document.id)
                        return@addOnSuccessListener
                    }
                }

                // ✅ 새 채팅방 생성 (users 필드 포함)
                val newChatRef = chatsRef.document()
                val chatData = hashMapOf(
                    "users" to listOf(userA, userB), // ✅ 채팅방 참여자 목록 포함
                    "lastMessage" to "",
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )
                newChatRef.set(chatData)
                    .addOnSuccessListener {
                        Log.d("ChatActivity", "✅ 새 채팅방 생성: ${newChatRef.id}, users: [$userA, $userB]")
                        callback(newChatRef.id)
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatActivity", "❌ 채팅방 생성 실패", e)
                    }
            }
    }

}
