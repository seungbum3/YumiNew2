package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.adapter.NameSearchAdapter
import com.example.yumi2.viewmodel.SummonerViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class NameSearchActivity : AppCompatActivity() {
    private val viewModel: SummonerViewModel by viewModels()
    private val nameSearchList = mutableListOf<String>()

    // 어댑터를 클래스 프로퍼티로 선언
    private lateinit var adapter: NameSearchAdapter

    // 🔹 현재 로그인한 uid를 가져옴
    private val uid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.name_search)

        // SharedPreferences에서 최근 검색 목록 불러오기
        loadRecentSearches(uid)

        // 최근 검색 목록 RecyclerView 설정
        val recyclerView = findViewById<RecyclerView>(R.id.recentSearchRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        val dividerItemDecoration = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecoration)

        // NameSearchAdapter 생성 후 클래스 프로퍼티에 할당
        adapter = NameSearchAdapter(
            nameSearchList,
            onItemClick = { item ->
                searchSummoner(item)
            },
            onDeleteClick = { item ->
                adapter.removeItem(item)
                saveRecentSearches(uid)  // 삭제 후 저장
            }
        )
        recyclerView.adapter = adapter

        // "전체 삭제" 버튼 클릭 시 전체 목록 삭제
        val clearAllButton = findViewById<Button>(R.id.clearAllButton)
        clearAllButton.setOnClickListener {
            adapter.clearAll()
            saveRecentSearches(uid)  // 전체 삭제 후 저장
        }

        val pageBack: Button = findViewById(R.id.PageBack)
        val searchInput = findViewById<EditText>(R.id.NameSearch)
        val searchIcon = findViewById<ImageView>(R.id.Search_icon)
        val searchButton = findViewById<Button>(R.id.search)
        val errorText = findViewById<TextView>(R.id.errorText) // 오류 메시지 표시용

        // 뒤로 가기 버튼 클릭 시 MainpageActivity로 이동
        pageBack.setOnClickListener {
            val intent = Intent(this, MainpageActivity::class.java)
            startActivity(intent)
        }

        // 검색창 입력 감지하여 아이콘 숨김 처리 및 오류 메시지 초기화
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchIcon.visibility = if (s.isNullOrEmpty()) ImageView.VISIBLE else ImageView.GONE
                errorText.text = ""
                errorText.visibility = TextView.GONE
            }
            override fun afterTextChanged(s: Editable?) { }
        })

        // 검색 버튼 클릭 시
        searchButton.setOnClickListener {
            val inputText = searchInput.text.toString().trim()

            // '#'가 포함되어 있지 않으면 오류 메시지 표시
            if (!inputText.contains("#")) {
                errorText.text = "올바른 형식으로 입력하세요"
                errorText.visibility = TextView.VISIBLE
                return@setOnClickListener
            }

            val parts = inputText.split("#")
            val gameName = parts[0].trim()
            val tagLine = parts.getOrNull(1)?.trim() ?: ""

            // FirebaseAuth에서 현재 사용자 uid 가져오기
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                errorText.text = "로그인 상태가 아닙니다."
                errorText.visibility = TextView.VISIBLE
                return@setOnClickListener
            }
            val uid = currentUser.uid


            // Riot API 호출
            viewModel.searchSummoner(gameName, tagLine, uid)

            // StateFlow를 수집하여 결과에 따라 UI 업데이트
            lifecycleScope.launch {
                val summoner = try {
                    withTimeout(2500L) {  // 최대 2.5초 대기
                        viewModel.summonerInfo.first { it != null }
                    }
                } catch (e: Exception) {
                    null
                }

                if (summoner == null) {
                    errorText.text = "닉네임이 존재하지 않습니다"
                    errorText.visibility = TextView.VISIBLE
                } else {
                    errorText.visibility = TextView.GONE

                    // API 응답의 올바른 소환사 이름과 태그를 사용
                    val correctedGameName = summoner.gameName ?: gameName
                    val correctedTagLine = summoner.tagLine ?: tagLine
                    val formattedName = "$correctedGameName#$correctedTagLine"

                    // 기존 목록에서 대소문자 구분 없이 해당 검색어가 있다면 제거
                    val index = nameSearchList.indexOfFirst { it.equals(formattedName, ignoreCase = true) }
                    if (index != -1) {
                        nameSearchList.removeAt(index)
                        adapter.notifyItemRemoved(index)
                    }

                    // 최신 순으로 저장
                    nameSearchList.add(0, formattedName)
                    adapter.notifyItemInserted(0)
                    saveRecentSearches(uid)

                    val intent = Intent(this@NameSearchActivity, NameSearchMainActivity::class.java)
                    intent.putExtra("gameName", gameName)
                    intent.putExtra("tagLine", tagLine)
                    startActivity(intent)
                    }
                }
            }
        }

    // 저장된 닉네임을 클릭했을 때 해당 닉네임을 최신으로 재정렬하고 검색 수행 후 인텐트
    private fun searchSummoner(nicknameWithTag: String) {
        val parts = nicknameWithTag.split("#")
        if (parts.size >= 2) {
            val gameName = parts[0].trim()
            val tagLine = parts[1].trim()

            // 🔹 대소문자 구분 없이 찾고 싶다면 ignoreCase = true 사용
            val currentIndex = nameSearchList.indexOfFirst {
                it.equals(nicknameWithTag, ignoreCase = true)
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) return
            val uid = currentUser.uid

            // 🔹 리스트에서 해당 항목을 제거 후 맨 앞(0번 인덱스)에 추가
            if (currentIndex >= 0) {
                nameSearchList.removeAt(currentIndex)
                adapter.notifyItemRemoved(currentIndex)

                // 리스트 맨 앞에 추가
                nameSearchList.add(0, nicknameWithTag)
                adapter.notifyItemInserted(0)

                saveRecentSearches(uid)
            }

            // 🔹 Riot API 호출 및 다음 화면 이동
            viewModel.searchSummoner(gameName, tagLine, uid)
            val intent = Intent(this, NameSearchMainActivity::class.java)
            intent.putExtra("gameName", gameName)
            intent.putExtra("tagLine", tagLine)
            startActivity(intent)
        }
    }

    private fun getPrefsName(uid: String): String {
        return "recent_searches_$uid"
    }

    // SharedPreferences에서 최근 검색 목록 불러오기
    private fun loadRecentSearches(uid: String) {
        val prefsName = getPrefsName(uid) // "recent_searches_$uid"
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE) // 🔹 수정
        val joinedString = prefs.getString("search_list_ordered", "") ?: ""

        // 🔹 쉼표로 구분된 문자열을 split하여 리스트로 복원
        val items = joinedString.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        nameSearchList.clear()
        nameSearchList.addAll(items)
    }


    // SharedPreferences에 최근 검색 목록 저장하기
    private fun saveRecentSearches(uid: String) {
        val prefsName = getPrefsName(uid) // "recent_searches_$uid"
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE) // 🔹 수정
        val editor = prefs.edit()

        // 🔹 리스트를 쉼표로 구분된 문자열로 만들기
        val joinedString = nameSearchList.joinToString(separator = ",")
        editor.putString("search_list_ordered", joinedString)

        editor.apply()
    }

}
