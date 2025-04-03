package com.example.yumi2.viewmodel

import androidx.lifecycle.ViewModel
import com.example.yumi2.repository.UserRepository
import com.example.yumi2.model.User


class RegisterViewModel : ViewModel() {
    private val userRepository = UserRepository()

        // 🔹 1. 닉네임 중복 확인
        fun checkNickname(nickname: String, callback: (Boolean) -> Unit) {
            userRepository.checkDuplicate("nickname", nickname, callback)
        }

        // 🔹 2. 아이디 중복 확인
        fun checkId(id: String, callback: (Boolean) -> Unit) {
            userRepository.checkDuplicate("id", id, callback)
        }

        // 🔹 3. 회원가입 처리
        fun registerUser(id: String, password: String, nickname: String, phone: String, callback: (Boolean, String?) -> Unit) {
            val user = User(id, nickname, password, phone)
            userRepository.registerUser(user, callback)
        }
}
