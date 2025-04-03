package com.example.opggyumi.repository

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.opggyumi.R
import com.example.opggyumi.model.User
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID


class UserRepository {
    private val db = FirebaseFirestore.getInstance()

        // 🔹 1. 아이디 또는 닉네임 중복 확인
        fun checkDuplicate(field: String, value: String, callback: (Boolean) -> Unit) {
            db.collection("users")
                .whereEqualTo(field, value)
                .get()
                .addOnSuccessListener { documents ->
                    callback(documents.isEmpty()) // 중복 없음 = true
                }
                .addOnFailureListener {
                    callback(false) // 에러 발생 시 false 반환
                }
        }

        // 🔹 2. 회원가입 정보 Firestore에 저장
        fun registerUser(user: User, callback: (Boolean, String?) -> Unit) {
            val userId = UUID.randomUUID().toString() // 랜덤한 userId 생성
            val userWithTimestamp = user.copy(createdAt = com.google.firebase.Timestamp.now())

            db.collection("users").document(userId)
                .set(userWithTimestamp)
                .addOnSuccessListener {
                    callback(true, null) // 성공
                }
                .addOnFailureListener { e ->
                    callback(false, e.message) // 실패
                }
        }
    }
