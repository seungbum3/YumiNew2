package com.example.yumi2

import android.app.AlertDialog
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
import android.view.MenuItem
import android.widget.Filterable
import android.widget.PopupMenu
import com.google.firebase.storage.FirebaseStorage
import android.widget.Filter

class FriendsAdapter(
    var fullList: List<Map<String, String>>,
    private val friendsList: List<Map<String, String>>, //친구 요청 페이지에서 사용
    private val layoutResId: Int  // 레이아웃 리소스 ID 추가
) : RecyclerView.Adapter<FriendsAdapter.ViewHolder>(), Filterable {
    private var filteredList = fullList.toMutableList()
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val friendProfileImage: ImageView = itemView.findViewById(R.id.friendProfileImage)
        val friendName: TextView = itemView.findViewById(R.id.friendName)
        val optionsButton: ImageView? = itemView.findViewById(R.id.optionsButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val friend = filteredList[position]
        val name = friend["nickname"] ?: "알 수 없음"
        val imageUrl = friend["profileImageUrl"] ?: ""
        val friendId = friend["id"] ?: ""

        holder.friendName.text = name

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
                    // 실패 시 기본 이미지 처리
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

            Log.d("FriendsAdapter", "✅ friend 데이터: $friend")
            Log.d("FriendsAdapter", "✅ friendId 값: $friendId")

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
        holder.optionsButton?.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.friend_item_menu, popup.menu)
            popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                when (menuItem.itemId) {
                    R.id.menu_chat -> {
                        val context = view.context
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnMenuItemClickListener true
                        if (friendId.isEmpty()) {
                            Log.e("FriendsAdapter", "❌ 친구 ID가 없음! 채팅을 시작할 수 없습니다.")
                            return@setOnMenuItemClickListener true
                        }
                        getOrCreateChatRoom(currentUserId, friendId) { chatId ->
                            val intent = Intent(context, ChatActivity::class.java).apply {
                                putExtra("chatId", chatId)
                                putExtra("friendId", friendId)
                                putExtra("friendNickname", friend["nickname"])
                            }
                            context.startActivity(intent)
                        }
                        true
                    }
                    R.id.menu_delete -> {
                        AlertDialog.Builder(holder.itemView.context)
                            .setTitle("친구 삭제")
                            .setMessage("${holder.friendName.text}님을 친구 목록에서 삭제하시겠습니까?")
                            .setPositiveButton("삭제") { _, _ -> deleteFriend(friendId) }
                            .setNegativeButton("취소", null)
                            .show()
                        true
                    }
                    R.id.menu_block -> {
                        blockFriend(friendId)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

    }

    fun updateData(newList: List<Map<String, String>>) {
        fullList = newList
        filteredList = newList.toMutableList()
        notifyDataSetChanged()
    }


    override fun getItemCount(): Int = filteredList.size

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?) = FilterResults().apply {
            val query = constraint?.toString()?.lowercase()?.trim()
            values = if (query.isNullOrEmpty()) fullList
            else fullList.filter { it["nickname"]?.lowercase()?.contains(query) == true }
        }
        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            filteredList = (results.values as List<Map<String,String>>).toMutableList()
            notifyDataSetChanged()
        }
    }

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
                // 새 채팅방 생성
                val newChatRef = chatsRef.document()
                val chatData = hashMapOf(
                    "users" to listOf(userA, userB),
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
    private fun deleteFriend(friendId: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        val myFriendRef = db.collection("users").document(currentUserId)
            .collection("friends").document(friendId)

        val theirFriendRef = db.collection("users").document(friendId)
            .collection("friends").document(currentUserId)

        myFriendRef.delete()
        theirFriendRef.delete()
            .addOnSuccessListener {
                Log.d("FriendsAdapter", "✅ 친구 삭제 성공 (양쪽)")
                val newList = filteredList.filter { it["id"] != friendId }
                updateData(newList)
            }
            .addOnFailureListener { e ->
                Log.e("FriendsAdapter", "❌ 친구 삭제 실패", e)
            }
    }

    private fun blockFriend(friendId: String) {
        Log.d("FriendsAdapter", "친구($friendId) 차단하기 선택됨")
    }
}
