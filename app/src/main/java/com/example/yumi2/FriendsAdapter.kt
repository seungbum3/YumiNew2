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
        val friendId = friend["id"] ?: ""

        // 1️⃣ Firestore에서 항상 user_profiles/{friendId} 읽어서 정보 최신화
        val db = FirebaseFirestore.getInstance()
        db.collection("user_profiles").document(friendId)
            .get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("nickname") ?: "알 수 없음"
                val imageUrl = doc.getString("profileImageUrl") ?: ""

                holder.friendName.text = name

                // 이미지 처리 (gs:// or http)
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
                            holder.friendProfileImage.setImageResource(R.drawable.default_profile)
                        }
                } else if (imageUrl.isNotEmpty()) {
                    Glide.with(holder.itemView.context)
                        .load(imageUrl)
                        .circleCrop()
                        .into(holder.friendProfileImage)
                } else {
                    // 이미지 없으면 기본 이미지
                    holder.friendProfileImage.setImageResource(R.drawable.default_profile)
                }

                // 클릭 시 채팅 이동 (닉네임 최신값 전달)
                holder.itemView.setOnClickListener {
                    val context = holder.itemView.context
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
                    if (friendId.isEmpty()) return@setOnClickListener

                    getOrCreateChatRoom(currentUserId, friendId) { chatId ->
                        val intent = Intent(context, ChatActivity::class.java).apply {
                            putExtra("chatId", chatId)
                            putExtra("friendId", friendId)
                            putExtra("friendNickname", name) // 최신 닉네임 전달!
                        }
                        context.startActivity(intent)
                    }
                }
            }
            .addOnFailureListener {
                // Firestore 에러 시
                holder.friendName.text = "알 수 없음"
                holder.friendProfileImage.setImageResource(R.drawable.default_profile)
            }

        // 메뉴 등 나머지 코드는 기존과 동일하게...
        holder.optionsButton?.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.friend_item_menu, popup.menu)
            popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                when (menuItem.itemId) {
                    R.id.menu_chat -> {
                        val context = view.context
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnMenuItemClickListener true
                        if (friendId.isEmpty()) return@setOnMenuItemClickListener true
                        getOrCreateChatRoom(currentUserId, friendId) { chatId ->
                            val intent = Intent(context, ChatActivity::class.java).apply {
                                putExtra("chatId", chatId)
                                putExtra("friendId", friendId)
                                // 별도 닉네임 전달 없이 ChatActivity에서 또 user_profiles fetch 추천
                            }
                            context.startActivity(intent)
                        }
                        true
                    }
                    R.id.menu_delete -> {
                        AlertDialog.Builder(holder.itemView.context)
                            .setTitle("친구 삭제")
                            .setMessage("${holder.friendName.text}님을 친구 목록에서 삭제하시겠습니까?")
                            .setPositiveButton("삭제") { _, _ ->
                                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton
                                deleteFriendAndChats(currentUserId, friendId, holder.itemView.context) {
                                    // UI 업데이트
                                    val newList = filteredList.filter { it["id"] != friendId }
                                    updateData(newList)
                                }
                            }
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
            filteredList = (results.values as List<Map<String, String>>).toMutableList()
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
                        Log.d(
                            "ChatActivity",
                            "✅ 새 채팅방 생성: ${newChatRef.id}, users: [$userA, $userB]"
                        )
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
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        // 현재 사용자의 차단 컬렉션에 차단 대상 추가
        val blockRef = db.collection("users").document(uid)
            .collection("blocked").document(friendId)
        batch.set(blockRef, mapOf("id" to friendId))

        // 양쪽의 친구 컬렉션에서 해당 친구 삭제
        val myFriendRef = db.collection("users").document(uid)
            .collection("friends").document(friendId)
        val theirFriendRef = db.collection("users").document(friendId)
            .collection("friends").document(uid)
        batch.delete(myFriendRef)
        batch.delete(theirFriendRef)

        batch.commit().addOnSuccessListener {
            Log.d("FriendsAdapter", "✅ 차단 성공: $friendId")
            // 차단한 친구를 리스트에서 제거하여 UI 업데이트
            fullList = fullList.filter { it["id"] != friendId }
            filteredList = filteredList.filter { it["id"] != friendId }.toMutableList()
            notifyDataSetChanged()
        }.addOnFailureListener { e ->
            Log.e("FriendsAdapter", "❌ 차단 실패: $friendId", e)
        }
    }
    private fun deleteFriendAndChats(currentUserId: String, friendId: String, context: android.content.Context, onComplete: (() -> Unit)? = null) {
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        // 1. 친구관계 삭제 (양쪽)
        val myFriendRef = db.collection("users").document(currentUserId).collection("friends").document(friendId)
        val theirFriendRef = db.collection("users").document(friendId).collection("friends").document(currentUserId)
        batch.delete(myFriendRef)
        batch.delete(theirFriendRef)

        // 2. 채팅방 삭제 (users 배열에 두 명만 포함된 채팅방)
        db.collection("chats")
            .whereEqualTo("users", listOf(currentUserId, friendId))
            .get()
            .addOnSuccessListener { docs1 ->
                db.collection("chats")
                    .whereEqualTo("users", listOf(friendId, currentUserId))
                    .get()
                    .addOnSuccessListener { docs2 ->
                        val allDocs = docs1.documents + docs2.documents
                        allDocs.forEach { doc ->
                            doc.reference.delete()
                        }
                        batch.commit().addOnSuccessListener {
                            android.widget.Toast.makeText(context, "친구 및 채팅 기록 삭제 완료", android.widget.Toast.LENGTH_SHORT).show()
                            onComplete?.invoke()
                        }
                    }
            }
    }
}
