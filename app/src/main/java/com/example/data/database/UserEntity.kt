package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserEntity(
    @PrimaryKey val email: String,
    val displayName: String,
    val avatarUrl: String,
    val isGoogleUser: Boolean = false,
    val lastLoginTime: Long = System.currentTimeMillis()
)
