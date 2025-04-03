package com.example.yumi2.model

data class Champion(
    var id: String? = null,
    var name: String? = null,
    var tags: List<String>? = null,
    var imageUrl: String? = null,
    var title: String? = null
)