package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val handle: String, // e.g. "marquesbrownlee" or "@handle"
    val channelName: String,
    val avatarUrl: String,
    val subscribedAt: Long = System.currentTimeMillis()
)
