package com.example.opggyumi.comment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.opggyumi.R
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FreeBoardAdapter(private val context: Context, private val posts: List<Post>) :
    RecyclerView.Adapter<FreeBoardAdapter.PostViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_post, parent, false)
        val params = view.layoutParams as RecyclerView.LayoutParams
        val height = (85 * context.resources.displayMetrics.density).toInt()
        params.height = height
        view.layoutParams = params
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]

        holder.titleTextView.text = post.title
        holder.contentTextView.text = post.content
        holder.categoryTextView.text = post.category
        holder.viewCountTextView.text = "조회수: ${post.views}"

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        holder.timestampTextView.text = dateFormat.format(Date(post.timestamp))

        if (!post.imageUrl.isNullOrEmpty()) {
            holder.postImageView.visibility = View.VISIBLE
            holder.postImageView.load(post.imageUrl)
        } else {
            holder.postImageView.visibility = View.GONE
        }

        holder.nicknameTextView.text = post.nickname ?: "알 수 없음"
        // 댓글 수를 표시할 TextView 가져오기
        val commentCountTextView = holder.itemView.findViewById<TextView>(R.id.text_comment_count)
        // Firestore의 댓글 컬렉션을 실시간으로 구독하여 댓글 수 업데이트
        FirebaseFirestore.getInstance().collection("posts")
            .document(post.postId)
            .collection("comments")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    commentCountTextView.text = "댓글[0]"
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val commentCount = snapshot.size()
                    commentCountTextView.text = "댓글[$commentCount]"
                }
            }
        // 기존 게시글 클릭 시 상세 페이지 이동 코드
        holder.itemView.setOnClickListener {
            val fragment = PostDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("postId", post.postId)
                }
            }
            (context as AppCompatActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }
    }


    override fun getItemCount(): Int = posts.size

    inner class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.text_title)
        val contentTextView: TextView = view.findViewById(R.id.text_content)
        val categoryTextView: TextView = view.findViewById(R.id.text_category)
        val timestampTextView: TextView = view.findViewById(R.id.text_timestamp)
        val viewCountTextView: TextView = view.findViewById(R.id.text_view_count)
        val postImageView: ImageView = view.findViewById(R.id.image_post)
        val nicknameTextView: TextView = view.findViewById(R.id.text_nickname)
    }
}
