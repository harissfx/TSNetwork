package com.textsocial.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PostCacheEntity::class,
        ProfileCacheEntity::class,
        StoryCacheEntity::class,
        ConversationCacheEntity::class,
        MessageCacheEntity::class,
        NotificationCacheEntity::class,
        LinkPreviewCacheEntity::class,
        TrendingHashtagCacheEntity::class,
        CacheMetaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postCacheDao(): PostCacheDao
    abstract fun profileCacheDao(): ProfileCacheDao
    abstract fun storyCacheDao(): StoryCacheDao
    abstract fun conversationCacheDao(): ConversationCacheDao
    abstract fun messageCacheDao(): MessageCacheDao
    abstract fun notificationCacheDao(): NotificationCacheDao
    abstract fun linkPreviewCacheDao(): LinkPreviewCacheDao
    abstract fun trendingHashtagCacheDao(): TrendingHashtagCacheDao
    abstract fun cacheMetaDao(): CacheMetaDao

    suspend fun clearAllCache() {
        clearAllTables()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "textsocial_cache.db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}