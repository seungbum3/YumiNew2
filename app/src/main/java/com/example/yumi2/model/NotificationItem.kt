package com.example.yumi2.model

data class NotificationItem(
    val senderUid: String = "",
    val senderNickname: String = "",
    val postId: String = "",
    val commentId: String? = null,
    val type: String = "",            // "comment" or "reply"
    val timestamp: Long = 0L,
    var isRead: Boolean = false       // 나중에 읽음 처리할 수도 있어요
)
