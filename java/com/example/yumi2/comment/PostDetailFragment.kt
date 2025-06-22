package com.example.yumi2.comment

import android.content.Intent
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
import coil.load
import com.bumptech.glide.Glide
import com.example.yumi2.ChampcalActivity
import com.example.yumi2.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson

import java.util.*

data class Post(
    val title: String = "",
    val content: String = "",
    val category: String = "",
    val timestamp: Long = 0L,
    val views: Int = 0,
    val postId: String = "",
    val imageUrl: String? = null,
    val uid: String? = null,        // 작성자 UID
    val nickname: String? = null,   // 작성자 닉네임
    val hashtags: List<String> = emptyList(),  // 해시태그 필드 추가
    val itemBuild: List<String>? = null, // ← 추가
    val championId: String? = null       // ← 추가
)

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
    private lateinit var itemImageContainer: LinearLayout


    // 하이라이트할 댓글 ID
    private var highlightCommentId: String? = null
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

    // 햄버거 메뉴 버튼
    private lateinit var menuButton: ImageView

    // 답글 대상
    private var replyTarget: Comment? = null

    // 수정용 게시글 데이터
    private var currentPost: Post? = null

    // Firestore 리스너
    private var postListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firestore = FirebaseFirestore.getInstance()
        postId = arguments?.getString("postId") ?: ""
        // 전달된 댓글 하이라이트 ID
        highlightCommentId = arguments?.getString("highlightCommentId")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_post_detail, container, false)

        itemImageContainer = view.findViewById(R.id.itemImageContainer)

        // 게시글 UI 초기화
        detailPostTitle = view.findViewById(R.id.detail_post_title)
        detailPostContent = view.findViewById(R.id.detail_post_content)
        detailPostCategory = view.findViewById(R.id.detail_post_category)
        detailPostTimestamp = view.findViewById(R.id.detail_post_timestamp)
        detailPostViewCount = view.findViewById(R.id.detail_post_view_count)
        detailPostImage = view.findViewById(R.id.detail_post_image)
        detailPostNickname = view.findViewById(R.id.detail_post_nickname)
        hashtagTextView = view.findViewById(R.id.hashtagTextView)

        // 댓글 UI 초기화
        commentEditText = view.findViewById(R.id.commentEditText)
        commentSendButton = view.findViewById(R.id.commentSendButton)
        commentRecyclerView = view.findViewById(R.id.commentRecyclerView)
        commentRecyclerView.layoutManager = LinearLayoutManager(context)

        commentEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                commentSendButton.performClick()
                true
            } else {
                false
            }
        }

        commentAdapter = CommentAdapter(comments) { comment ->
            replyTarget = comment
            commentEditText.hint = "답글 입력 (@${comment.nickname}에게)"
            commentEditText.requestFocus()
        }
        // 친구 추가 콜백 연결!
        commentAdapter.onAddFriendClick = { targetUid, targetNickname ->
            sendFriendRequestFromDetail(targetUid, targetNickname)
        }
        commentRecyclerView.adapter = commentAdapter

        menuButton = view.findViewById(R.id.menuButton)
        menuButton.setOnClickListener { showMenuOptions() }

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

    private fun sendFriendRequestFromDetail(targetUid: String, targetNickname: String?) {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // 1. 이미 친구인지 확인
        db.collection("users").document(myUid).collection("friends").document(targetUid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    Toast.makeText(context, "이미 친구입니다!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                // 2. 친구 요청 중복 확인 (이미 요청했는지)
                db.collection("users").document(targetUid)
                    .collection("friend_requests").document(myUid)
                    .get()
                    .addOnSuccessListener { reqDoc ->
                        if (reqDoc.exists()) {
                            Toast.makeText(context, "이미 친구 요청을 보냈어요!", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        // 3. 내 닉네임 조회 후 요청/알림 전송
                        db.collection("user_profiles").document(myUid).get()
                            .addOnSuccessListener { profileDoc ->
                                val myNickname = profileDoc.getString("nickname") ?: "알 수 없음"

                                // 친구 요청 저장
                                db.collection("users").document(targetUid)
                                    .collection("friend_requests").document(myUid)
                                    .set(
                                        mapOf(
                                            "senderUid" to myUid,
                                            "receiverUid" to targetUid,
                                            "timestamp" to System.currentTimeMillis()
                                        )
                                    ).addOnSuccessListener {
                                        // 알림 전송
                                        db.collection("users").document(targetUid)
                                            .collection("notifications")
                                            .add(
                                                mapOf(
                                                    "type" to "friend_request",
                                                    "senderUid" to myUid,
                                                    "senderNickname" to myNickname,
                                                    "timestamp" to System.currentTimeMillis()
                                                )
                                            )
                                        Toast.makeText(context, "친구 요청을 보냈습니다!", Toast.LENGTH_SHORT).show()
                                    }
                            }
                    }
            }
    }


    private fun loadPostDetail() {
        if (postId.isEmpty()) return

        firestore.collection("posts").document(postId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    detailPostTitle.text = document.getString("title") ?: ""
                    detailPostContent.text = document.getString("content") ?: ""
                    detailPostCategory.text = document.getString("category") ?: ""
                    detailPostNickname.text = document.getString("nickname") ?: ""

                    // 타임스탬프 처리 (필요시)
                    val timestamp = document.getLong("timestamp") ?: 0L
                    detailPostTimestamp.text = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

                    val imageUrl = document.getString("imageUrl")
                    if (!imageUrl.isNullOrEmpty()) {
                        detailPostImage.visibility = View.VISIBLE
                        detailPostImage.load(imageUrl)
                    } else {
                        detailPostImage.visibility = View.GONE
                    }


                    // 해시태그 처리 (필요시)
                    val hashtags = document.get("hashtags") as? List<String>
                    hashtagTextView.text = hashtags?.joinToString(" ") ?: ""

                    // ✅ 아이템 빌드 텍스트뷰 설정 및 저장 버튼 처리
                    val itemBuild = document.get("itemBuild") as? List<String> ?: emptyList()
                    val textItemBuild = view?.findViewById<TextView>(R.id.textItemBuild)
                    val btnSaveItemBuild = view?.findViewById<Button>(R.id.btnSaveItemBuild)

                    if (itemBuild.isEmpty()) {
                        textItemBuild?.visibility = View.GONE
                        btnSaveItemBuild?.visibility = View.GONE
                    } else {
                        textItemBuild?.visibility = View.VISIBLE
                        val postUid = document.getString("uid")  // 작성자 UID
                        val currentUid = FirebaseAuth.getInstance().currentUser?.uid

                        // 본인이 작성한 글이면 저장 버튼 숨기기
                        if (postUid == currentUid) {
                            btnSaveItemBuild?.visibility = View.GONE
                        } else {
                            btnSaveItemBuild?.visibility = View.VISIBLE
                        }
                        textItemBuild?.text = "아이템: ${itemBuild.joinToString(", ")}"

                        // 💡 [2] 아이템 이미지 불러오기 추가! (버튼 리스너 밖)
                        val db = FirebaseFirestore.getInstance()
                        db.collection("items")
                            .whereIn("name", itemBuild)
                            .get()
                            .addOnSuccessListener { snapshot ->
                                itemImageContainer.removeAllViews()
                                for (doc in snapshot.documents) {
                                    val imageUrl = doc.getString("imageUrl") ?: continue
                                    val imageView = ImageView(requireContext()).apply {
                                        layoutParams = LinearLayout.LayoutParams(100, 100).apply {
                                            setMargins(8, 8, 8, 8)
                                        }
                                        scaleType = ImageView.ScaleType.CENTER_CROP
                                    }
                                    Glide.with(this@PostDetailFragment)
                                        .load(imageUrl)
                                        .placeholder(R.drawable.placeholder_image)
                                        .into(imageView)
                                    itemImageContainer.addView(imageView)
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "아이템 이미지 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            }


                        btnSaveItemBuild?.setOnClickListener {
                            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener

                            val inputView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_title_input, null)
                            val editTitle = inputView.findViewById<EditText>(R.id.editTextDialogTitle)

                            AlertDialog.Builder(requireContext())
                                .setTitle("제목을 적어주세요")
                                .setView(inputView)
                                .setPositiveButton("다음") { _, _ ->
                                    val inputTitle = editTitle.text.toString().trim()
                                    if (inputTitle.isEmpty()) {
                                        Toast.makeText(requireContext(), "제목을 입력해주세요", Toast.LENGTH_SHORT).show()
                                        return@setPositiveButton
                                    }

                                    // 🔥 이름 → 아이템 ID 변환해서 저장하는 로직
                                    val db = FirebaseFirestore.getInstance()
                                    val itemsRef = db.collection("items")

                                    itemsRef.get().addOnSuccessListener { snapshot ->
                                        val nameToIdMap = snapshot.documents.associate {
                                            it.getString("name") to it.getString("id")
                                        }

                                        val itemIds = itemBuild.mapNotNull { nameToIdMap[it] }

                                        val saveData = hashMapOf(
                                            "configName" to inputTitle,
                                            "slots" to itemIds
                                        )

                                        db.collection("users")
                                            .document(currentUid)
                                            .collection("savedConfigurations")
                                            .add(saveData)
                                            .addOnSuccessListener {
                                                Toast.makeText(context, "아이템 저장이 되었습니다", Toast.LENGTH_SHORT).show()

                                                AlertDialog.Builder(requireContext())
                                                    .setTitle("챔피언 능력치 계산기로 이동하겠습니까?")
                                                    .setPositiveButton("이동") { _, _ ->
                                                        val intent = Intent(requireContext(), ChampcalActivity::class.java)
                                                        intent.putStringArrayListExtra("itemBuild", ArrayList(itemIds))
                                                        intent.putExtra("championId", document.getString("championId") ?: "")
                                                        startActivity(intent)
                                                    }
                                                    .setNegativeButton("취소", null)
                                                    .show()
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(context, "아이템 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                    }.addOnFailureListener { e ->
                                        Toast.makeText(context, "아이템 데이터 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .setNegativeButton("취소", null)
                                .show()
                        }


                    }


                    // currentPost 세팅 (수정/삭제 기능)
                    currentPost = document.toObject(Post::class.java)?.copy(postId = document.id)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "게시글 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (postId.isNotEmpty()) {
            val postRef = firestore.collection("posts").document(postId)
            postRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val postUid = document.getString("uid")
                        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                        val currentViews = document.getLong("views") ?: 0

                        if (postUid != null && postUid != currentUid) {
                            postRef.update("views", FieldValue.increment(1))
                                .addOnSuccessListener {
                                    detailPostViewCount.text = "조회수: ${currentViews + 1}"
                                    loadPostDetail()  // 🔥 조회수 증가 후에 게시글 내용 불러오기
                                }
                                .addOnFailureListener { e ->
                                    Log.e("FirestoreError", "조회수 증가 실패: ${e.message}")
                                    detailPostViewCount.text = "조회수: $currentViews"
                                    loadPostDetail()
                                }
                        } else {
                            detailPostViewCount.text = "조회수: $currentViews"
                            loadPostDetail()
                        }

                    } else {
                        Toast.makeText(context, "게시글이 존재하지 않습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "게시글 로딩 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }

            view.findViewById<ImageView>(R.id.backButton).setOnClickListener {
                requireActivity().onBackPressed()
            }

            loadComments()
        } else {
            Toast.makeText(context, "Invalid post ID", Toast.LENGTH_SHORT).show()
        }
    }



    private fun createNotificationIfEnabled(
        recipientUid: String,
        senderUid: String,
        senderNickname: String,
        postId: String,
        commentId: String?,
        type: String
    ) {
        val db = FirebaseFirestore.getInstance()
        db.collection("user_profiles").document(recipientUid)
            .get()
            .addOnSuccessListener { doc ->
                val notificationOn = doc.getBoolean("notificationOn") ?: true
                if (notificationOn) {
                    val notif = mapOf(
                        "senderUid" to senderUid,
                        "senderNickname" to senderNickname,
                        "postId" to postId,
                        "commentId" to commentId,
                        "type" to type,
                        "timestamp" to System.currentTimeMillis(),
                        "isRead" to false
                    )
                    db.collection("users").document(recipientUid)
                        .collection("notifications")
                        .add(notif)
                }
            }
    }


    // 댓글 작성 메서드
    private fun postComment(commentText: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid
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
                    .addOnSuccessListener { result ->
                        Toast.makeText(context, "댓글 작성 완료!", Toast.LENGTH_SHORT).show()
                        // 🔔 알림 생성 호출
                        if (postAuthorUid.isNotEmpty() && postAuthorUid != uid) {
                            createNotificationIfEnabled(
                                recipientUid = postAuthorUid,
                                senderUid = uid,
                                senderNickname = nickname,
                                postId = postId,
                                commentId = result.id,
                                type = "comment"
                            )
                        }
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

    // 답글 작성 메서드
    private fun postReply(parentComment: Comment, replyText: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid
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
                    .addOnSuccessListener { result ->
                        Toast.makeText(context, "답글 작성 완료!", Toast.LENGTH_SHORT).show()
                        // 🔔 알림 생성 호출
                        if (postAuthorUid.isNotEmpty() && postAuthorUid != uid) {
                            createNotificationIfEnabled(
                                recipientUid = postAuthorUid,
                                senderUid = uid,
                                senderNickname = nickname,
                                postId = postId,
                                commentId = result.id,
                                type = "reply"
                            )
                        }
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

    // 알림을 Firestore에 저장하는 헬퍼
    private fun createNotification(
        recipientUid: String,
        senderUid: String,
        senderNickname: String,
        postId: String,
        commentId: String?,
        type: String
    ) {
        val notif = mapOf(
            "senderUid" to senderUid,
            "senderNickname" to senderNickname,
            "postId" to postId,
            "commentId" to commentId,
            "type" to type,
            "timestamp" to System.currentTimeMillis(),
            "isRead" to false
        )
        firestore.collection("users")
            .document(recipientUid)
            .collection("notifications")
            .add(notif)
    }

    private fun loadComments() {
        firestore.collection("posts")
            .document(postId)
            .collection("comments")
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val allDocs = querySnapshot.documents
                val commentsWithReplies = mutableListOf<Comment>()
                var processedCount = 0

                if (allDocs.isEmpty()) {
                    comments.clear()
                    commentAdapter.notifyDataSetChanged()
                } else {
                    for (doc in allDocs) {
                        val parentId = doc.getString("parentId")
                        if (parentId == null) {
                            val baseComment = Comment(
                                commentId = doc.id,
                                text = doc.getString("text") ?: "",
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                uid = doc.getString("uid"),
                                nickname = doc.getString("nickname"),
                                replies = emptyList()
                            )
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
                                        view?.findViewById<TextView>(R.id.toggleCommentText)
                                            ?.text = "댓글[${allDocs.size}]"

                                        // 하이라이트 댓글로 스크롤
                                        highlightCommentId?.let { targetId ->
                                            val pos = comments.indexOfFirst { it.commentId == targetId }
                                            if (pos >= 0) {
                                                commentRecyclerView.post {
                                                    commentRecyclerView.scrollToPosition(pos)
                                                    commentRecyclerView
                                                        .findViewHolderForAdapterPosition(pos)
                                                        ?.itemView
                                                        ?.setBackgroundResource(R.drawable.highlight_bg)
                                                }
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "댓글 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onStart() {
        super.onStart()
        // Firestore 리스너 등록
        listenToPostChanges()
    }

    override fun onStop() {
        super.onStop()
        postListener?.remove()
    }

    private fun listenToPostChanges() {
        val postRef = firestore.collection("posts").document(postId)
        postListener = postRef.addSnapshotListener { document, error ->
            if (error != null) {
                Log.e("PostDetailFragment", "데이터 리스너 오류: ${error.message}")
                return@addSnapshotListener
            }
            document?.let {
                postAuthorUid = it.getString("uid") ?: ""
            }
        }
    }

    private fun showMenuOptions() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null || currentPost == null) {
            Toast.makeText(context, "데이터 로딩 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentUser.uid != currentPost?.uid) {
            Toast.makeText(context, "수정 및 삭제 권한이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf("삭제", "수정")
        AlertDialog.Builder(requireContext())
            .setTitle("옵션 선택")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> deletePost()
                    1 -> {
                        Intent(requireContext(), WritingActivity::class.java).apply {
                            putExtra("temp_post", Gson().toJson(currentPost))
                            putExtra("category", currentPost?.category)
                            startActivity(this)
                        }
                    }
                }
            }
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
