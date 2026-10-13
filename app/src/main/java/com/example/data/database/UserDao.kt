package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM user_profiles ORDER BY lastLoginTime DESC LIMIT 1")
    fun getActiveUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM user_profiles ORDER BY lastLoginTime DESC LIMIT 1")
    suspend fun getActiveUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM user_profiles")
    suspend fun clearUser()
}
