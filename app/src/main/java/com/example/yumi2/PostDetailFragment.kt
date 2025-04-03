package com.example.yumi2

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import coil.load
import com.example.yumi2.CommentAdapter
import java.text.SimpleDateFormat
import java.util.*

data class Comment(
    val text: String,
    val timestamp: Long,
    val uid: String? = null,
    val nickname: String? = null
)

class PostDetailFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private var postId: String = ""

    // UI 요소들
    private lateinit var detailPostTitle: TextView
    private lateinit var detailPostContent: TextView
    private lateinit var detailPostCategory: TextView
    private lateinit var detailPostTimestamp: TextView
    private lateinit var detailPostViewCount: TextView
    private lateinit var detailPostImage: ImageView
    private lateinit var detailPostNickname: TextView

    private lateinit var commentEditText: EditText
    private lateinit var commentSendButton: Button
    private lateinit var commentRecyclerView: RecyclerView
    private lateinit var commentAdapter: CommentAdapter
    private var comments = mutableListOf<Comment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firestore = FirebaseFirestore.getInstance()
        postId = arguments?.getString("postId") ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.activity_post_detail, container, false)
        detailPostTitle = view.findViewById(R.id.detail_post_title)
        detailPostContent = view.findViewById(R.id.detail_post_content)
        detailPostCategory = view.findViewById(R.id.detail_post_category)
        detailPostTimestamp = view.findViewById(R.id.detail_post_timestamp)
        detailPostViewCount = view.findViewById(R.id.detail_post_view_count)
        detailPostImage = view.findViewById(R.id.detail_post_image)
        detailPostNickname = view.findViewById(R.id.detail_post_nickname)

        commentEditText = view.findViewById(R.id.commentEditText)
        commentSendButton = view.findViewById(R.id.commentSendButton)
        commentRecyclerView = view.findViewById(R.id.commentRecyclerView)
        commentRecyclerView.layoutManager = LinearLayoutManager(context)
        commentAdapter = CommentAdapter(comments)
        commentRecyclerView.adapter = commentAdapter

        commentSendButton.setOnClickListener {
            val commentText = commentEditText.text.toString().trim()
            if (commentText.isNotEmpty()) {
                postComment(commentText)
            } else {
                Toast.makeText(context, "댓글을 입력하세요.", Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 게시글 로드 및 조회수 증가
        if (postId.isNotEmpty()) {
            val postRef = firestore.collection("posts").document(postId)
            postRef.update("views", FieldValue.increment(1))
                .addOnSuccessListener {
                    postRef.get().addOnSuccessListener { document ->
                        val views = document.getLong("views") ?: 0
                        view.findViewById<TextView>(R.id.detail_post_view_count).text = "조회수: $views"
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("FirestoreError", "조회수 증가 실패: ${exception.message}")
                }
            loadPostDetails(postId)
            loadComments(postId)
        } else {
            Toast.makeText(context, "Invalid post ID", Toast.LENGTH_SHORT).show()
        }

        // 뒤로가기 버튼 처리
        val backButton: ImageView = view.findViewById(R.id.backButton)
        backButton.setOnClickListener {
            requireActivity().onBackPressed()
        }

        // [댓글] 텍스트뷰와 댓글 영역 토글 처리
        val toggleCommentText: TextView = view.findViewById(R.id.toggleCommentText)
        val commentInputLayout: View = view.findViewById(R.id.commentInputContainer)
        val commentRecyclerView: View = view.findViewById(R.id.commentRecyclerView)

        // 초기 상태: 댓글 입력 영역과 댓글 목록 숨김
        commentInputLayout.visibility = View.GONE
        commentRecyclerView.visibility = View.GONE

        var isCommentVisible = false
        toggleCommentText.setOnClickListener {
            isCommentVisible = !isCommentVisible
            if (isCommentVisible) {
                commentInputLayout.visibility = View.VISIBLE
                commentRecyclerView.visibility = View.VISIBLE
            } else {
                commentInputLayout.visibility = View.GONE
                commentRecyclerView.visibility = View.GONE
            }
        }
    }

    private fun loadPostDetails(postId: String) {
        val postRef = firestore.collection("posts").document(postId)
        postRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val title = document.getString("title") ?: "제목 없음"
                    val content = document.getString("content") ?: "내용 없음"
                    val category = document.getString("category") ?: "카테고리 없음"
                    val timestamp = document.getLong("timestamp") ?: 0L
                    val imageUrl = document.getString("imageUrl") ?: ""
                    val nickname = document.getString("nickname") ?: "닉네임 없음"

                    detailPostTitle.text = title
                    detailPostContent.text = content
                    detailPostCategory.text = category
                    detailPostNickname.text = nickname

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    detailPostTimestamp.text = dateFormat.format(Date(timestamp))

                    if (imageUrl.isNotEmpty()) {
                        detailPostImage.visibility = View.VISIBLE
                        detailPostImage.load(imageUrl) { crossfade(true) }
                    } else {
                        detailPostImage.visibility = View.GONE
                    }
                } else {
                    Toast.makeText(context, "게시글을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "게시글 로드 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun postComment(commentText: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = currentUser.uid
        firestore.collection("user_profiles").document(uid)
            .get()
            .addOnSuccessListener { document ->
                val nickname = document.getString("nickname") ?: "닉네임 없음"
                val commentData = hashMapOf(
                    "text" to commentText,
                    "timestamp" to System.currentTimeMillis(),
                    "uid" to uid,
                    "nickname" to nickname
                )
                firestore.collection("posts")
                    .document(postId)
                    .collection("comments")
                    .add(commentData)
                    .addOnSuccessListener {
                        Toast.makeText(context, "댓글 저장 성공!", Toast.LENGTH_SHORT).show()
                        commentEditText.text.clear()
                        loadComments(postId)
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(context, "댓글 저장 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "닉네임 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // loadComments 함수 수정: 댓글을 불러온 후 toggleCommentText에 댓글 개수를 업데이트합니다.
    private fun loadComments(postId: String) {
        firestore.collection("posts")
            .document(postId)
            .collection("comments")
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { querySnapshot ->
                comments.clear()
                for (document in querySnapshot.documents) {
                    val commentText = document.getString("text") ?: ""
                    val timestamp = document.getLong("timestamp") ?: 0L
                    val uid = document.getString("uid") ?: ""
                    val nickname = document.getString("nickname") ?: "닉네임 없음"
                    comments.add(Comment(commentText, timestamp, uid, nickname))
                }
                commentAdapter.notifyDataSetChanged()
                // 댓글 수에 따라 [댓글] 텍스트뷰 업데이트
                val toggleCommentText: TextView? = view?.findViewById(R.id.toggleCommentText)
                toggleCommentText?.text = "댓글[${comments.size}]"
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "댓글 불러오기 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
