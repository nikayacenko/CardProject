package com.example.cardproject.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cardproject.model.LocalUser

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): LocalUser?

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): LocalUser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(user: LocalUser)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Long) {
        println("🚨🚨🚨 [UserDao] УДАЛЕНИЕ ПОЛЬЗОВАТЕЛЯ! userId=$userId")
        println("🚨 Стек вызовов:")
        Exception().printStackTrace()
        // выполнение
    }

    @Query("DELETE FROM users")
    suspend fun deleteAll() {
        println("🚨🚨🚨 [UserDao] УДАЛЕНИЕ ВСЕХ ПОЛЬЗОВАТЕЛЕЙ!")
    }

    @Query("UPDATE users SET lastLoginAt = :lastLoginAt WHERE id = :userId")
    suspend fun updateLastLogin(userId: Long, lastLoginAt: Long)

    @Query("UPDATE users SET modelVersion = :modelVersion WHERE id = :userId")
    suspend fun updateModelVersion(userId: Long, modelVersion: Int)
}