package com.example.yumi2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import coil.load
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

        // 이미지 URL이 있으면 Coil로 이미지 로딩
        if (!post.imageUrl.isNullOrEmpty()) {
            holder.postImageView.visibility = View.VISIBLE
            holder.postImageView.load(post.imageUrl)
        } else {
            holder.postImageView.visibility = View.GONE
        }

        // 작성자 닉네임 설정
        holder.nicknameTextView.text = post.nickname ?: "알 수 없음"

        // **여기서 2번 작업 시작**
        // 게시글 클릭 시 PostDetailFragment를 생성하고, postId를 Bundle로 전달 후
        // MainActivity의 fragment_container에 프래그먼트를 replace합니다.
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
        // **여기서 2번 작업 끝**
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
