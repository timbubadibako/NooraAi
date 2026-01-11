package com.example.nooraai.data

data class Category(
    val id: Int,
    val slug: String,
    val name: String,
    val description: String? = null,
    val isActive: Boolean = true
)