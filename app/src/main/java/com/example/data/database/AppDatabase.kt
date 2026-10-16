package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SubscriptionEntity::class, WatchHistoryEntity::class, VideoEntity::class, UserEntity::class, SearchHistoryEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelSubscriptionDao(): ChannelSubscriptionDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun videoDao(): VideoDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `videos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `publicId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnail` TEXT NOT NULL, `url` TEXT NOT NULL, `views` INTEGER NOT NULL, `duration` REAL NOT NULL, `channelName` TEXT NOT NULL, `avatarUrl` TEXT NOT NULL, `handle` TEXT NOT NULL, `uploaderEmail` TEXT NOT NULL, `createdAt` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_profiles` (`email` TEXT NOT NULL, `displayName` TEXT NOT NULL, `avatarUrl` TEXT NOT NULL, `isGoogleUser` INTEGER NOT NULL, `lastLoginTime` INTEGER NOT NULL, PRIMARY KEY(`email`))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `subscriptions` (`handle` TEXT NOT NULL, `channelName` TEXT NOT NULL, `avatarUrl` TEXT NOT NULL, `subscribedAt` INTEGER NOT NULL, PRIMARY KEY(`handle`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `watch_history` (`publicId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnail` TEXT NOT NULL, `channelName` TEXT NOT NULL, `handle` TEXT NOT NULL, `watchedAt` INTEGER NOT NULL, PRIMARY KEY(`publicId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `videos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `publicId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnail` TEXT NOT NULL, `url` TEXT NOT NULL, `views` INTEGER NOT NULL, `duration` REAL NOT NULL, `channelName` TEXT NOT NULL, `avatarUrl` TEXT NOT NULL, `handle` TEXT NOT NULL, `uploaderEmail` TEXT NOT NULL, `createdAt` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_profiles` (`email` TEXT NOT NULL, `displayName` TEXT NOT NULL, `avatarUrl` TEXT NOT NULL, `isGoogleUser` INTEGER NOT NULL, `lastLoginTime` INTEGER NOT NULL, PRIMARY KEY(`email`))")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`query` TEXT NOT NULL, `searchedAt` INTEGER NOT NULL, PRIMARY KEY(`query`))")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE videos ADD COLUMN description TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        // NEW: adds a unique index on publicId so REPLACE-on-conflict
        // works correctly and duplicate video rows can't accumulate,
        // and dedupes any pre-existing duplicate rows before the index
        // is created (a unique index will fail to create if dupes exist).
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep only the highest id row per publicId (most recent insert)
                db.execSQL(
                    """
                    DELETE FROM videos
                    WHERE id NOT IN (
                        SELECT MAX(id) FROM videos GROUP BY publicId
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_videos_publicId ON videos(publicId)"
                )
            }
        }

        // The `videos` table used to hold fake "uploads" saved when an upload
        // failed, plus videos that may since have gone private on the server.
        // It's only a cache, so drop its contents; it refills from the API.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM videos")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "airstreamx_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}