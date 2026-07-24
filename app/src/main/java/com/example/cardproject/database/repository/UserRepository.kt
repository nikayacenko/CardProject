// data/repository/UserRepository.kt
package com.example.cardproject.data.repository

import com.example.cardproject.database.dao.UserDao
import com.example.cardproject.model.LocalUser
import com.example.cardproject.model.User
import com.example.cardproject.utils.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val tokenManager: TokenManager
) {

    suspend fun getCurrentUser(): LocalUser? {
        val userId = tokenManager.getUserId() ?: return null
        return userDao.getUserById(userId)
    }

    suspend fun getCurrentUserSync(): LocalUser? {
        val userId = tokenManager.getUserId() ?: return null
        return userDao.getUserById(userId)
    }

    suspend fun saveUserFromServer(user: User) {
        val localUser = user.toLocalUser()
        userDao.insertOrUpdate(localUser)
    }

    suspend fun saveUser(user: LocalUser) {
        userDao.insertOrUpdate(user)
    }

    suspend fun updateLastLogin() {
        val userId = tokenManager.getUserId() ?: return
        userDao.updateLastLogin(userId, System.currentTimeMillis())
    }

    suspend fun updateModelVersion(version: Int) {
        val userId = tokenManager.getUserId() ?: return
        userDao.updateModelVersion(userId, version)
    }

    suspend fun clearAll() {
        userDao.deleteAll()
    }

    suspend fun deleteCurrentUser() {
        val userId = tokenManager.getUserId() ?: return
        userDao.deleteUser(userId)
    }
}