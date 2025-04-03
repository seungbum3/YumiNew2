package com.example.yumi2.repository

import com.example.yumi2.model.User
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID


class UserRepository {
    private val db = FirebaseFirestore.getInstance()


    fun checkDuplicate(field: String, value: String, callback: (Boolean) -> Unit) {
        db.collection("users")
            .whereEqualTo(field, value)
            .get()
            .addOnSuccessListener { documents ->
                callback(documents.isEmpty())
            }
            .addOnFailureListener {
                callback(false)
            }
    }


    fun registerUser(user: User, callback: (Boolean, String?) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val userId = UUID.randomUUID().toString() // ✅ 무조건 새로운 UUID 생성

        val userData = hashMapOf(
            "id" to user.id,
            "phone_number" to user.phone_number,
            "nickname" to user.nickname,
            "password" to user.password,
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        val profileData = hashMapOf(
            "profileImageUrl" to "https://firebasestorage.googleapis.com/v0/b/yumi-5f5c0.appspot.com/o/default_profile.jpg?alt=media",
            "myinfo" to "안녕하세요! 반갑습니다.",
            "theme" to "light",
            "friendRequestCount" to "0"
        )

        val userRef = db.collection("users").document(userId)
        val profileRef = db.collection("user_profiles").document(userId)

        userRef.set(userData)
            .addOnSuccessListener {
                profileRef.set(profileData) // ✅ users 저장 성공 후 user_profiles 저장
                    .addOnSuccessListener {
                        callback(true, null)
                    }
                    .addOnFailureListener { e ->
                        callback(false, e.message)
                    }
            }
            .addOnFailureListener { e ->
                callback(false, e.message)
            }
    }





}








