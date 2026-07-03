package com.textsocial.app.domain.model

import java.io.Serializable

data class User(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String?,
    val bio: String?,
    val avatarColor: String, // Hex string like "#FF5722"
    val isPrivate: Boolean = false,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0
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
    val isLiked: Boolean = false
) : Serializable

data class Story(
    val id: String,
    val userId: String,
    val username: String,
    val text: String,
    val createdAt: String,
    val expiresAt: String,
    val avatarColor: String,
    val views: List<String> = emptyList() // List of usernames who viewed
) : Serializable

data class Comment(
    val id: String,
    val postId: String,
    val userId: String,
    val username: String,
    val text: String,
    val createdAt: String,
    val avatarColor: String
) : Serializable

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val createdAt: String,
    val isRead: Boolean
) : Serializable

data class Conversation(
    val id: String,
    val otherUserId: String,
    val otherUsername: String,
    val otherAvatarColor: String,
    val lastMessage: String?,
    val lastMessageTime: String?
) : Serializable

data class Notification(
    val id: String,
    val type: String, // "LIKE", "COMMENT", "FOLLOW", "MENTION"
    val senderUsername: String,
    val senderAvatarColor: String,
    val text: String,
    val createdAt: String
) : Serializable
