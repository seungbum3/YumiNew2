package com.example.yumi2



data class ChatMessage(
    val senderId: String = "",
    val message: String = "", // ✅ Firestore의 "message" 필드와 일치하도록 수정
    val timestamp: com.google.firebase.Timestamp? = null
)
