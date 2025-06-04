package com.example.yumi2.model

data class NotificationItem(
    val senderUid: String = "",
    val senderNickname: String = "",
    val postId: String = "",
    val commentId: String? = null,
    val type: String = "",            // "comment", "reply", "chat", "friend_request" 등
    val timestamp: Long = 0L,
    val chatId: String? = null,
    var isRead: Boolean = false,      // 나중에 읽음 처리할 수도 있어요
    val message: String? = null       // 🔥 채팅 알림 메시지
)
