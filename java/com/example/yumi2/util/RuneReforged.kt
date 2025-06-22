package com.example.yumi2.util

data class RuneReforged(
    val id: Int,
    val key: String,
    val slots: List<Slot>
)

data class Slot(
    val runes: List<RuneDetail>
)

data class RuneDetail(
    val id: Int,
    val key: String,
    val icon: String
)
