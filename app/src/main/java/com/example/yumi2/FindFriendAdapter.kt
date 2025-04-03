package com.example.yumi2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage

class FindFriendAdapter(
    private val users: MutableList<Map<String, String>>,
    private val sentRequests: MutableSet<String>, // 이미 요청한 사용자 ID 리스트
    private val listener: FriendRequestListener
) : RecyclerView.Adapter<FindFriendAdapter.VH>() {

    interface FriendRequestListener {
        fun onSendRequest(userId: String)
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val img: ImageView = itemView.findViewById(R.id.friendProfileImage)
        val name: TextView = itemView.findViewById(R.id.friendName)
        val btnSendRequest: Button = itemView.findViewById(R.id.btnSendRequest)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_find_friend, parent, false))

    override fun getItemCount() = users.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val user = users[position]
        val userId = user["id"] ?: return
        holder.name.text = user["nickname"]

        val imageUrl = user["profileImageUrl"].orEmpty()

        if (imageUrl == "default") {
            // 기본 프로필 이미지를 Firebase Storage에서 불러오기
            val storageRef = FirebaseStorage.getInstance().reference.child("default_profile.jpg")
            storageRef.downloadUrl
                .addOnSuccessListener { uri ->
                    Glide.with(holder.img.context)
                        .load(uri.toString()) // 변환된 HTTP URL 로드
                        .circleCrop()
                        .into(holder.img)
                }
                .addOnFailureListener {
                    // Firebase에서 가져오기 실패 시 기본 이미지 설정
                    holder.img.setImageResource(R.drawable.error_image)
                }
        } else if (imageUrl.startsWith("gs://")) {
            // gs:// 형식이면 Firebase Storage에서 HTTP URL로 변환 후 Glide에 전달
            val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl)
            storageRef.downloadUrl
                .addOnSuccessListener { uri ->
                    Glide.with(holder.img.context)
                        .load(uri.toString())
                        .circleCrop()
                        .into(holder.img)
                }
                .addOnFailureListener {
                    holder.img.setImageResource(R.drawable.error_image)
                }
        } else {
            // HTTP URL이면 그대로 Glide 로드
            Glide.with(holder.img.context)
                .load(imageUrl)
                .circleCrop()
                .placeholder(R.drawable.error_image)
                .into(holder.img)
        }

        // sentRequests의 상태에 따라 버튼 텍스트를 설정
        if (sentRequests.contains(userId)) {
            holder.btnSendRequest.text = "요청됨"
        } else {
            holder.btnSendRequest.text = "요청"
        }
        // 버튼은 항상 활성화 시켜두기
        holder.btnSendRequest.isEnabled = true

        // 버튼 클릭 시에는 단순히 요청 전송/취소 작업을 호출하고,
        // 작업 성공 후 Activity에서 sentRequests를 업데이트하고 adapter.notifyDataSetChanged() 호출할 것.
        holder.btnSendRequest.setOnClickListener {
            listener.onSendRequest(userId)
        }
    }
}
