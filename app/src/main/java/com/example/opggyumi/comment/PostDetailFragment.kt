package com.example.opggyumi.comment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import coil.load
import com.example.opggyumi.R
import java.text.SimpleDateFormat
import java.util.*

data class Reply(
    val text: String = "",
    val timestamp: Long = 0L,
    val uid: String? = null,
    val nickname: String? = null,
    val replyId: String = ""
)

data class Comment(
    val commentId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val uid: String? = null,
    val nickname: String? = null,
    val replies: List<Reply> = emptyList()
)

class PostDetailFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private var postId: String = ""
    private var postAuthorUid: String = ""

    // 게시글 UI 요소
    private lateinit var detailPostTitle: TextView
    private lateinit var detailPostContent: TextView
    private lateinit var detailPostCategory: TextView
    private lateinit var detailPostTimestamp: TextView
    private lateinit var detailPostViewCount: TextView
    private lateinit var detailPostImage: ImageView
    private lateinit var detailPostNickname: TextView
    private lateinit var hashtagTextView: TextView

    // 댓글 관련 UI 요소
    private lateinit var commentEditText: EditText
    private lateinit var commentSendButton: Button
    private lateinit var commentRecyclerView: RecyclerView
    private lateinit var commentAdapter: CommentAdapter
    private var comments = mutableListOf<Comment>()

    // 삭제 버튼
    private lateinit var deleteTextView: TextView

    // 답글 대상 댓글 (사용자가 "답글달기" 버튼을 누르면 설정됨)
    private var replyTarget: Comment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firestore = FirebaseFirestore.getInstance()
        postId = arguments?.getString("postId") ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.activity_post_detail, container, false)

        // 게시글 UI 초기화
        detailPostTitle = view.findViewById(R.id.detail_post_title)
        detailPostContent = view.findViewById(R.id.detail_post_content)
        detailPostCategory = view.findViewById(R.id.detail_post_category)
        detailPostTimestamp = view.findViewById(R.id.detail_post_timestamp)
        detailPostViewCount = view.findViewById(R.id.detail_post_view_count)
        detailPostImage = view.findViewById(R.id.detail_post_image)
        detailPostNickname = view.findViewById(R.id.detail_post_nickname)
        hashtagTextView = view.findViewById(R.id.hashtagTextView)

        // 댓글 입력 UI 초기화
        commentEditText = view.findViewById(R.id.commentEditText)
        commentSendButton = view.findViewById(R.id.commentSendButton)
        commentRecyclerView = view.findViewById(R.id.commentRecyclerView)
        commentRecyclerView.layoutManager = LinearLayoutManager(context)

        // 키보드의 Send/Done 버튼 처리
        commentEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                commentSendButton.performClick()
                true
            } else {
                false
            }
        }

        // CommentAdapter 생성 (답글달기 클릭 시 replyTarget 설정)
        commentAdapter = CommentAdapter(comments) { comment ->
            replyTarget = comment
            commentEditText.hint = "답글 입력 (@${comment.nickname}에게)"
            commentEditText.requestFocus()
        }
        commentRecyclerView.adapter = commentAdapter

        // 삭제 버튼
        deleteTextView = view.findViewById(R.id.deleteTextView)
        deleteTextView.setOnClickListener { showDeleteConfirmation() }

        // 댓글 전송 버튼: replyTarget가 있으면 답글, 없으면 일반 댓글 전송
        commentSendButton.setOnClickListener {
            val inputText = commentEditText.text.toString().trim()
            if (inputText.isEmpty()) {
                Toast.makeText(context, "댓글을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (replyTarget != null) {
                postReply(replyTarget!!, inputText)
                replyTarget = null
                commentEditText.hint = "댓글을 입력하세요"
            } else {
                postComment(inputText)
            }
            commentEditText.text.clear()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (postId.isNotEmpty()) {
            // 게시글 조회 및 조회수 증가
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
            loadPostDetails()
            loadComments()
        } else {
            Toast.makeText(context, "Invalid post ID", Toast.LENGTH_SHORT).show()
        }

        val backButton: ImageView = view.findViewById(R.id.backButton)
        backButton.setOnClickListener { requireActivity().onBackPressed() }

        // 댓글 영역 토글 (필요 시)
        val toggleCommentText: TextView = view.findViewById(R.id.toggleCommentText)
        val commentInputLayout: View = view.findViewById(R.id.commentInputContainer)
        val commentRecyclerViewView: View = view.findViewById(R.id.commentRecyclerView)
        commentInputLayout.visibility = View.GONE
        commentRecyclerViewView.visibility = View.GONE
        var isCommentVisible = false
        toggleCommentText.setOnClickListener {
            isCommentVisible = !isCommentVisible
            if (isCommentVisible) {
                commentInputLayout.visibility = View.VISIBLE
                commentRecyclerViewView.visibility = View.VISIBLE
            } else {
                commentInputLayout.visibility = View.GONE
                commentRecyclerViewView.visibility = View.GONE
            }
        }
    }

    private fun loadPostDetails() {
        val postRef = firestore.collection("posts").document(postId)
        postRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    detailPostTitle.text = document.getString("title") ?: "제목 없음"
                    detailPostContent.text = document.getString("content") ?: "내용 없음"
                    detailPostCategory.text = document.getString("category") ?: "카테고리 없음"
                    detailPostNickname.text = document.getString("nickname") ?: "닉네임 없음"

                    val timestamp = document.getLong("timestamp") ?: 0L
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    detailPostTimestamp.text = dateFormat.format(Date(timestamp))

                    val imageUrl = document.getString("imageUrl") ?: ""
                    if (imageUrl.isNotEmpty()) {
                        detailPostImage.visibility = View.VISIBLE
                        detailPostImage.load(imageUrl) { crossfade(true) }
                    } else {
                        detailPostImage.visibility = View.GONE
                    }

                    postAuthorUid = document.getString("uid") ?: ""
                    val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
                    deleteTextView.visibility = if (currentUserUid != null && currentUserUid == postAuthorUid) View.VISIBLE else View.GONE

                    val hashtags = document.get("hashtags") as? List<String> ?: emptyList()
                    hashtagTextView.text = hashtags.joinToString(", ")
                } else {
                    Toast.makeText(context, "게시글을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "게시글 로드 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun postComment(commentText: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid
        // user_profiles 컬렉션에서 닉네임을 가져오기
        firestore.collection("user_profiles").document(uid)
            .get()
            .addOnSuccessListener { document ->
                val nickname = document.getString("nickname") ?: "닉네임 없음"
                firestore.collection("posts")
                    .document(postId)
                    .collection("comments")
                    .add(
                        hashMapOf(
                            "postId" to postId,
                            "parentId" to null,
                            "text" to commentText,
                            "timestamp" to System.currentTimeMillis(),
                            "uid" to uid,
                            "nickname" to nickname
                        )
                    )
                    .addOnSuccessListener {
                        Toast.makeText(context, "댓글 작성 완료!", Toast.LENGTH_SHORT).show()
                        loadComments()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "댓글 작성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "닉네임 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun postReply(parentComment: Comment, replyText: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid
        // user_profiles 컬렉션에서 닉네임을 가져오기
        firestore.collection("user_profiles").document(uid)
            .get()
            .addOnSuccessListener { document ->
                val nickname = document.getString("nickname") ?: "닉네임 없음"
                firestore.collection("posts")
                    .document(postId)
                    .collection("comments")
                    .document(parentComment.commentId)
                    .collection("replies")
                    .add(
                        hashMapOf(
                            "text" to replyText,
                            "timestamp" to System.currentTimeMillis(),
                            "uid" to uid,
                            "nickname" to nickname
                        )
                    )
                    .addOnSuccessListener {
                        Toast.makeText(context, "답글 작성 완료!", Toast.LENGTH_SHORT).show()
                        loadComments()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "답글 작성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "닉네임 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    // 모든 댓글(및 답글) 불러오기 및 계층 구성
    private fun loadComments() {
        firestore.collection("posts")
            .document(postId)
            .collection("comments")
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val allDocs = querySnapshot.documents
                // 모든 댓글 문서를 Comment 객체로 변환
                val allComments = allDocs.map { doc ->
                    Comment(
                        commentId = doc.id,
                        text = doc.getString("text") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        uid = doc.getString("uid"),
                        nickname = doc.getString("nickname"),
                        replies = emptyList()
                    )
                }
                // 최상위 댓글 (parentId == null)
                val topComments = allComments.filter {
                        doc ->
                    doc.commentId.isNotEmpty() && (doc.commentId != null) // parentId는 문서 내에 저장되어 있지 않으므로, 최상위 댓글은 모두 가져온다고 가정

                }
                var processedCount = 0
                val commentsWithReplies = mutableListOf<Comment>()
                if (allDocs.isEmpty()) {
                    commentAdapter.notifyDataSetChanged()
                }
                for (doc in allDocs) {
                    val parentId = doc.getString("parentId")
                    // 최상위 댓글만 처리 (답글은 나중에 댓글 문서의 하위 컬렉션으로 불러옴)
                    if (parentId == null) {
                        val commentId = doc.id
                        val commentText = doc.getString("text") ?: ""
                        val timestamp = doc.getLong("timestamp") ?: 0L
                        val uid = doc.getString("uid") ?: ""
                        val nickname = doc.getString("nickname") ?: "닉네임 없음"
                        val baseComment = Comment(
                            commentId = commentId,
                            text = commentText,
                            timestamp = timestamp,
                            uid = uid,
                            nickname = nickname,
                            replies = emptyList()
                        )
                        // 답글 불러오기
                        doc.reference.collection("replies")
                            .orderBy("timestamp")
                            .get()
                            .addOnSuccessListener { replySnapshot ->
                                val replyList = replySnapshot.documents.map { replyDoc ->
                                    Reply(
                                        text = replyDoc.getString("text") ?: "",
                                        timestamp = replyDoc.getLong("timestamp") ?: 0L,
                                        uid = replyDoc.getString("uid"),
                                        nickname = replyDoc.getString("nickname"),
                                        replyId = replyDoc.id
                                    )
                                }
                                commentsWithReplies.add(baseComment.copy(replies = replyList))
                                processedCount++
                                if (processedCount == allDocs.filter { it.getString("parentId") == null }.size) {
                                    comments.clear()
                                    comments.addAll(commentsWithReplies.sortedBy { it.timestamp })
                                    commentAdapter.notifyDataSetChanged()
                                }
                            }
                            .addOnFailureListener {
                                commentsWithReplies.add(baseComment)
                                processedCount++
                                if (processedCount == allDocs.filter { it.getString("parentId") == null }.size) {
                                    comments.clear()
                                    comments.addAll(commentsWithReplies.sortedBy { it.timestamp })
                                    commentAdapter.notifyDataSetChanged()
                                }
                            }
                    }
                }
                val toggleCommentText: TextView? = view?.findViewById(R.id.toggleCommentText)
                toggleCommentText?.text = "댓글[${allDocs.size}]"
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "댓글 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("게시글 삭제")
            .setMessage("게시글을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> deletePost() }
            .setNegativeButton("취소") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun deletePost() {
        firestore.collection("posts").document(postId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(context, "게시글이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressed()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "게시글 삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
