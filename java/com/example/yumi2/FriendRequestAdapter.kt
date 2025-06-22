package com.example.yumi2

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage

class FriendRequestAdapter(
    private val requests: MutableList<Map<String, String>>,
    private val listener: ActionListener
) : RecyclerView.Adapter<FriendRequestAdapter.VH>() {

    interface ActionListener {
        fun onAccept(requesterId: String)
        fun onReject(requesterId: String)
        fun onBlock(requesterId: String)
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val img: ImageView = itemView.findViewById(R.id.friendProfileImage)
        val name: TextView = itemView.findViewById(R.id.friendName)
        val btnAccept: Button = itemView.findViewById(R.id.btnAccept)
        val btnReject: Button = itemView.findViewById(R.id.btnReject)
        val btnBlock: Button = itemView.findViewById(R.id.btnBlock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_friend_request, parent, false))

    override fun getItemCount() = requests.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val req = requests[position]
        val id = req["id"] ?: return
        holder.name.text = req["nickname"]
        val imageUrl = req["profileImageUrl"].orEmpty()

        if (imageUrl == "default" || imageUrl.startsWith("gs://")) {
            Glide.with(holder.img.context)
                .load(R.drawable.default_profile)  // 로컬 리소스 사용
                .circleCrop()                      // 원형으로 변환
                .placeholder(R.drawable.error_image)
                .into(holder.img)
        } else {
            Glide.with(holder.img.context)
                .load(imageUrl)
                .circleCrop()
                .placeholder(R.drawable.error_image)
                .into(holder.img)
        }

        holder.btnAccept.setOnClickListener { listener.onAccept(id) }
        holder.btnReject.setOnClickListener { listener.onReject(id) }
        holder.btnBlock.setOnClickListener { listener.onBlock(id) }
    }

}
