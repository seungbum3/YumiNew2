package com.example.yumi2.repository

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.yumi2.R
import com.example.yumi2.model.User
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import com.google.firebase.auth.FirebaseAuth


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
        val auth = FirebaseAuth.getInstance()


        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                    val db = FirebaseFirestore.getInstance()


                    val userData = hashMapOf(
                        "id" to user.id,
                        "phone_number" to user.phone_number,
                        "nickname" to user.nickname,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )


                    val profileData = hashMapOf(
                        "profileImageUrl" to "https://firebasestorage.googleapis.com/v0/b/your_project_id.appspot.com/o/default_profile.jpg?alt=media",
                        "myinfo" to "안녕하세요! 반갑습니다.",
                        "theme" to "light",
                        "friendRequestCount" to "0"
                    )

                    val batch = db.batch()

                    val userRef = db.collection("users").document(userId)
                    val profileRef = db.collection("user_profiles").document(userId)

                    batch.set(userRef, userData)
                    batch.set(profileRef, profileData)

                    val friendsRef = db.collection("users").document(userId).collection("friends").document("default")
                    val favoritesRef = db.collection("users").document(userId).collection("favorites").document("default")

                    val defaultFriendData = hashMapOf("system" to true)
                    val defaultFavoriteData = hashMapOf("system" to true)

                    batch.set(friendsRef, defaultFriendData)
                    batch.set(favoritesRef, defaultFavoriteData)

                    batch.commit()
                        .addOnSuccessListener {
                            callback(true, null)
                        }
                        .addOnFailureListener { e ->
                            callback(false, e.message)
                        }
                } else {
                    callback(false, task.exception?.message)
                }
            }
    }



}
