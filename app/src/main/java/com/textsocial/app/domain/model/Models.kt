package com.textsocial.app.domain.model

import java.io.Serializable

data class User(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String?,
    val bio: String?,
    val avatarColor: String,
    val isPrivate: Boolean = false,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0,
    val isVerified: Boolean = false,
    val avatarUrl: String? = null,
    val hideFollowingList: Boolean = false
) : Serializable

data class Post(
    val id: String,
    val userId: String,
    val username: String,
    val displayName: String?,
    val userAvatarColor: String,
    val text: String,
    val createdAt: String,
    val likesCount: Int,
    val commentsCount: Int,
    val isLiked: Boolean = false,
    val isVerified: Boolean = false,
    val userAvatarUrl: String? = null,
    val linkPreview: LinkPreview? = null
) : Serializable

// Metadata Open Graph (judul/deskripsi/gambar) dari link pertama yang ada di teks post,
// diambil dari cache tabel link_previews (lihat PostRepositoryImpl.attachLinkPreviews).
data class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val siteName: String?
) : Serializable

data class Story(
    val id: String,
    val userId: String,
    val username: String,
    val text: String,
    val createdAt: String,
    val expiresAt: String,
    val avatarColor: String,
    val views: List<String> = emptyList(),
    val backgroundColor: String = "#000000",
    val textColor: String = "#FFFFFF",
    val fontFamily: String = "default",
    val isVerified: Boolean = false,
    val avatarUrl: String? = null
) : Serializable

data class Comment(
    val id: String,
    val postId: String,
    val userId: String,
    val username: String,
    val displayName: String? = null,
    val text: String,
    val createdAt: String,
    val avatarColor: String,
    val parentId: String? = null,
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val isVerified: Boolean = false,
    val avatarUrl: String? = null
) : Serializable

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val createdAt: String,
    val isRead: Boolean,
    val isDeleted: Boolean = false,
    val isPending: Boolean = false,
    val isFailed: Boolean = false
) : Serializable

data class Conversation(
    val id: String,
    val otherUserId: String,
    val otherUsername: String,
    val otherAvatarColor: String,
    val lastMessage: String?,
    val lastMessageTime: String?,
    val unreadCount: Int = 0,
    val otherIsVerified: Boolean = false,
    val otherAvatarUrl: String? = null
) : Serializable
data class FollowListEntry(
    val user: User,
    val isFollowedByMe: Boolean,
    val followsMe: Boolean
)

data class Notification(
    val id: String,
    val type: String,
    val senderId: String,
    val senderUsername: String,
    val senderAvatarColor: String,
    val postId: String?,
    val commentId: String?,
    val text: String,
    val createdAt: String,
    val isRead: Boolean = false,
    val senderIsVerified: Boolean = false,
    val senderAvatarUrl: String? = null
) : Serializable