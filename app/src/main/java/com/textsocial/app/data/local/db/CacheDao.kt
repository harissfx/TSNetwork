package com.textsocial.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PostCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<PostCacheEntity>)

    @Query("SELECT * FROM post_cache ORDER BY createdAt DESC")
    suspend fun getAll(): List<PostCacheEntity>

    @Query("DELETE FROM post_cache")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(posts: List<PostCacheEntity>) {
        clear()
        insertAll(posts)
    }
}

@Dao
interface ProfileCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileCacheEntity)

    @Query("SELECT * FROM profile_cache WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): ProfileCacheEntity?

    @Query("SELECT * FROM profile_cache WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): ProfileCacheEntity?

    @Query("UPDATE profile_cache SET followersCount = :followers, followingCount = :following, followCountsFetchedAt = :fetchedAt WHERE userId = :userId")
    suspend fun updateFollowCounts(userId: String, followers: Int, following: Int, fetchedAt: Long)
}

@Dao
interface StoryCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stories: List<StoryCacheEntity>)

    @Query("SELECT * FROM story_cache ORDER BY createdAt DESC")
    suspend fun getAll(): List<StoryCacheEntity>

    @Query("DELETE FROM story_cache")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(stories: List<StoryCacheEntity>) {
        clear()
        insertAll(stories)
    }
}

@Dao
interface ConversationCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationCacheEntity>)

    @Query("SELECT * FROM conversation_cache ORDER BY lastMessageTime DESC")
    suspend fun getAll(): List<ConversationCacheEntity>

    @Query("DELETE FROM conversation_cache")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(conversations: List<ConversationCacheEntity>) {
        clear()
        insertAll(conversations)
    }
}

@Dao
interface MessageCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageCacheEntity>)

    @Query("SELECT * FROM message_cache WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getForConversation(conversationId: String): List<MessageCacheEntity>

    @Query("DELETE FROM message_cache WHERE conversationId = :conversationId")
    suspend fun clearForConversation(conversationId: String)

    @Transaction
    suspend fun replaceForConversation(conversationId: String, messages: List<MessageCacheEntity>) {
        clearForConversation(conversationId)
        insertAll(messages)
    }
}

@Dao
interface NotificationCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<NotificationCacheEntity>)

    @Query("SELECT * FROM notification_cache ORDER BY createdAt DESC")
    suspend fun getAll(): List<NotificationCacheEntity>

    @Query("DELETE FROM notification_cache")
    suspend fun clear()

    @Query("DELETE FROM notification_cache WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notification_cache WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE notification_cache SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE notification_cache SET isRead = 1")
    suspend fun markAllRead()

    @Transaction
    suspend fun replaceAll(notifications: List<NotificationCacheEntity>) {
        clear()
        insertAll(notifications)
    }
}

@Dao
interface LinkPreviewCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(previews: List<LinkPreviewCacheEntity>)

    @Query("SELECT * FROM link_preview_cache WHERE url = :url LIMIT 1")
    suspend fun get(url: String): LinkPreviewCacheEntity?

    @Query("SELECT * FROM link_preview_cache WHERE url IN (:urls)")
    suspend fun getMultiple(urls: List<String>): List<LinkPreviewCacheEntity>
}

@Dao
interface TrendingHashtagCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(hashtags: List<TrendingHashtagCacheEntity>)

    @Query("SELECT * FROM trending_hashtag_cache ORDER BY count DESC")
    suspend fun getAll(): List<TrendingHashtagCacheEntity>

    @Query("DELETE FROM trending_hashtag_cache")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(hashtags: List<TrendingHashtagCacheEntity>) {
        clear()
        insertAll(hashtags)
    }
}

@Dao
interface CacheMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: CacheMetaEntity)

    @Query("SELECT timestamp FROM cache_meta WHERE `key` = :key LIMIT 1")
    suspend fun getTimestamp(key: String): Long?
}