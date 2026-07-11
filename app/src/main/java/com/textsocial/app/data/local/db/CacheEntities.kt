package com.textsocial.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entitas-entitas Room untuk menyimpan cache data di device.
 *
 * Semua entity punya kolom `fetchedAt` (epoch millis) yang dipakai untuk
 * menentukan apakah data masih "segar" (di bawah TTL) atau harus di-refresh
 * ulang ke server. Lihat [com.textsocial.app.data.local.CacheConfig] untuk nilai TTL.
 */

@Entity(tableName = "post_cache")
data class PostCacheEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val username: String,
    val displayName: String?,
    val userAvatarColor: String,
    val text: String,
    val createdAt: String,
    val likesCount: Int,
    val commentsCount: Int,
    val isLiked: Boolean,
    val isVerified: Boolean,
    val userAvatarUrl: String?,
    val linkPreviewUrl: String?,
    val linkPreviewTitle: String?,
    val linkPreviewDescription: String?,
    val linkPreviewImageUrl: String?,
    val linkPreviewSiteName: String?,
    val fetchedAt: Long
)

@Entity(tableName = "profile_cache")
data class ProfileCacheEntity(
    @PrimaryKey val userId: String,
    val username: String,
    val email: String,
    val displayName: String?,
    val bio: String?,
    val isPrivate: Boolean,
    val followersCount: Int,
    val followingCount: Int,
    val postsCount: Int,
    val isVerified: Boolean,
    val avatarUrl: String?,
    val hideFollowingList: Boolean,
    val fetchedAt: Long,
    // waktu terpisah untuk followers/following count karena bisa berubah
    // lebih cepat (like/follow) dibanding data profil lain
    val followCountsFetchedAt: Long = 0L
)

@Entity(tableName = "story_cache")
data class StoryCacheEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val username: String,
    val text: String,
    val createdAt: String,
    val expiresAt: String,
    val avatarColor: String,
    // list username disimpan dipisah koma
    val viewsCsv: String,
    // format per viewer: username::avatarUrl::avatarColor::isVerified, dipisah ";;"
    val viewersEncoded: String,
    val backgroundColor: String,
    val textColor: String,
    val fontFamily: String,
    val isVerified: Boolean,
    val avatarUrl: String?,
    val fetchedAt: Long
)

@Entity(tableName = "conversation_cache")
data class ConversationCacheEntity(
    @PrimaryKey val id: String,
    val otherUserId: String,
    val otherUsername: String,
    val otherAvatarColor: String,
    val lastMessage: String?,
    val lastMessageTime: String?,
    val unreadCount: Int,
    val otherIsVerified: Boolean,
    val otherAvatarUrl: String?,
    val fetchedAt: Long
)

@Entity(tableName = "message_cache")
data class MessageCacheEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val createdAt: String,
    val isRead: Boolean,
    val isDeleted: Boolean,
    val fetchedAt: Long
)

@Entity(tableName = "notification_cache")
data class NotificationCacheEntity(
    @PrimaryKey val id: String,
    val type: String,
    val senderId: String,
    val senderUsername: String,
    val senderAvatarColor: String,
    val postId: String?,
    val commentId: String?,
    val text: String,
    val createdAt: String,
    val isRead: Boolean,
    val senderIsVerified: Boolean,
    val senderAvatarUrl: String?,
    val fetchedAt: Long
)

@Entity(tableName = "link_preview_cache")
data class LinkPreviewCacheEntity(
    @PrimaryKey val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val siteName: String?,
    val fetchedAt: Long
)

@Entity(tableName = "trending_hashtag_cache")
data class TrendingHashtagCacheEntity(
    @PrimaryKey val tag: String,
    val count: Int,
    val fetchedAt: Long
)

/**
 * Menyimpan cap waktu "kapan terakhir daftar ini di-refresh dari server",
 * dipakai untuk endpoint yang berupa list (posts, stories, conversations, dst)
 * yang tidak punya key per-item alami untuk dicek satu-satu.
 */
@Entity(tableName = "cache_meta")
data class CacheMetaEntity(
    @PrimaryKey val key: String,
    val timestamp: Long
)