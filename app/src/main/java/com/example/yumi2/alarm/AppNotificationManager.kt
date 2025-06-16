package com.example.yumi2.alarm.util

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object AppNotificationManager {
    // 기본값은 알림 ON (true)
    var notificationOn: Boolean = true

    fun loadNotificationSetting(context: Context, onLoaded: (() -> Unit)? = null) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("user_profiles").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                notificationOn = doc.getBoolean("notificationOn") ?: true
                Log.d("AppNotificationManager", "notificationOn 불러옴: $notificationOn")
                onLoaded?.invoke()
            }
            .addOnFailureListener {
                notificationOn = true
                onLoaded?.invoke()
            }
    }

    fun setNotificationOn(context: Context, isOn: Boolean) {
        notificationOn = isOn
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("user_profiles").document(uid)
            .update("notificationOn", isOn)
    }
}
