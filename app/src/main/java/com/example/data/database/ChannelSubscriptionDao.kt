package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelSubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY subscribedAt DESC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE handle = :handle OR handle = '@' || :handle)")
    fun isSubscribed(handle: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun subscribe(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE handle = :handle OR handle = '@' || :handle")
    suspend fun unsubscribe(handle: String)
}
