package com.example.yumi2

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage

class ProfileEditDialog : DialogFragment() {
    interface ProfileUpdateListener {
        fun onProfileUpdated(nickname: String, bio: String, imageUrl: String?)
    }

    var listener: ProfileUpdateListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? ProfileUpdateListener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private lateinit var profileImageView: ImageView
    private lateinit var editNickname: EditText
    private lateinit var editBio: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var btnCheckNickname: Button

    private var imageUri: Uri? = null

    private var originalNickname: String = ""
    private var isNicknameChecked = false
    private var isNicknameAvailable = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_profile_edit, null)
        dialog.setContentView(view)

        val sharedPref = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val uid = sharedPref.getString("loggedInUID", null) ?: ""

        if (uid.isEmpty()) {
            Toast.makeText(context, "로그인 정보가 없습니다!", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        editNickname = view.findViewById(R.id.editNickname)
        editBio = view.findViewById(R.id.editBio)
        profileImageView = view.findViewById(R.id.profileImageView)
        btnSave = view.findViewById(R.id.btnSave)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnCheckNickname = view.findViewById(R.id.btnCheckNickname)

        loadUserProfile(uid) // 사용자 정보 불러오기

        btnCheckNickname.setOnClickListener {
            checkNicknameDuplicate()
        }

        profileImageView.setOnClickListener {
            selectImage()
        }

        btnSave.setOnClickListener {
            saveProfileChanges(uid)
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        return dialog
    }

    private fun loadUserProfile(uid: String) {
        val db = FirebaseFirestore.getInstance()
        val userProfileRef = db.collection("user_profiles").document(uid)

        Log.d("Firestore", "🔍 Firestore에서 사용자 정보 불러오기 시작: UID = $uid")

        userProfileRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val nickname = document.getString("nickname") ?: "알 수 없음"
                    originalNickname = nickname
                    editNickname.setText(nickname)
                    val bio = document.getString("myinfo") ?: ""
                    editBio.setText(bio)

                    val imageUrl = document.getString("profileImageUrl")
                    val defaultProfileUrl = "gs://yumi-5f5c0.firebasestorage.app/default_profile.jpg"

                    val finalUrl = if (!imageUrl.isNullOrEmpty()) imageUrl else defaultProfileUrl
                    convertGsUrlToHttp(finalUrl) { httpUrl ->
                        Glide.with(this)
                            .load(httpUrl)
                            .circleCrop()
                            .into(profileImageView)
                    }
                } else {
                    Log.e("Firestore", "❌ Firestore에서 사용자 정보 없음!")
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "❌ Firestore에서 데이터 가져오기 실패", e)
            }
    }

    private fun convertGsUrlToHttp(gsUrl: String, onComplete: (String?) -> Unit) {
        FirebaseStorage.getInstance().getReferenceFromUrl(gsUrl)
            .downloadUrl
            .addOnSuccessListener { downloadUri ->
                onComplete(downloadUri.toString())
            }
            .addOnFailureListener { e ->
                Log.e("URL Conversion", "gs:// URL 변환 실패", e)
                onComplete(null)
            }
    }

    private fun saveProfileChanges(uid: String) {
        val newNickname = editNickname.text.toString().trim()
        val newBio = editBio.text.toString().trim().take(20)

        if (newNickname.isEmpty()) {
            Toast.makeText(context, "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (newNickname == originalNickname) {
            saveProfileToFirestore(uid, newNickname, newBio, imageUri)
            return
        }

        if (!isNicknameChecked) {
            Toast.makeText(context, "닉네임 중복 확인을 해주세요!", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isNicknameAvailable) {
            Toast.makeText(context, "이미 사용 중인 닉네임입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        saveProfileToFirestore(uid, newNickname, newBio, imageUri)
    }

    private fun saveProfileToFirestore(uid: String, nickname: String, bio: String, imageUri: Uri?) {
        val profileUpdate = mutableMapOf<String, Any>("myinfo" to bio, "nickname" to nickname)

        if (imageUri != null) {
            uploadProfileImage(uid, imageUri) { downloadUrl ->
                if (downloadUrl != null) {
                    profileUpdate["profileImageUrl"] = downloadUrl
                }
                updateUserProfile(uid, profileUpdate)
            }
        } else {
            updateUserProfile(uid, profileUpdate)
        }
    }

    private fun uploadProfileImage(uid: String, imageUri: Uri, onComplete: (String?) -> Unit) {
        val storageRef = FirebaseStorage.getInstance().reference.child("profile_images/$uid/profile.jpg")
        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    onComplete(downloadUri.toString())
                }.addOnFailureListener { e ->
                    Log.e("ProfileImage", "다운로드 URL 받기 실패", e)
                    onComplete(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("ProfileImage", "이미지 업로드 실패", e)
                onComplete(null)
            }
    }

    private fun updateUserProfile(uid: String, profileUpdate: Map<String, Any>) {
        val userProfileRef = FirebaseFirestore.getInstance()
            .collection("user_profiles")
            .document(uid)

        userProfileRef.set(profileUpdate, SetOptions.merge())
            .addOnSuccessListener {
                activity?.let {
                    Toast.makeText(it, "프로필 저장 성공!", Toast.LENGTH_SHORT).show()
                }

                // Listener를 통해 UI 즉시 갱신
                val nickname = profileUpdate["nickname"] as? String ?: ""
                val bio = profileUpdate["myinfo"] as? String ?: ""
                val imageUrl = profileUpdate["profileImageUrl"] as? String

                listener?.onProfileUpdated(nickname, bio, imageUrl)

                dismiss()
            }
            .addOnFailureListener { e ->
                activity?.let {
                    Toast.makeText(it, "프로필 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }




    private fun selectImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK) {
            imageUri = data?.data
            profileImageView.setImageURI(imageUri) // 선택한 이미지를 즉시 UI에 반영
        }
    }

    private fun checkNicknameDuplicate() {
        val nickname = editNickname.text.toString().trim()
        if (nickname.isEmpty()) {
            Toast.makeText(context, "닉네임을 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 현재 닉네임과 동일하면 중복검사 필요 없음
        if (nickname == originalNickname) {
            isNicknameChecked = true
            isNicknameAvailable = true
            Toast.makeText(context, "기존 닉네임입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("user_profiles")
            .whereEqualTo("nickname", nickname)
            .get()
            .addOnSuccessListener { documents ->
                isNicknameChecked = true
                isNicknameAvailable = documents.isEmpty
                val message = if (isNicknameAvailable) "사용 가능한 닉네임입니다!" else "이미 사용 중인 닉네임입니다."
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
    }

    companion object {
        private const val IMAGE_PICK_CODE = 1000
    }
}
