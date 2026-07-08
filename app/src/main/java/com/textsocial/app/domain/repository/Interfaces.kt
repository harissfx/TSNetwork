package com.textsocial.app.domain.repository

import com.textsocial.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(usernameOrEmail: String, password: String): Result<User>
    suspend fun register(username: String, email: String, password: String): Result<User>
    suspend fun forgotPassword(email: String): Result<Unit>
    suspend fun logout(): Result<Unit>
    suspend fun refreshToken(): Result<Unit>
    fun getCurrentUserFlow(): Flow<User?>
    suspend fun getSessionUser(): User?
}

interface PostRepository {
    suspend fun getPosts(hashtag: String? = null): Result<List<Post>>
    suspend fun createPost(text: String): Result<Unit>
    suspend fun deletePost(postId: String): Result<Unit>
    suspend fun likePost(postId: String): Result<Unit>
    suspend fun unlikePost(postId: String): Result<Unit>
    suspend fun getComments(postId: String): Result<List<Comment>>
    suspend fun createComment(postId: String, text: String, parentId: String? = null): Result<Unit>
    suspend fun deleteComment(commentId: String): Result<Unit>
    suspend fun likeComment(commentId: String): Result<Unit>
    suspend fun unlikeComment(commentId: String): Result<Unit>
}

interface StoryRepository {
    suspend fun getStories(): Result<List<Story>>
    suspend fun createStory(
        text: String,
        backgroundColor: String = "#000000",
        textColor: String = "#FFFFFF",
        fontFamily: String = "default"
    ): Result<Unit>
    suspend fun getStoryViews(storyId: String): Result<List<String>>
    suspend fun recordStoryView(storyId: String): Result<Unit>
    suspend fun deleteStory(storyId: String): Result<Unit>
}

interface MessageRepository {
    suspend fun getConversations(): Result<List<Conversation>>
    suspend fun getMessages(otherUserId: String): Result<List<Message>>
    suspend fun sendMessage(otherUserId: String, text: String): Result<Unit>
    suspend fun markMessagesAsRead(otherUserId: String): Result<Unit>
    suspend fun deleteMessageForEveryone(otherUserId: String, messageId: String): Result<Unit>
    fun observeMessages(otherUserId: String): Flow<List<Message>>
}

interface UserRepository {
    suspend fun getProfile(userId: String): Result<User>
    suspend fun getProfileByUsername(username: String): Result<User>
    suspend fun updateProfile(displayName: String?, bio: String?, isPrivate: Boolean): Result<Unit>
    suspend fun uploadAvatar(imageBytes: ByteArray): Result<String>
    suspend fun followUser(targetUserId: String): Result<Unit>
    suspend fun unfollowUser(targetUserId: String): Result<Unit>
    suspend fun isFollowing(targetUserId: String): Result<Boolean>
    suspend fun isFollowedBy(targetUserId: String): Result<Boolean>
    suspend fun getFollowCounts(userId: String): Result<Pair<Int, Int>>
    suspend fun getFollowingUsers(): Result<List<User>>
    suspend fun getFollowersOf(userId: String): Result<List<User>>
    suspend fun getFollowingOf(userId: String): Result<List<User>>
    suspend fun updateFollowListPrivacy(hideFollowingList: Boolean): Result<Unit>
    suspend fun updateUsername(newUsername: String): Result<Unit>
    suspend fun searchUsers(query: String): Result<List<User>>
    suspend fun getNotifications(): Result<List<Notification>>
    suspend fun markNotificationAsRead(notificationId: String): Result<Unit>
    suspend fun markNotificationsAsRead(): Result<Unit>
    suspend fun deleteNotification(notificationId: String): Result<Unit>
    suspend fun deleteNotifications(notificationIds: List<String>): Result<Unit>
    suspend fun getTrendingHashtags(): Result<List<Pair<String, Int>>>
}