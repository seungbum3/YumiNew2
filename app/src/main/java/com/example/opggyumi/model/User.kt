package com.example.opggyumi.model

data class User(
    val id: String = "",
    val nickname: String = "",
    val password: String = "",
    val phone_number: String = "",
    val createdAt: com.google.firebase.Timestamp? = null // Firestore Timestamp 사용
)
