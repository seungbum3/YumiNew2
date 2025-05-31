package com.example.yumi2

import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.ChatAdapter
import com.example.yumi2.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var chatTitle: TextView
    private lateinit var backButton: ImageButton

    private lateinit var chatId: String
    private lateinit var friendId: String
    private lateinit var friendNickname: String
    private lateinit var currentUserId: String

    private val messages = mutableListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        chatAdapter = ChatAdapter(messages, FirebaseAuth.getInstance().currentUser?.uid ?: "")
        chatRecyclerView.adapter = chatAdapter
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        chatTitle = findViewById(R.id.chatTitle)
        backButton = findViewById(R.id.backButton)


        chatId = intent.getStringExtra("chatId") ?: ""
        Log.d("ChatActivity", "📌 가져온 chatId: $chatId")
        loadMessages(chatId)

        // 현재 로그인한 유저 ID 가져오기
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        if (currentUserId.isEmpty()) {
            finish()
            return
        }


        friendId = intent.getStringExtra("friendId") ?: ""
        friendNickname = intent.getStringExtra("friendNickname") ?: "알 수 없는 사용자"


        // 채팅방 상단 제목 친구 이름으로
        chatTitle.text = friendNickname

        // 뒤로가기 버튼 동작
        backButton.setOnClickListener {
            finish()
        }

        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(mutableListOf(), currentUserId)
        chatRecyclerView.adapter = chatAdapter

        if (friendId.isNotEmpty()) {
            getOrCreateChatRoom(currentUserId, friendId) { chatRoomId ->
                chatId = chatRoomId
                loadMessages(chatId)
            }
        }

        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty() && ::chatId.isInitialized) {
                sendMessage(chatId, currentUserId, message)
                messageInput.setText("") // 입력창 초기화
            }
            if (!::chatId.isInitialized) {
                Log.e("ChatActivity", "⚠️ chatId가 초기화되지 않았습니다!")
                return@setOnClickListener
            }

        }

        val rootView = window.decorView.rootView
        rootView.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val rect = Rect()
                rootView.getWindowVisibleDisplayFrame(rect)
                val screenHeight = rootView.height
                val keypadHeight = screenHeight - rect.bottom

                if (keypadHeight > screenHeight * 0.15) { // 키보드가 올라온 경우
                    chatRecyclerView.postDelayed({
                        chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                    }, 100)
                }
            }
        })

        // 여러 줄 입력 시 높이 제한하여 버튼 위치 고정
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                messageInput.post {
                    val lineCount = messageInput.lineCount

                    if (lineCount in 1..5) { // 1~5줄까지 자연스럽게 크기 확장
                        messageInput.layoutParams.height =
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    } else if (lineCount > 5) { // 5줄 이상 입력하면 크기 유지 & 내부 스크롤
                        messageInput.layoutParams.height =
                            messageInput.lineHeight * 5 + 20 // 크기를 약간 키워 4번째 줄 기준으로 맞춤
                        messageInput.scrollTo(0, messageInput.bottom)
                    }
                    messageInput.requestLayout() // 크기 변경 후 적용
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })




        messageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                chatRecyclerView.postDelayed({
                    chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                }, 300)
            }
        }

        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty() && ::chatId.isInitialized) {
                sendMessage(chatId, currentUserId, message)
                messageInput.setText("") // 입력창 초기화
            }
        }
    }

    // 채팅방을 찾거나 생성하는 함수
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
                    "users" to listOf(userA, userB),  // ✅ 두 유저 모두 포함
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


    // 메시지 전송 함수
    private fun sendMessage(chatId: String, senderId: String, message: String) {
        val db = FirebaseFirestore.getInstance()
        val messageRef = db.collection("chats").document(chatId).collection("messages").document()

        val messageData = hashMapOf(
            "senderId" to senderId,
            "message" to message, // ✅ Firestore 필드명과 일치
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        messageRef.set(messageData)
            .addOnSuccessListener {
                Log.d("ChatActivity", "✅ 메시지 전송 성공: $message")

                runOnUiThread {
                    chatAdapter.updateMessages(messages)
                    chatRecyclerView.scrollToPosition(messages.size - 1)
                    Log.d("ChatActivity", "📢 RecyclerView 업데이트 완료")
                }


            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ 메시지 전송 실패", e)
            }

        db.collection("chats").document(chatId)
            .update(mapOf(
                "lastMessage" to message,
                "updatedAt" to com.google.firebase.Timestamp.now()
            ))
            .addOnSuccessListener {
                Log.d("ChatActivity", "✅ 채팅방 정보 업데이트 완료")
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ 채팅방 정보 업데이트 실패", e)
            }
    }


    // 채팅 내역 불러오기 함수
    private fun loadMessages(chatId: String) {
        Log.d("ChatActivity", "📥 loadMessages() 호출됨 - chatId: $chatId")
        val db = FirebaseFirestore.getInstance()
        val messagesRef = db.collection("chats").document(chatId).collection("messages")

        messagesRef.orderBy("timestamp")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("ChatActivity", "❌ 메시지 불러오기 실패", e)
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) {
                    Log.d("ChatActivity", "📭 불러온 메시지가 없음")
                    return@addSnapshotListener
                }

                Log.d("ChatActivity", "✅ 불러온 메시지 개수: ${snapshots.size()}")

                // 📌 기존 리스트를 업데이트하고 RecyclerView에 반영
                messages.clear()
                for (doc in snapshots.documents) {
                    val message = doc.toObject(ChatMessage::class.java)
                    if (message != null) {
                        Log.d("ChatActivity", "📩 메시지 로드: ${message.message}")
                        messages.add(message)
                    } else {
                        Log.e("ChatActivity", "❌ 메시지 변환 실패 - 문서 ID: ${doc.id}")
                    }
                }

                // 📌 UI 업데이트 (새로운 메시지를 RecyclerView에 표시)
                runOnUiThread {
                    chatAdapter.updateMessages(messages)
                    chatRecyclerView.scrollToPosition(messages.size - 1)
                    Log.d("ChatActivity", "📢 RecyclerView 업데이트 완료")
                }

            }
    }
}
