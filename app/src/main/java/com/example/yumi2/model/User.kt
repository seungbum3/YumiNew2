package com.example.yumi2.model

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.yumi2.R

data class User(
    val id: String = "",
    val nickname: String = "",
    val password: String = "",
    val phone_number: String = "",
    val createdAt: com.google.firebase.Timestamp? = null // Firestore Timestamp 사용
)
