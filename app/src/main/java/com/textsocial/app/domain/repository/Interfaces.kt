package com.textsocial.app.domain.repository

import com.textsocial.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(usernameOrEmail: String, password: String): Result<User>
    suspend fun register(username: String, email: String, password: String): Result<User>
    suspend fun forgotPassword(email: String): Result<Unit>
    suspend fun verifyCurrentPassword(currentPassword: String): Result<Unit>
    suspend fun updatePassword(newPassword: String): Result<Unit>
    suspend fun logout(): Result<Unit>
    suspend fun refreshToken(): Result<Unit>
    fun getCurrentUserFlow(): Flow<User?>
    suspend fun getSessionUser(): User?
}

/**
 * Satu halaman hasil paginasi post: isi postnya + penanda apakah masih ada halaman
 * berikutnya (dipakai UI untuk tahu kapan berhenti memicu "load more").
 */
data class PostsPage(
    val posts: List<Post>,
    val hasMore: Boolean,
    /** Total baris yang match filter di server (dari header Content-Range), kalau tersedia.
     *  Dipakai untuk mis. angka "jumlah post" di profil tanpa perlu fetch semuanya. */
    val totalCount: Int? = null
)

/**
 * Satu halaman hasil paginasi komentar (pola sama dengan PostsPage): isi komentarnya + apakah
 * masih ada komentar yang lebih baru yang belum dimuat, dipakai UI utk tombol "load more".
 */
data class CommentsPage(
    val comments: List<Comment>,
    val hasMore: Boolean
)

interface PostRepository {
    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }

    /**
     * @param before cursor keyset: created_at dari post terakhir di halaman sebelumnya
     *   (null untuk halaman pertama / refresh). Cache lokal (TTL) HANYA berlaku untuk
     *   halaman pertama; halaman berikutnya selalu ambil langsung ke server.
     */
    suspend fun getPosts(
        hashtag: String? = null,
        before: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Result<PostsPage>

    /** Ambil satu post by id langsung dari server (tanpa narik seluruh tabel). */
    suspend fun getPostById(postId: String): Result<Post?>

    /** Post milik satu user, difilter & dipaginasi di server (dipakai halaman profil). */
    suspend fun getPostsByUser(
        userId: String,
        before: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Result<PostsPage>

    suspend fun createPost(text: String): Result<Unit>
    suspend fun deletePost(postId: String): Result<Unit>
    suspend fun likePost(postId: String): Result<Unit>
    suspend fun unlikePost(postId: String): Result<Unit>
    /**
     * @param after cursor keyset: created_at dari komentar TERAKHIR (paling baru) di halaman
     *   sebelumnya (null untuk halaman pertama). Dipakai UI "load more comments" supaya post
     *   viral dengan >200 komentar tidak terpotong.
     */
    suspend fun getComments(
        postId: String,
        after: String? = null,
        pageSize: Int = 200
    ): Result<CommentsPage>
    suspend fun createComment(postId: String, text: String, parentId: String? = null): Result<Unit>
    suspend fun deleteComment(commentId: String): Result<Unit>
    suspend fun likeComment(commentId: String): Result<Unit>
    suspend fun unlikeComment(commentId: String): Result<Unit>
    suspend fun getLinkPreview(url: String): Result<LinkPreview?>
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
    suspend fun hideConversation(otherUserId: String): Result<Unit>
    suspend fun hideConversations(otherUserIds: List<String>): Result<Unit>
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
    suspend fun registerDeviceToken(fcmToken: String): Result<Unit>
    suspend fun unregisterDeviceToken(fcmToken: String): Result<Unit>
    suspend fun getTrendingHashtags(): Result<List<Pair<String, Int>>>
}

interface AppUpdateRepository {
    /** Null berarti tidak ada update (versi app sudah paling baru atau lebih baru dari server). */
    suspend fun checkForUpdate(): Result<AppUpdateInfo?>

    /** Versi terakhir yang di-skip user lewat tombol "Nanti", 0 kalau belum pernah di-skip. */
    fun getDismissedVersionCode(): Int

    fun dismissVersion(versionCode: Int)
}