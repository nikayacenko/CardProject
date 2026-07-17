package com.example.cardproject.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: Int,
    val username: String,
    val email: String? = null,
    @SerializedName("created_at")
    val createdAt: String
)

data class AuthRequest(
    val username: String,
    val password: String,
    val email: String? = null
)

data class AuthResponse(
    val status: String,
    val user: User,
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String = "bearer"
)