package com.example.cardproject.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class LocalUser(
    @PrimaryKey
    val id: Long,
    val username: String,
    val email: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val createdAt: Long,
    val lastLoginAt: Long,
    val isActive: Boolean = true,
    val modelVersion: Int=0
){
    fun toUser(): User {
        return User(
            id = this.id.toInt(),
            username = this.username,
            email = this.email,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(this.createdAt))
        )
    }
}