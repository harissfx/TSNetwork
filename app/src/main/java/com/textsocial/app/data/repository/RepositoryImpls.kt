package com.textsocial.app.data.repository

import android.content.Context
import com.textsocial.app.R
import com.textsocial.app.data.api.SupabaseApiService
import com.textsocial.app.data.api.SupabaseClient
import com.textsocial.app.data.local.CacheConfig
import com.textsocial.app.data.local.EncryptedPreferencesManager
import com.textsocial.app.data.local.db.AppDatabase
import com.textsocial.app.data.local.db.CacheMetaEntity
import com.textsocial.app.data.local.db.ConversationCacheEntity
import com.textsocial.app.data.local.db.LinkPreviewCacheEntity
import com.textsocial.app.data.local.db.MessageCacheEntity
import com.textsocial.app.data.local.db.NotificationCacheEntity
import com.textsocial.app.data.local.db.PostCacheEntity
import com.textsocial.app.data.local.db.ProfileCacheEntity
import com.textsocial.app.data.local.db.StoryCacheEntity
import com.textsocial.app.data.local.db.TrendingHashtagCacheEntity
import com.textsocial.app.data.model.*
import com.textsocial.app.domain.model.*
import com.textsocial.app.domain.repository.*
import com.textsocial.app.util.LocaleManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SupabaseAuthException(val code: Int, val errorMsg: String) : Exception("Error $code: $errorMsg")

// Jendela post terbaru yang dipindai untuk hitung trending hashtag (dipakai di
// UserRepositoryImpl.getTrendingHashtags). Dulu tanpa batas (whole table); sekarang
// dibatasi supaya beban query & payload tidak terus membesar seiring jumlah post
// total di platform bertambah. Taruh di level file (bukan di dalam salah satu class
// repository) karena dipakai lintas class.
private const val TRENDING_HASHTAG_SCAN_WINDOW = 500

/** Parse header Postgrest "Content-Range: 0-19/153" -> 153. Null kalau header tidak ada
 *  atau totalnya "*" (Postgrest kirim ini saat count belum/tidak dihitung). Top-level supaya
 *  dipakai bareng oleh beberapa repository (posts, follows, dst) yang butuh count=exact. */
private fun parseContentRangeTotal(headerValue: String?): Int? {
    val total = headerValue?.substringAfter('/', missingDelimiterValue = "")
    return total?.takeIf { it.isNotBlank() && it != "*" }?.toIntOrNull()
}

private fun getSupabaseError(context: Context, code: Int, errorBody: String?): SupabaseAuthException {
    if (errorBody.isNullOrBlank()) {
        return SupabaseAuthException(code, LocaleManager.applyLocale(context).getString(R.string.error_api_generic))
    }
    return try {
        val obj = JSONObject(errorBody)
        val msg = obj.optString("msg", obj.optString("error_description", obj.optString("error", errorBody)))
        SupabaseAuthException(code, msg)
    } catch (e: Exception) {
        SupabaseAuthException(code, errorBody)
    }
}

class AuthRepositoryImpl(
    private val apiService: SupabaseApiService,
    private val prefs: EncryptedPreferencesManager,
    private val context: Context,
    private val db: AppDatabase? = null
) : AuthRepository {

    private val _currentUser = MutableStateFlow<User?>(null)

    init {
        val storedUserId = prefs.getUserId()
        val storedUsername = prefs.getUsername()
        if (storedUserId != null && storedUsername != null) {
            _currentUser.value = User(
                id = storedUserId,
                username = storedUsername,
                email = "",
                displayName = storedUsername.replaceFirstChar { it.uppercase() },
                bio = LocaleManager.applyLocale(context).getString(R.string.bio_default_welcome),
                avatarColor = prefs.getUserAvatarColor(),
                followersCount = 0,
                followingCount = 0,
                postsCount = 0
            )
        }
    }

    override suspend fun login(usernameOrEmail: String, password: String): Result<User> {
        if (usernameOrEmail.isBlank() || password.isBlank()) {
            return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_login_fields_empty)))
        }

        return try {
            val email = if (usernameOrEmail.contains("@")) {
                usernameOrEmail
            } else {
                val profileResponse = apiService.getProfilesByUsername("eq.$usernameOrEmail")
                if (profileResponse.isSuccessful) {
                    profileResponse.body()?.firstOrNull()?.email
                        ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_username_not_found)))
                } else {
                    return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_resolve_email_failed)))
                }
            }

            val response = apiService.signIn(SupabaseSignInRequest(email = email, password = password))
            if (response.isSuccessful && response.body() != null) {
                val authData = response.body()!!
                prefs.saveToken(authData.access_token)
                prefs.saveRefreshToken(authData.refresh_token)

                val profileResponse = apiService.getProfile("eq.${authData.user.id}")
                var userProfile = profileResponse.body()?.firstOrNull()

                if (userProfile == null) {
                    val cleanUsername = email.split("@").first().lowercase()
                    userProfile = ProfileDto(
                        id = authData.user.id,
                        username = cleanUsername,
                        email = email,
                        display_name = cleanUsername.replaceFirstChar { it.uppercase() },
                        bio = LocaleManager.applyLocale(context).getString(R.string.bio_just_joined_wave)
                    )
                    apiService.createProfile(userProfile)
                }

                prefs.saveUserId(userProfile.id)
                prefs.saveUsername(userProfile.username)
                prefs.saveUserAvatarColor(getAvatarColor(userProfile.username))

                val user = User(
                    id = userProfile.id,
                    username = userProfile.username,
                    email = userProfile.email ?: "",
                    displayName = userProfile.display_name ?: userProfile.username.replaceFirstChar { it.uppercase() },
                    bio = userProfile.bio ?: LocaleManager.applyLocale(context).getString(R.string.bio_no_bio_yet),
                    avatarColor = getAvatarColor(userProfile.username),
                    isPrivate = userProfile.is_private,
                    isVerified = userProfile.is_verified,
                    avatarUrl = userProfile.avatar_url,
                    hideFollowingList = userProfile.hide_following_list
                )
                _currentUser.value = user
                Result.success(user)
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(getSupabaseError(context, response.code(), errorBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(username: String, email: String, password: String): Result<User> {
        if (username.length < 3) return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_username_min_length)))
        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_username_invalid_chars)))
        }

        return try {
            val response = apiService.signUp(
                SupabaseSignUpRequestBody(
                    email = email,
                    password = password,
                    data = SignUpUserData(
                        username = username.lowercase(),
                        display_name = username.replaceFirstChar { it.uppercase() }
                    )
                )
            )
            if (response.isSuccessful) {
                val loginResponse = apiService.signIn(SupabaseSignInRequest(email = email, password = password))
                if (loginResponse.isSuccessful && loginResponse.body() != null) {
                    val authData = loginResponse.body()!!
                    prefs.saveToken(authData.access_token)
                    prefs.saveRefreshToken(authData.refresh_token)

                    val avatarColor = getAvatarColor(username)
                    val profile = ProfileDto(
                        id = authData.user.id,
                        username = username.lowercase(),
                        email = email,
                        display_name = username.replaceFirstChar { it.uppercase() },
                        bio = LocaleManager.applyLocale(context).getString(R.string.bio_just_joined_oss_wave),
                        is_private = false
                    )
                    apiService.createProfile(profile)

                    prefs.saveUserId(authData.user.id)
                    prefs.saveUsername(username)
                    prefs.saveUserAvatarColor(avatarColor)

                    val user = User(
                        id = authData.user.id,
                        username = username,
                        email = email,
                        displayName = profile.display_name,
                        bio = profile.bio,
                        avatarColor = avatarColor,
                        isPrivate = false,
                        isVerified = false
                    )
                    _currentUser.value = user
                    Result.success(user)
                } else {
                    val errorBody = loginResponse.errorBody()?.string()
                    Result.failure(getSupabaseError(context, loginResponse.code(), errorBody))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(getSupabaseError(context, response.code(), errorBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun forgotPassword(email: String): Result<Unit> {
        return try {
            val response = apiService.recoverPassword(com.textsocial.app.data.model.RecoverPasswordRequest(email = email))
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_password_reset_failed)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyCurrentPassword(currentPassword: String): Result<Unit> {
        if (currentPassword.isBlank()) {
            return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_login_fields_empty)))
        }
        return try {
            val userId = prefs.getUserId()
                ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_api_generic)))

            val profileResponse = apiService.getProfile("eq.$userId")
            val email = profileResponse.body()?.firstOrNull()?.email
                ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_api_generic)))

            // Sengaja TIDAK menyimpan access_token/refresh_token dari respons ini ke prefs --
            // ini cuma dipakai untuk mengecek apakah currentPassword benar, bukan untuk login
            // ulang, supaya sesi yang sedang aktif tidak ikut berubah/tertimpa.
            val response = apiService.signIn(SupabaseSignInRequest(email = email, password = currentPassword))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    Exception(LocaleManager.applyLocale(context).getString(R.string.error_current_password_incorrect))
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> {
        return try {
            val response = apiService.updatePassword(
                com.textsocial.app.data.model.UpdatePasswordRequest(password = newPassword)
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    getSupabaseError(context, response.code(), response.errorBody()?.string())
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        prefs.clear()
        _currentUser.value = null
        try {
            db?.clearAllCache()
        } catch (e: Exception) {
        }
        return Result.success(Unit)
    }

    override suspend fun refreshToken(): Result<Unit> {
        val refreshToken = prefs.getRefreshToken() ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_no_refresh_token)))
        return try {
            val response = apiService.refreshToken(com.textsocial.app.data.model.RefreshTokenRequest(refresh_token = refreshToken))
            if (response.isSuccessful && response.body() != null) {
                val authData = response.body()!!
                prefs.saveToken(authData.access_token)
                prefs.saveRefreshToken(authData.refresh_token)
                Result.success(Unit)
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_refresh_token_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getCurrentUserFlow(): Flow<User?> = _currentUser.asStateFlow()

    override suspend fun getSessionUser(): User? = _currentUser.value

    private fun getRandomColorHex(): String {
        val colors = listOf("#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#00BCD4", "#4CAF50", "#FFC107", "#FF9800", "#FF5722")
        return colors.random()
    }
}

internal fun getAvatarColor(username: String?): String {
    if (username.isNullOrEmpty()) return "#3F51B5"
    val colors = listOf("#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#00BCD4", "#4CAF50", "#FFC107", "#FF9800", "#FF5722")
    val index = Math.abs(username.hashCode()) % colors.size
    return colors[index]
}

class PostRepositoryImpl(
    private val apiService: SupabaseApiService,
    private val prefs: EncryptedPreferencesManager,
    private val context: Context,
    private val db: AppDatabase
) : PostRepository {

    private val localPosts = MutableStateFlow<List<Post>>(emptyList())

    private fun PostCacheEntity.toDomain() = Post(
        id = id,
        userId = userId,
        username = username,
        displayName = displayName,
        userAvatarColor = userAvatarColor,
        text = text,
        createdAt = createdAt,
        likesCount = likesCount,
        commentsCount = commentsCount,
        isLiked = isLiked,
        isVerified = isVerified,
        userAvatarUrl = userAvatarUrl,
        linkPreview = linkPreviewUrl?.let {
            LinkPreview(
                url = it,
                title = linkPreviewTitle,
                description = linkPreviewDescription,
                imageUrl = linkPreviewImageUrl,
                siteName = linkPreviewSiteName
            )
        }
    )

    private fun Post.toCacheEntity(fetchedAt: Long) = PostCacheEntity(
        id = id,
        userId = userId,
        username = username,
        displayName = displayName,
        userAvatarColor = userAvatarColor,
        text = text,
        createdAt = createdAt,
        likesCount = likesCount,
        commentsCount = commentsCount,
        isLiked = isLiked,
        isVerified = isVerified,
        userAvatarUrl = userAvatarUrl,
        linkPreviewUrl = linkPreview?.url,
        linkPreviewTitle = linkPreview?.title,
        linkPreviewDescription = linkPreview?.description,
        linkPreviewImageUrl = linkPreview?.imageUrl,
        linkPreviewSiteName = linkPreview?.siteName,
        fetchedAt = fetchedAt
    )

    /** Paksa fetch ulang dari server di pemanggilan getPosts berikutnya. */
    private suspend fun invalidatePostsCache() {
        db.cacheMetaDao().upsert(CacheMetaEntity(CacheConfig.META_KEY_POSTS, 0L))
    }

    /** Ubah PostDto hasil API jadi domain Post, sekalian isi status "isLiked" & link preview.
     *  Dipakai bersama oleh getPosts/getPostById/getPostsByUser biar logikanya konsisten. */
    private suspend fun dtosToPosts(dtos: List<PostDto>): List<Post> {
        if (dtos.isEmpty()) return emptyList()
        val myId = prefs.getUserId() ?: ""

        // Cuma minta status like utk post-post yang ADA di batch ini (mis. 20 post di satu
        // halaman feed), bukan seluruh riwayat like user. PostgREST "in.(...)" filter di
        // kolom post_id -- payload & waktu query jadi konstan terhadap jumlah post di batch,
        // bukan terhadap total like historis user (yang bisa ribuan pada user aktif lama).
        val postIdsInBatch = dtos.map { it.id }.distinct()
        val likedPostIds = if (postIdsInBatch.isEmpty()) {
            emptySet()
        } else {
            val postIdFilter = "in.(${postIdsInBatch.joinToString(",")})"
            val likesResponse = apiService.getLikes(postIdFilter = postIdFilter, userIdFilter = "eq.$myId")
            if (likesResponse.isSuccessful) {
                likesResponse.body()?.map { it.post_id }?.toSet() ?: emptySet()
            } else emptySet()
        }
        val urlsByPostId = dtos.associate { it.id to com.textsocial.app.util.LinkUtils.extractFirstUrl(it.content) }
        val previewsByUrl = fetchCachedLinkPreviews(urlsByPostId.values.filterNotNull().distinct())

        return dtos.map { dto ->
            val senderUsername = dto.users?.username ?: "user"
            Post(
                id = dto.id,
                userId = dto.user_id,
                username = senderUsername,
                displayName = dto.users?.display_name ?: senderUsername.replaceFirstChar { it.uppercase() },
                userAvatarColor = getAvatarColor(senderUsername),
                text = dto.content,
                createdAt = dto.created_at,
                likesCount = dto.like_count,
                commentsCount = dto.comment_count,
                isLiked = likedPostIds.contains(dto.id),
                isVerified = dto.users?.is_verified ?: false,
                userAvatarUrl = dto.users?.avatar_url,
                linkPreview = urlsByPostId[dto.id]?.let { previewsByUrl[it] }
            )
        }
    }

    override suspend fun getPosts(hashtag: String?, before: String?, pageSize: Int): Result<PostsPage> {
        // Cache TTL cuma dipakai untuk HALAMAN PERTAMA (before == null). Halaman ke-2 dst
        // selalu langsung ke server: isinya beda-beda tergantung cursor, jadi tidak worth
        // disimpan ke Room (dan supaya tidak menimpa cache halaman pertama yang sudah ada).
        if (before == null) {
            val cacheTimestamp = db.cacheMetaDao().getTimestamp(CacheConfig.META_KEY_POSTS) ?: 0L
            if (CacheConfig.isFresh(cacheTimestamp, CacheConfig.POSTS_TTL_MS)) {
                val cachedPosts = db.postCacheDao().getAll().map { it.toDomain() }
                if (cachedPosts.isNotEmpty()) {
                    val filtered = if (hashtag != null) {
                        cachedPosts.filter { it.text.contains(hashtag, ignoreCase = true) }
                    } else cachedPosts
                    localPosts.value = filtered
                    // Cache lama tersimpan tanpa info cursor persis, jadi kita anggap ada
                    // halaman berikutnya -- aman, scroll berikutnya tetap langsung ke server.
                    return Result.success(PostsPage(filtered, hasMore = cachedPosts.size >= pageSize))
                }
            }
        }

        return try {
            val response = apiService.getPosts(
                createdBefore = before?.let { "lt.$it" },
                limit = pageSize
            )
            if (response.isSuccessful && response.body() != null) {
                val posts = dtosToPosts(response.body()!!)

                if (before == null) {
                    val now = System.currentTimeMillis()
                    db.postCacheDao().replaceAll(posts.map { it.toCacheEntity(now) })
                    db.cacheMetaDao().upsert(CacheMetaEntity(CacheConfig.META_KEY_POSTS, now))
                    localPosts.value = posts
                }

                val filtered = if (hashtag != null) {
                    posts.filter { it.text.contains(hashtag, ignoreCase = true) }
                } else posts

                Result.success(PostsPage(filtered, hasMore = posts.size >= pageSize))
            } else if (before == null) {
                // Server gagal/timeout di halaman pertama -> fallback ke cache lama daripada kosong total.
                val cachedPosts = db.postCacheDao().getAll().map { it.toDomain() }
                if (cachedPosts.isNotEmpty()) {
                    val filtered = if (hashtag != null) cachedPosts.filter { it.text.contains(hashtag, ignoreCase = true) } else cachedPosts
                    Result.success(PostsPage(filtered, hasMore = true))
                } else {
                    Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_fetch_posts_failed, response.code().toString())))
                }
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_fetch_posts_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            if (before == null) {
                val cachedPosts = db.postCacheDao().getAll().map { it.toDomain() }
                if (cachedPosts.isNotEmpty()) {
                    val filtered = if (hashtag != null) cachedPosts.filter { it.text.contains(hashtag, ignoreCase = true) } else cachedPosts
                    return Result.success(PostsPage(filtered, hasMore = true))
                }
            }
            Result.failure(e)
        }
    }

    override suspend fun getPostById(postId: String): Result<Post?> {
        return try {
            val response = apiService.getPostById(idFilter = "eq.$postId")
            if (response.isSuccessful) {
                Result.success(dtosToPosts(response.body().orEmpty()).firstOrNull())
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_fetch_posts_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPostsByUser(userId: String, before: String?, pageSize: Int): Result<PostsPage> {
        return try {
            val response = apiService.getPostsByUser(
                userIdFilter = "eq.$userId",
                createdBefore = before?.let { "lt.$it" },
                limit = pageSize
            )
            if (response.isSuccessful) {
                val posts = dtosToPosts(response.body().orEmpty())
                val totalCount = parseContentRangeTotal(response.headers()["Content-Range"])
                Result.success(PostsPage(posts, hasMore = posts.size >= pageSize, totalCount = totalCount))
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_fetch_posts_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createPost(text: String): Result<Unit> {
        if (text.length > 3000) {
            return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_post_too_long)))
        }

        return try {
            val myId = prefs.getUserId() ?: ""
            val body = com.textsocial.app.data.model.CreatePostRequest(
                user_id = myId,
                content = text
            )
            val response = apiService.createPost(body)
            if (!response.isSuccessful) {
                return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_publish_post_failed, response.code().toString())))
            }

            val createdPostId = response.body()?.firstOrNull()?.id
            if (createdPostId != null) {
                val mentionedUsernames = Regex("@([a-zA-Z0-9_]+)").findAll(text)
                    .map { it.groupValues[1] }
                    .distinct()
                    .toList()
                for (username in mentionedUsernames) {
                    try {
                        val profileResp = apiService.getProfilesByUsername("eq.$username")
                        val mentionedUser = profileResp.body()?.firstOrNull()
                        if (mentionedUser != null && mentionedUser.id != myId) {
                            apiService.createNotification(
                                com.textsocial.app.data.model.CreateNotificationRequest(
                                    recipient_id = mentionedUser.id,
                                    sender_id = myId,
                                    type = "mention",
                                    post_id = createdPostId
                                )
                            )
                        }
                    } catch (e: Exception) {
                    }
                }
            }

            invalidatePostsCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePost(postId: String): Result<Unit> {
        return try {
            val response = apiService.deletePost("eq.$postId")
            if (response.isSuccessful) {
                invalidatePostsCache()
                Result.success(Unit)
            } else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_delete_post_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun likePost(postId: String): Result<Unit> {
        return try {
            val body = com.textsocial.app.data.model.LikePostRequest(
                post_id = postId,
                user_id = (prefs.getUserId() ?: "")
            )
            val response = apiService.likePost(body)
            if (response.isSuccessful) {
                invalidatePostsCache()
                Result.success(Unit)
            } else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_like_post_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unlikePost(postId: String): Result<Unit> {
        return try {
            val myId = prefs.getUserId() ?: ""
            val response = apiService.unlikePost(postIdFilter = "eq.$postId", userIdFilter = "eq.$myId")
            if (response.isSuccessful) {
                invalidatePostsCache()
                Result.success(Unit)
            } else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_unlike_post_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getComments(postId: String, after: String?, pageSize: Int): Result<CommentsPage> {
        return try {
            val response = apiService.getComments(
                postIdFilter = "eq.$postId",
                createdAfter = after?.let { "gt.$it" },
                limit = pageSize
            )
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!
                val myId = prefs.getUserId() ?: ""
                val commentIds = dtos.map { it.id }
                val likesByComment: Map<String, List<CommentLikeDto>> = if (commentIds.isNotEmpty()) {
                    val idsFilter = "in.(${commentIds.joinToString(",")})"
                    val likesResponse = apiService.getCommentLikes(idsFilter)
                    if (likesResponse.isSuccessful) {
                        likesResponse.body()?.groupBy { it.comment_id } ?: emptyMap()
                    } else emptyMap()
                } else emptyMap()

                val comments = dtos.map { dto ->
                    val commenterUsername = dto.users?.username ?: "user"
                    val likesForComment = likesByComment[dto.id] ?: emptyList()
                    Comment(
                        id = dto.id,
                        postId = dto.post_id,
                        userId = dto.user_id,
                        username = commenterUsername,
                        displayName = dto.users?.display_name,
                        text = dto.content,
                        createdAt = dto.created_at,
                        avatarColor = getAvatarColor(commenterUsername),
                        parentId = dto.parent_id,
                        likesCount = likesForComment.size,
                        isLiked = likesForComment.any { it.user_id == myId },
                        isVerified = dto.users?.is_verified ?: false,
                        avatarUrl = dto.users?.avatar_url
                    )
                }
                Result.success(CommentsPage(comments, hasMore = comments.size >= pageSize))
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_fetch_comments_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createComment(postId: String, text: String, parentId: String?): Result<Unit> {
        return try {
            val myId = prefs.getUserId() ?: ""
            val body = com.textsocial.app.data.model.CreateCommentRequest(
                post_id = postId,
                user_id = myId,
                content = text,
                parent_id = parentId
            )
            val response = apiService.createComment(body)
            if (!response.isSuccessful) {
                return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_create_comment_failed, response.code().toString())))
            }

            val createdCommentId = response.body()?.firstOrNull()?.id
            if (createdCommentId != null) {
                val mentionedUsernames = Regex("@([a-zA-Z0-9_]+)").findAll(text)
                    .map { it.groupValues[1] }
                    .distinct()
                    .toList()
                for (username in mentionedUsernames) {
                    try {
                        val profileResp = apiService.getProfilesByUsername("eq.$username")
                        val mentionedUser = profileResp.body()?.firstOrNull()
                        if (mentionedUser != null && mentionedUser.id != myId) {
                            apiService.createNotification(
                                com.textsocial.app.data.model.CreateNotificationRequest(
                                    recipient_id = mentionedUser.id,
                                    sender_id = myId,
                                    type = "mention",
                                    post_id = postId,
                                    comment_id = createdCommentId
                                )
                            )
                        }
                    } catch (e: Exception) {
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteComment(commentId: String): Result<Unit> {
        return try {
            val response = apiService.deleteComment("eq.$commentId")
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_delete_comment_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun likeComment(commentId: String): Result<Unit> {
        return try {
            val body = com.textsocial.app.data.model.CreateCommentLikeRequest(
                comment_id = commentId,
                user_id = (prefs.getUserId() ?: "")
            )
            val response = apiService.likeComment(body)
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_like_comment_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unlikeComment(commentId: String): Result<Unit> {
        return try {
            val myId = prefs.getUserId() ?: ""
            val response = apiService.unlikeComment(commentIdFilter = "eq.$commentId", userIdFilter = "eq.$myId")
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_unlike_comment_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLinkPreview(url: String): Result<LinkPreview?> {
        // Link preview jarang sekali berubah -> cek cache device dulu (TTL 24 jam)
        // sebelum minta ke edge function link-preview di server.
        val cached = db.linkPreviewCacheDao().get(url)
        if (cached != null && CacheConfig.isFresh(cached.fetchedAt, CacheConfig.LINK_PREVIEW_TTL_MS)) {
            return Result.success(cached.toDomain())
        }

        return try {
            val response = apiService.fetchLinkPreview(com.textsocial.app.data.model.LinkPreviewRequest(url))
            if (!response.isSuccessful) {
                // Gagal fetch baru -> tetap pakai cache lama kalau ada, daripada gagal total.
                return if (cached != null) Result.success(cached.toDomain())
                else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_fetch_link_preview_failed, response.code().toString())))
            }
            val dto = response.body()
            val preview = dto?.takeIf { !it.fetch_failed }?.toDomain()
            if (preview != null) {
                db.linkPreviewCacheDao().upsertAll(listOf(preview.toCacheEntity(System.currentTimeMillis())))
            }
            Result.success(preview)
        } catch (e: Exception) {
            if (cached != null) Result.success(cached.toDomain()) else Result.failure(e)
        }
    }

    private fun LinkPreviewCacheEntity.toDomain() = LinkPreview(
        url = url,
        title = title,
        description = description,
        imageUrl = imageUrl,
        siteName = siteName
    )

    private fun LinkPreview.toCacheEntity(fetchedAt: Long) = LinkPreviewCacheEntity(
        url = url,
        title = title,
        description = description,
        imageUrl = imageUrl,
        siteName = siteName,
        fetchedAt = fetchedAt
    )

    private suspend fun fetchCachedLinkPreviews(urls: List<String>): Map<String, LinkPreview> {
        if (urls.isEmpty()) return emptyMap()

        // 1) Ambil dulu yang sudah ada & masih segar di cache device.
        val now = System.currentTimeMillis()
        val localHits = db.linkPreviewCacheDao().getMultiple(urls)
            .filter { CacheConfig.isFresh(it.fetchedAt, CacheConfig.LINK_PREVIEW_TTL_MS) }
            .associate { it.url to it.toDomain() }

        val remainingUrls = urls.filterNot { localHits.containsKey(it) }
        if (remainingUrls.isEmpty()) return localHits

        // 2) Sisanya baru diminta ke server (endpoint cache preview Supabase).
        val remoteHits = try {
            val filter = "in.(${remainingUrls.joinToString(",") { "\"$it\"" }})"
            val response = apiService.getLinkPreviewsCached(urlInFilter = filter)
            if (!response.isSuccessful) emptyMap()
            else response.body()
                ?.filterNot { it.fetch_failed }
                ?.associate { it.url to it.toDomain() }
                ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

        if (remoteHits.isNotEmpty()) {
            db.linkPreviewCacheDao().upsertAll(remoteHits.values.map { it.toCacheEntity(now) })
        }

        return localHits + remoteHits
    }

    private fun com.textsocial.app.data.model.LinkPreviewDto.toDomain() = LinkPreview(
        url = url,
        title = title,
        description = description,
        imageUrl = image_url,
        siteName = site_name
    )
}

class StoryRepositoryImpl(
    private val apiService: SupabaseApiService,
    private val prefs: EncryptedPreferencesManager,
    private val context: Context,
    private val db: AppDatabase
) : StoryRepository {

    private fun StoryCacheEntity.toDomain() = Story(
        id = id,
        userId = userId,
        username = username,
        text = text,
        createdAt = createdAt,
        expiresAt = expiresAt,
        avatarColor = avatarColor,
        views = if (viewsCsv.isBlank()) emptyList() else viewsCsv.split(","),
        viewers = if (viewersEncoded.isBlank()) emptyList() else viewersEncoded.split(";;").mapNotNull { entry ->
            val parts = entry.split("::")
            if (parts.size < 4) null else StoryViewer(
                username = parts[0],
                avatarUrl = parts[1].ifEmpty { null },
                avatarColor = parts[2],
                isVerified = parts[3].toBoolean()
            )
        },
        backgroundColor = backgroundColor,
        textColor = textColor,
        fontFamily = fontFamily,
        isVerified = isVerified,
        avatarUrl = avatarUrl
    )

    private fun Story.toCacheEntity(fetchedAt: Long) = StoryCacheEntity(
        id = id,
        userId = userId,
        username = username,
        text = text,
        createdAt = createdAt,
        expiresAt = expiresAt,
        avatarColor = avatarColor,
        viewsCsv = views.joinToString(","),
        viewersEncoded = viewers.joinToString(";;") { "${it.username}::${it.avatarUrl ?: ""}::${it.avatarColor}::${it.isVerified}" },
        backgroundColor = backgroundColor,
        textColor = textColor,
        fontFamily = fontFamily,
        isVerified = isVerified,
        avatarUrl = avatarUrl,
        fetchedAt = fetchedAt
    )

    private suspend fun invalidateStoriesCache() {
        db.cacheMetaDao().upsert(CacheMetaEntity(CacheConfig.META_KEY_STORIES, 0L))
    }

    override suspend fun getStories(): Result<List<Story>> {
        val cacheTimestamp = db.cacheMetaDao().getTimestamp(CacheConfig.META_KEY_STORIES) ?: 0L
        if (CacheConfig.isFresh(cacheTimestamp, CacheConfig.STORIES_TTL_MS)) {
            val cachedStories = db.storyCacheDao().getAll().map { it.toDomain() }
            if (cachedStories.isNotEmpty()) return Result.success(cachedStories)
        }

        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val nowString = sdf.format(Date())
            val response = apiService.getStories("gt.$nowString")
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!

                val viewerUsernamesByStoryId = dtos.associate { dto ->
                    val viewsResponse = apiService.getStoryViews("eq.${dto.id}")
                    dto.id to (if (viewsResponse.isSuccessful) {
                        viewsResponse.body()?.map { it.viewer_username } ?: emptyList()
                    } else emptyList())
                }

                val allViewerUsernames = viewerUsernamesByStoryId.values.flatten().distinct()
                val viewerProfilesByUsername = fetchProfilesByUsernames(allViewerUsernames)

                val stories = dtos.map { dto ->
                    val viewerUsernames = viewerUsernamesByStoryId[dto.id] ?: emptyList()
                    val senderUsername = dto.users?.username ?: "user"
                    val filteredViewerUsernames = viewerUsernames.filter { it != senderUsername }
                    val filteredViewers = filteredViewerUsernames.map { username ->
                        val viewerProfile = viewerProfilesByUsername[username]
                        StoryViewer(
                            username = username,
                            avatarUrl = viewerProfile?.avatar_url,
                            avatarColor = getAvatarColor(username),
                            isVerified = viewerProfile?.is_verified ?: false
                        )
                    }
                    Story(
                        id = dto.id,
                        userId = dto.user_id,
                        username = senderUsername,
                        text = dto.content,
                        createdAt = dto.created_at,
                        expiresAt = dto.expires_at,
                        avatarColor = getAvatarColor(senderUsername),
                        views = filteredViewerUsernames,
                        viewers = filteredViewers,
                        backgroundColor = dto.background_color ?: "#000000",
                        textColor = dto.text_color ?: "#FFFFFF",
                        fontFamily = dto.font_family ?: "default",
                        isVerified = dto.users?.is_verified ?: false,
                        avatarUrl = dto.users?.avatar_url
                    )
                }
                val now = System.currentTimeMillis()
                db.storyCacheDao().replaceAll(stories.map { it.toCacheEntity(now) })
                db.cacheMetaDao().upsert(CacheMetaEntity(CacheConfig.META_KEY_STORIES, now))
                Result.success(stories)
            } else {
                val cachedStories = db.storyCacheDao().getAll().map { it.toDomain() }
                if (cachedStories.isNotEmpty()) Result.success(cachedStories)
                else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_fetch_stories_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            val cachedStories = db.storyCacheDao().getAll().map { it.toDomain() }
            if (cachedStories.isNotEmpty()) Result.success(cachedStories) else Result.failure(e)
        }
    }

    private suspend fun fetchProfilesByUsernames(usernames: List<String>): Map<String, ProfileDto> {
        if (usernames.isEmpty()) return emptyMap()
        return try {
            val filterValue = "in.(${usernames.joinToString(",")})"
            val response = apiService.getProfilesByUsername(filterValue)
            if (response.isSuccessful) {
                response.body()?.associateBy { it.username } ?: emptyMap()
            } else emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    override suspend fun createStory(
        text: String,
        backgroundColor: String,
        textColor: String,
        fontFamily: String
    ): Result<Unit> {
        if (text.length > 280) return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_story_too_long)))
        return try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR, 24)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val expiresString = sdf.format(calendar.time)

            val body = com.textsocial.app.data.model.CreateStoryRequest(
                user_id = (prefs.getUserId() ?: ""),
                content = text,
                expires_at = expiresString,
                background_color = backgroundColor,
                text_color = textColor,
                font_family = fontFamily
            )
            val response = apiService.createStory(body)
            if (response.isSuccessful) {
                invalidateStoriesCache()
                Result.success(Unit)
            } else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_share_story_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStoryViews(storyId: String): Result<List<String>> {
        return try {
            val response = apiService.getStoryViews("eq.$storyId")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.map { it.viewer_username })
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_get_story_views_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recordStoryView(storyId: String): Result<Unit> {
        return try {
            val body = com.textsocial.app.data.model.RecordStoryViewRequest(
                story_id = storyId,
                viewer_username = (prefs.getUsername() ?: "user")
            )
            val response = apiService.recordStoryView(body)
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_record_view_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteStory(storyId: String): Result<Unit> {
        return try {
            val response = apiService.deleteStory("eq.$storyId")
            if (response.isSuccessful) {
                invalidateStoriesCache()
                Result.success(Unit)
            } else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_delete_story_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class MessageRepositoryImpl(
    private val apiService: SupabaseApiService,
    private val prefs: EncryptedPreferencesManager,
    private val context: Context,
    private val db: AppDatabase
) : MessageRepository {

    private fun ConversationCacheEntity.toDomain() = Conversation(
        id = id,
        otherUserId = otherUserId,
        otherUsername = otherUsername,
        otherAvatarColor = otherAvatarColor,
        lastMessage = lastMessage,
        lastMessageTime = lastMessageTime,
        unreadCount = unreadCount,
        otherIsVerified = otherIsVerified,
        otherAvatarUrl = otherAvatarUrl
    )

    private fun Conversation.toCacheEntity(fetchedAt: Long) = ConversationCacheEntity(
        id = id,
        otherUserId = otherUserId,
        otherUsername = otherUsername,
        otherAvatarColor = otherAvatarColor,
        lastMessage = lastMessage,
        lastMessageTime = lastMessageTime,
        unreadCount = unreadCount,
        otherIsVerified = otherIsVerified,
        otherAvatarUrl = otherAvatarUrl,
        fetchedAt = fetchedAt
    )

    private fun MessageCacheEntity.toDomain() = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        text = text,
        createdAt = createdAt,
        isRead = isRead,
        isDeleted = isDeleted
    )

    private fun Message.toCacheEntity(fetchedAt: Long) = MessageCacheEntity(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        text = text,
        createdAt = createdAt,
        isRead = isRead,
        isDeleted = isDeleted,
        fetchedAt = fetchedAt
    )

    private val wsClient = OkHttpClient()
    private val webSockets = ConcurrentHashMap<String, WebSocket>()
    private val conversationMessages = ConcurrentHashMap<String, List<Message>>()
    private val _messageFlows = ConcurrentHashMap<String, MutableStateFlow<List<Message>>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private fun getConversationId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }

    private fun connectWebSocket(conversationId: String) {
        if (webSockets.containsKey(conversationId)) return

        val anonKey = SupabaseClient.SUPABASE_ANON_KEY
        val baseUrl = SupabaseClient.SUPABASE_URL
        if (baseUrl.contains("placeholder-project")) return

        val wsUrl = baseUrl.replace("https://", "wss://").replace("http://", "ws://")
        val wsEndpoint = if (wsUrl.endsWith("/")) "${wsUrl}realtime/v1/websocket?apikey=$anonKey" else "$wsUrl/realtime/v1/websocket?apikey=$anonKey"

        val request = Request.Builder().url(wsEndpoint).build()

        val newSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {

                val joinMsg = JSONObject().apply {
                    put("topic", "realtime:messages:$conversationId")
                    put("event", "phx_join")
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("postgres_changes", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("event", "*")
                                    put("schema", "public")
                                    put("table", "messages")
                                    put("filter", "conversation_id=eq.$conversationId")
                                })
                            })
                        })
                    })
                    put("ref", "1")
                }.toString()
                webSocket.send(joinMsg)

                scope.launch {
                    var ref = 2
                    while (webSockets[conversationId] != null) {
                        delay(25000)
                        try {
                            webSockets[conversationId]?.send("{\"topic\":\"phoenix\",\"event\":\"heartbeat\",\"payload\":{},\"ref\":\"${ref++}\"}")
                        } catch (e: Exception) {
                            break
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val event = json.optString("event")
                    if (event == "postgres_changes") {
                        val payload = json.optJSONObject("payload")
                        val data = payload?.optJSONObject("data")
                        val record = data?.optJSONObject("record")
                        if (record != null) {
                            val id = record.getString("id")
                            val convId = record.getString("conversation_id")
                            val senderId = record.getString("sender_id")
                            val textVal = record.getString("content")
                            val createdAt = record.getString("created_at")
                            val isRead = record.optBoolean("is_read", false)
                            val isDeleted = record.optBoolean("is_deleted", false)

                            val newMessage = Message(
                                id = id,
                                conversationId = convId,
                                senderId = senderId,
                                text = textVal,
                                createdAt = createdAt,
                                isRead = isRead,
                                isDeleted = isDeleted
                            )

                            val flow = _messageFlows.getOrPut(convId) { MutableStateFlow(emptyList()) }
                            val currentList = conversationMessages[convId] ?: emptyList()
                            val updated = if (currentList.any { it.id == newMessage.id }) {
                                currentList.map { if (it.id == newMessage.id) newMessage else it }
                            } else {
                                currentList + newMessage
                            }
                            conversationMessages[convId] = updated
                            flow.value = updated
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                webSockets.remove(conversationId)
                scope.launch {
                    delay(5000)
                    connectWebSocket(conversationId)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                webSockets.remove(conversationId)
            }
        })

        webSockets[conversationId] = newSocket
    }

    override suspend fun getConversations(): Result<List<Conversation>> {
        val myId = prefs.getUserId() ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_not_logged_in)))

        val cacheTimestamp = db.cacheMetaDao().getTimestamp(CacheConfig.META_KEY_CONVERSATIONS) ?: 0L
        if (CacheConfig.isFresh(cacheTimestamp, CacheConfig.CONVERSATIONS_TTL_MS)) {
            val cached = db.conversationCacheDao().getAll().map { it.toDomain() }
            if (cached.isNotEmpty()) return Result.success(cached)
        }

        return try {
            val response = apiService.getConversations("(user1_id.eq.$myId,user2_id.eq.$myId)")
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!.mapNotNull { dto ->
                    val isMeUser1 = dto.user1_id == myId
                    val hiddenForMe = if (isMeUser1) dto.hidden_for_user1 else dto.hidden_for_user2
                    if (hiddenForMe) return@mapNotNull null

                    val otherUserId = if (isMeUser1) dto.user2_id else dto.user1_id
                    val profileResponse = apiService.getProfile("eq.$otherUserId")
                    val otherProfile = if (profileResponse.isSuccessful) {
                        profileResponse.body()?.firstOrNull()
                    } else null

                    val latestMsg = dto.messages.maxByOrNull { it.created_at }
                    val unreadCount = dto.messages.count { it.sender_id != myId && !it.is_read }

                    Conversation(
                        id = dto.id,
                        otherUserId = otherUserId,
                        otherUsername = otherProfile?.username ?: "user_${otherUserId.take(4)}",
                        otherAvatarColor = getAvatarColor(otherProfile?.username),
                        lastMessage = latestMsg?.content,
                        lastMessageTime = latestMsg?.created_at ?: dto.updated_at,
                        unreadCount = unreadCount,
                        otherIsVerified = otherProfile?.is_verified ?: false,
                        otherAvatarUrl = otherProfile?.avatar_url
                    )
                }
                val sorted = list.sortedByDescending { it.lastMessageTime }
                val now = System.currentTimeMillis()
                db.conversationCacheDao().replaceAll(sorted.map { it.toCacheEntity(now) })
                db.cacheMetaDao().upsert(CacheMetaEntity(CacheConfig.META_KEY_CONVERSATIONS, now))
                Result.success(sorted)
            } else {
                val cached = db.conversationCacheDao().getAll().map { it.toDomain() }
                if (cached.isNotEmpty()) Result.success(cached)
                else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_load_conversations_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            val cached = db.conversationCacheDao().getAll().map { it.toDomain() }
            if (cached.isNotEmpty()) Result.success(cached) else Result.failure(e)
        }
    }

    override suspend fun getMessages(otherUserId: String): Result<List<Message>> {
        val myId = prefs.getUserId() ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_not_logged_in)))
        val conversationIdForCache = getConversationId(myId, otherUserId)
        val metaKey = CacheConfig.messagesMetaKey(conversationIdForCache)
        val cacheTimestamp = db.cacheMetaDao().getTimestamp(metaKey) ?: 0L
        if (CacheConfig.isFresh(cacheTimestamp, CacheConfig.MESSAGES_TTL_MS)) {
            val cachedMessages = db.messageCacheDao().getForConversation(conversationIdForCache).map { it.toDomain() }
            if (cachedMessages.isNotEmpty()) {
                conversationMessages[conversationIdForCache] = cachedMessages
                val flow = _messageFlows.getOrPut(conversationIdForCache) { MutableStateFlow(emptyList()) }
                flow.value = cachedMessages
                connectWebSocket(conversationIdForCache)
                return Result.success(cachedMessages)
            }
        }

        return try {
            val conversationId = getConversationId(myId, otherUserId)
            val response = apiService.getMessages("eq.$conversationId")
            if (response.isSuccessful && response.body() != null) {
                val messages = response.body()!!.map { dto ->
                    Message(
                        id = dto.id,
                        conversationId = dto.conversation_id,
                        senderId = dto.sender_id,
                        text = dto.content,
                        createdAt = dto.created_at,
                        isRead = dto.is_read,
                        isDeleted = dto.is_deleted
                    )
                }
                conversationMessages[conversationId] = messages
                val flow = _messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
                flow.value = messages

                val now = System.currentTimeMillis()
                db.messageCacheDao().replaceForConversation(conversationId, messages.map { it.toCacheEntity(now) })
                db.cacheMetaDao().upsert(CacheMetaEntity(CacheConfig.messagesMetaKey(conversationId), now))

                connectWebSocket(conversationId)

                Result.success(messages)
            } else {
                val cachedMessages = db.messageCacheDao().getForConversation(conversationId).map { it.toDomain() }
                if (cachedMessages.isNotEmpty()) {
                    conversationMessages[conversationId] = cachedMessages
                    val flow = _messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
                    flow.value = cachedMessages
                    connectWebSocket(conversationId)
                    Result.success(cachedMessages)
                } else {
                    Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_get_messages_failed, response.code().toString())))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val ensuredConversations = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private suspend fun ensureConversationExists(conversationId: String, myId: String, otherUserId: String) {
        if (ensuredConversations.contains(conversationId)) return
        try {
            val uid1: String
            val uid2: String
            if (myId < otherUserId) {
                uid1 = myId; uid2 = otherUserId
            } else {
                uid1 = otherUserId; uid2 = myId
            }
            val response = apiService.upsertConversation(
                com.textsocial.app.data.model.UpsertConversationRequest(
                    id = conversationId,
                    user1_id = uid1,
                    user2_id = uid2
                )
            )
            if (response.isSuccessful) {
                ensuredConversations.add(conversationId)
            }
        } catch (_: Exception) {
        }
    }

    override suspend fun sendMessage(otherUserId: String, text: String): Result<Unit> {
        val myId = prefs.getUserId() ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_not_logged_in)))
        return try {
            val conversationId = getConversationId(myId, otherUserId)
            ensureConversationExists(conversationId, myId, otherUserId)

            val body = com.textsocial.app.data.model.SendMessageRequest(
                conversation_id = conversationId,
                sender_id = myId,
                content = text
            )
            val response = apiService.sendMessage(body)
            if (response.isSuccessful) {
                val createdDto = response.body()?.firstOrNull()
                if (createdDto != null) {
                    val newMessage = Message(
                        id = createdDto.id,
                        conversationId = createdDto.conversation_id,
                        senderId = createdDto.sender_id,
                        text = createdDto.content,
                        createdAt = createdDto.created_at,
                        isRead = createdDto.is_read,
                        isDeleted = createdDto.is_deleted
                    )
                    val flow = _messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
                    val currentList = conversationMessages[conversationId] ?: emptyList()
                    if (currentList.none { it.id == newMessage.id }) {
                        val updated = currentList + newMessage
                        conversationMessages[conversationId] = updated
                        flow.value = updated
                        db.messageCacheDao().insertAll(listOf(newMessage.toCacheEntity(System.currentTimeMillis())))
                    }
                    // Daftar percakapan berubah (last message baru) -> paksa refresh berikutnya.
                    db.cacheMetaDao().upsert(CacheMetaEntity(CacheConfig.META_KEY_CONVERSATIONS, 0L))
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_send_message_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markMessagesAsRead(otherUserId: String): Result<Unit> {
        val myId = prefs.getUserId() ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_not_logged_in)))
        return try {
            val conversationId = getConversationId(myId, otherUserId)
            val response = apiService.markMessagesAsRead(
                conversationFilter = "eq.$conversationId",
                senderFilter = "neq.$myId"
            )
            if (response.isSuccessful) {
                db.cacheMetaDao().upsert(CacheMetaEntity(CacheConfig.META_KEY_CONVERSATIONS, 0L))
                Result.success(Unit)
            } else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_mark_read_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMessageForEveryone(otherUserId: String, messageId: String): Result<Unit> {
        val myId = prefs.getUserId() ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_not_logged_in)))
        return try {
            val conversationId = getConversationId(myId, otherUserId)
            val response = apiService.deleteMessageForEveryone(idFilter = "eq.$messageId")
            if (response.isSuccessful) {
                val flow = _messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
                val currentList = conversationMessages[conversationId] ?: emptyList()
                val updated = currentList.map {
                    if (it.id == messageId) it.copy(text = LocaleManager.applyLocale(context).getString(R.string.pesan_dihapus), isDeleted = true) else it
                }
                conversationMessages[conversationId] = updated
                flow.value = updated
                Result.success(Unit)
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_delete_message_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun hideConversation(otherUserId: String): Result<Unit> {
        return hideConversations(listOf(otherUserId))
    }

    override suspend fun hideConversations(otherUserIds: List<String>): Result<Unit> {
        if (otherUserIds.isEmpty()) return Result.success(Unit)
        val myId = prefs.getUserId() ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_not_logged_in)))
        return try {
            val idsWhereImUser1 = otherUserIds.filter { myId < it }.map { getConversationId(myId, it) }
            val idsWhereImUser2 = otherUserIds.filter { myId >= it }.map { getConversationId(myId, it) }

            if (idsWhereImUser1.isNotEmpty()) {
                val filter = "in.(${idsWhereImUser1.joinToString(",")})"
                val response = apiService.hideConversationsForUser1(filter)
                if (!response.isSuccessful) {
                    return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_hide_conversation_failed, response.code().toString())))
                }
            }
            if (idsWhereImUser2.isNotEmpty()) {
                val filter = "in.(${idsWhereImUser2.joinToString(",")})"
                val response = apiService.hideConversationsForUser2(filter)
                if (!response.isSuccessful) {
                    return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_hide_conversation_failed, response.code().toString())))
                }
            }
            db.cacheMetaDao().upsert(CacheMetaEntity(CacheConfig.META_KEY_CONVERSATIONS, 0L))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeMessages(otherUserId: String): Flow<List<Message>> {
        val myId = prefs.getUserId() ?: "me_id"
        val conversationId = getConversationId(myId, otherUserId)
        connectWebSocket(conversationId)
        return _messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.asStateFlow()
    }
}

class UserRepositoryImpl(
    private val apiService: SupabaseApiService,
    private val prefs: EncryptedPreferencesManager,
    private val context: Context,
    private val db: AppDatabase
) : UserRepository {

    private fun ProfileCacheEntity.toDomain() = User(
        id = userId,
        username = username,
        email = email,
        displayName = displayName,
        bio = bio,
        avatarColor = getAvatarColor(username),
        isPrivate = isPrivate,
        followersCount = followersCount,
        followingCount = followingCount,
        postsCount = postsCount,
        isVerified = isVerified,
        avatarUrl = avatarUrl,
        hideFollowingList = hideFollowingList
    )

    private suspend fun cacheProfile(p: ProfileDto) {
        val existing = db.profileCacheDao().getByUserId(p.id)
        db.profileCacheDao().upsert(
            ProfileCacheEntity(
                userId = p.id,
                username = p.username,
                email = p.email ?: "",
                displayName = p.display_name ?: p.username.replaceFirstChar { it.uppercase() },
                bio = p.bio ?: LocaleManager.applyLocale(context).getString(R.string.bio_welcome_oss),
                isPrivate = p.is_private,
                followersCount = existing?.followersCount ?: 0,
                followingCount = existing?.followingCount ?: 0,
                postsCount = existing?.postsCount ?: 0,
                isVerified = p.is_verified,
                avatarUrl = p.avatar_url,
                hideFollowingList = p.hide_following_list,
                fetchedAt = System.currentTimeMillis(),
                followCountsFetchedAt = existing?.followCountsFetchedAt ?: 0L
            )
        )
    }

    /** Paksa profil (dan follow counts-nya) di-refresh ulang di panggilan berikutnya. */
    private suspend fun invalidateProfileCache(userId: String) {
        val existing = db.profileCacheDao().getByUserId(userId) ?: return
        db.profileCacheDao().upsert(existing.copy(fetchedAt = 0L, followCountsFetchedAt = 0L))
    }

    override suspend fun getProfile(userId: String): Result<User> {
        return try {
            val resolvedUserId = if (userId == "me_id") {
                prefs.getUserId() ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_not_logged_in)))
            } else {
                userId
            }

            val cached = db.profileCacheDao().getByUserId(resolvedUserId)
            if (cached != null && CacheConfig.isFresh(cached.fetchedAt, CacheConfig.PROFILE_TTL_MS)) {
                return Result.success(cached.toDomain())
            }

            val response = apiService.getProfile("eq.$resolvedUserId")
            if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                val p = response.body()!!.first()
                cacheProfile(p)
                val user = User(
                    id = p.id,
                    username = p.username,
                    email = p.email ?: "",
                    displayName = p.display_name ?: p.username.replaceFirstChar { it.uppercase() },
                    bio = p.bio ?: LocaleManager.applyLocale(context).getString(R.string.bio_welcome_oss),
                    avatarColor = getAvatarColor(p.username),
                    isPrivate = p.is_private,
                    isVerified = p.is_verified,
                    avatarUrl = p.avatar_url,
                    hideFollowingList = p.hide_following_list
                )
                Result.success(user)
            } else if (cached != null) {
                Result.success(cached.toDomain())
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_profile_not_found, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getProfileByUsername(username: String): Result<User> {
        return try {
            val cached = db.profileCacheDao().getByUsername(username.lowercase())
            if (cached != null && CacheConfig.isFresh(cached.fetchedAt, CacheConfig.PROFILE_TTL_MS)) {
                return Result.success(cached.toDomain())
            }

            val response = apiService.getProfilesByUsername("eq.${username.lowercase()}")
            if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                val p = response.body()!!.first()
                cacheProfile(p)
                val user = User(
                    id = p.id,
                    username = p.username,
                    email = p.email ?: "",
                    displayName = p.display_name ?: p.username.replaceFirstChar { it.uppercase() },
                    bio = p.bio ?: LocaleManager.applyLocale(context).getString(R.string.bio_welcome_oss),
                    avatarColor = getAvatarColor(p.username),
                    isPrivate = p.is_private,
                    isVerified = p.is_verified,
                    avatarUrl = p.avatar_url,
                    hideFollowingList = p.hide_following_list
                )
                Result.success(user)
            } else if (cached != null) {
                Result.success(cached.toDomain())
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_username_profile_not_found, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProfile(displayName: String?, bio: String?, isPrivate: Boolean): Result<Unit> {
        return try {
            val myId = prefs.getUserId() ?: ""
            val body = com.textsocial.app.data.model.UpdateProfileRequest(
                display_name = displayName,
                bio = bio,
                is_private = isPrivate
            )
            val response = apiService.updateProfile("eq.$myId", body)
            if (response.isSuccessful) {
                invalidateProfileCache(myId)
                Result.success(Unit)
            } else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_update_profile_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadAvatar(imageBytes: ByteArray): Result<String> {
        return try {
            val myId = prefs.getUserId() ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_not_logged_in)))
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val requestBody = imageBytes.toRequestBody(mediaType)
            val objectPath = "$myId/profile.jpg"

            val uploadResponse = apiService.uploadAvatar(
                bucketPath = "avatars/$objectPath",
                file = requestBody,
                contentType = "image/jpeg"
            )
            if (!uploadResponse.isSuccessful) {
                return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_upload_photo_failed, uploadResponse.code().toString())))
            }

            val baseUrl = SupabaseClient.SUPABASE_URL.trimEnd('/')
            val publicUrl = "$baseUrl/storage/v1/object/public/avatars/$objectPath?v=${System.currentTimeMillis()}"

            val saveResponse = apiService.updateAvatarUrl("eq.$myId", UpdateAvatarRequest(avatar_url = publicUrl))
            if (!saveResponse.isSuccessful) {
                return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_save_profile_photo_failed, saveResponse.code().toString())))
            }

            invalidateProfileCache(myId)
            Result.success(publicUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun followUser(targetUserId: String): Result<Unit> {
        return try {
            val myId = prefs.getUserId() ?: ""
            val body = com.textsocial.app.data.model.FollowUserRequest(
                follower_id = myId,
                following_id = targetUserId
            )
            val response = apiService.followUser(body)
            if (response.isSuccessful) {
                invalidateProfileCache(targetUserId)
                invalidateProfileCache(myId)
                Result.success(Unit)
            } else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_follow_user_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unfollowUser(targetUserId: String): Result<Unit> {
        return try {
            val myId = prefs.getUserId() ?: ""
            val response = apiService.unfollowUser(followerFilter = "eq.$myId", followingFilter = "eq.$targetUserId")
            if (response.isSuccessful) {
                invalidateProfileCache(targetUserId)
                invalidateProfileCache(myId)
                Result.success(Unit)
            } else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_unfollow_user_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isFollowing(targetUserId: String): Result<Boolean> {
        return try {
            val myId = prefs.getUserId() ?: return Result.success(false)
            // Cek pasangan (myId, targetUserId) langsung lewat unique constraint -- dulu
            // manggil getFollowers(targetUserId) TANPA LIMIT (narik seluruh follower akun
            // target cuma buat cek 1 boolean), bisa berat banget di akun populer/verified.
            val response = apiService.checkFollowExists(
                followerIdFilter = "eq.$myId",
                followingIdFilter = "eq.$targetUserId"
            )
            if (response.isSuccessful) {
                Result.success(response.body()?.isNotEmpty() ?: false)
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_check_follow_status_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFollowCounts(userId: String): Result<Pair<Int, Int>> {
        val cached = db.profileCacheDao().getByUserId(userId)
        if (cached != null && CacheConfig.isFresh(cached.followCountsFetchedAt, CacheConfig.FOLLOW_COUNTS_TTL_MS)) {
            return Result.success(cached.followersCount to cached.followingCount)
        }

        return try {
            // Dulu narik SELURUH baris follows lewat getFollowers()/getFollowing() lalu
            // di-.size() di client. Sekarang minta Postgrest hitung langsung lewat header
            // Content-Range (count=exact) -- payload cuma 1 baris (limit=1), count di
            // header tetap akurat terhadap total baris yang match filter.
            val followersResponse = apiService.getFollowersCount("eq.$userId")
            val followingResponse = apiService.getFollowingCount("eq.$userId")
            val followersCount = parseContentRangeTotal(followersResponse.headers()["Content-Range"]) ?: 0
            val followingCount = parseContentRangeTotal(followingResponse.headers()["Content-Range"]) ?: 0
            db.profileCacheDao().updateFollowCounts(userId, followersCount, followingCount, System.currentTimeMillis())
            Result.success(followersCount to followingCount)
        } catch (e: Exception) {
            if (cached != null) Result.success(cached.followersCount to cached.followingCount) else Result.failure(e)
        }
    }

    /** Ambil profil untuk banyak id sekaligus lewat "id=in.(...)", di-chunk per 100 id supaya
     *  panjang URL tetap aman. Dulu 1 request per id secara SEQUENTIAL (N+1 query) -- follow
     *  list dengan ribuan follower berarti ribuan HTTP request berurutan cuma buat render 1 layar. */
    private suspend fun mapUserIdsToProfiles(ids: List<String>): List<User> {
        if (ids.isEmpty()) return emptyList()
        val profilesById = HashMap<String, ProfileDto>()
        for (chunk in ids.distinct().chunked(100)) {
            try {
                val filter = "in.(${chunk.joinToString(",")})"
                val profileResp = apiService.getProfile(filter)
                profileResp.body()?.forEach { p -> profilesById[p.id] = p }
            } catch (e: Exception) {
                // Satu chunk gagal tidak menggagalkan semuanya -- id yang tidak ketemu
                // tetap difilter lewat mapNotNull di bawah, sama seperti perilaku lama
                // saat 1 getProfile() individual gagal.
            }
        }
        return ids.mapNotNull { id ->
            profilesById[id]?.let { p ->
                User(
                    id = p.id,
                    username = p.username,
                    email = p.email ?: "",
                    displayName = p.display_name,
                    bio = p.bio,
                    avatarColor = getAvatarColor(p.username),
                    isPrivate = p.is_private,
                    isVerified = p.is_verified,
                    avatarUrl = p.avatar_url,
                    hideFollowingList = p.hide_following_list
                )
            }
        }
    }

    override suspend fun getFollowingUsers(): Result<List<User>> {
        return try {
            val myId = prefs.getUserId() ?: return Result.success(emptyList())
            val followingResponse = apiService.getFollowing("eq.$myId")
            if (!followingResponse.isSuccessful) {
                return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_load_following_failed, followingResponse.code().toString())))
            }
            val followingIds = followingResponse.body()?.map { it.following_id } ?: emptyList()
            Result.success(mapUserIdsToProfiles(followingIds))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFollowersOf(userId: String): Result<List<User>> {
        return try {
            val response = apiService.getFollowers("eq.$userId")
            if (!response.isSuccessful) {
                return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_load_followers_failed, response.code().toString())))
            }
            val ids = response.body()?.map { it.follower_id } ?: emptyList()
            Result.success(mapUserIdsToProfiles(ids))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFollowingOf(userId: String): Result<List<User>> {
        return try {
            val response = apiService.getFollowing("eq.$userId")
            if (!response.isSuccessful) {
                return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_load_following_failed, response.code().toString())))
            }
            val ids = response.body()?.map { it.following_id } ?: emptyList()
            Result.success(mapUserIdsToProfiles(ids))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isFollowedBy(targetUserId: String): Result<Boolean> {
        return try {
            val myId = prefs.getUserId() ?: return Result.success(false)
            // Sama seperti isFollowing() -- cek pasangan (targetUserId, myId) langsung,
            // bukan narik seluruh followers list milik diri sendiri.
            val response = apiService.checkFollowExists(
                followerIdFilter = "eq.$targetUserId",
                followingIdFilter = "eq.$myId"
            )
            if (response.isSuccessful) {
                Result.success(response.body()?.isNotEmpty() ?: false)
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_check_followback_status_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateFollowListPrivacy(hideFollowingList: Boolean): Result<Unit> {
        return try {
            val myId = prefs.getUserId() ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_not_logged_in)))
            val response = apiService.updateFollowListPrivacy(
                "eq.$myId",
                UpdateFollowListPrivacyRequest(hide_following_list = hideFollowingList)
            )
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_update_privacy_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUsername(newUsername: String): Result<Unit> {
        val cleanUsername = newUsername.trim().lowercase()

        if (cleanUsername.length < 3) {
            return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_username_min_length)))
        }
        if (!cleanUsername.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_username_invalid_chars)))
        }

        return try {
            val myId = prefs.getUserId() ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_not_logged_in)))
            val checkResponse = apiService.getProfilesByUsername("eq.$cleanUsername")
            val takenByOther = checkResponse.body()?.any { it.id != myId } ?: false
            if (takenByOther) {
                return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_username_taken)))
            }

            val response = apiService.updateUsername("eq.$myId", UpdateUsernameRequest(username = cleanUsername))
            if (response.isSuccessful) {
                prefs.saveUsername(cleanUsername)
                Result.success(Unit)
            } else if (response.code() == 409) {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_username_taken)))
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_username_update_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchUsers(query: String): Result<List<User>> {
        if (query.isBlank()) return Result.success(emptyList())
        return try {
            val response = apiService.getProfilesByUsername("ilike.%$query%")
            if (response.isSuccessful && response.body() != null) {
                val users = response.body()!!.map { p ->
                    User(
                        id = p.id,
                        username = p.username,
                        email = p.email ?: "",
                        displayName = p.display_name ?: p.username.replaceFirstChar { it.uppercase() },
                        bio = p.bio ?: "",
                        avatarColor = getAvatarColor(p.username),
                        isPrivate = p.is_private,
                        isVerified = p.is_verified,
                        avatarUrl = p.avatar_url,
                        hideFollowingList = p.hide_following_list
                    )
                }
                Result.success(users)
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_search_profiles_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun NotificationCacheEntity.toDomain() = Notification(
        id = id,
        type = type,
        senderId = senderId,
        senderUsername = senderUsername,
        senderAvatarColor = senderAvatarColor,
        postId = postId,
        commentId = commentId,
        text = text,
        createdAt = createdAt,
        isRead = isRead,
        senderIsVerified = senderIsVerified,
        senderAvatarUrl = senderAvatarUrl
    )

    private fun Notification.toCacheEntity(fetchedAt: Long) = NotificationCacheEntity(
        id = id,
        type = type,
        senderId = senderId,
        senderUsername = senderUsername,
        senderAvatarColor = senderAvatarColor,
        postId = postId,
        commentId = commentId,
        text = text,
        createdAt = createdAt,
        isRead = isRead,
        senderIsVerified = senderIsVerified,
        senderAvatarUrl = senderAvatarUrl,
        fetchedAt = fetchedAt
    )

    override suspend fun getNotifications(): Result<List<Notification>> {
        val cacheTimestamp = db.cacheMetaDao().getTimestamp(CacheConfig.META_KEY_NOTIFICATIONS) ?: 0L
        if (CacheConfig.isFresh(cacheTimestamp, CacheConfig.NOTIFICATIONS_TTL_MS)) {
            val cached = db.notificationCacheDao().getAll().map { it.toDomain() }
            if (cached.isNotEmpty()) return Result.success(cached)
        }

        return try {
            val myId = prefs.getUserId() ?: ""
            val response = apiService.getNotifications("eq.$myId")
            if (response.isSuccessful && response.body() != null) {
                val notifications = response.body()!!.map { dto ->
                    val senderUsername = dto.sender?.username ?: "someone"
                    val senderAvatarColor = getAvatarColor(senderUsername)

                    Notification(
                        id = dto.id,
                        type = dto.type,
                        senderId = dto.sender_id,
                        senderUsername = senderUsername,
                        senderAvatarColor = senderAvatarColor,
                        postId = dto.post_id,
                        commentId = dto.comment_id,
                        text = "",
                        createdAt = dto.created_at,
                        isRead = dto.is_read,
                        senderIsVerified = dto.sender?.is_verified ?: false,
                        senderAvatarUrl = dto.sender?.avatar_url
                    )
                }
                val now = System.currentTimeMillis()
                db.notificationCacheDao().replaceAll(notifications.map { it.toCacheEntity(now) })
                db.cacheMetaDao().upsert(CacheMetaEntity(CacheConfig.META_KEY_NOTIFICATIONS, now))
                Result.success(notifications)
            } else {
                val cached = db.notificationCacheDao().getAll().map { it.toDomain() }
                if (cached.isNotEmpty()) Result.success(cached)
                else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_fetch_notifications_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            val cached = db.notificationCacheDao().getAll().map { it.toDomain() }
            if (cached.isNotEmpty()) Result.success(cached) else Result.failure(e)
        }
    }

    override suspend fun markNotificationAsRead(notificationId: String): Result<Unit> {
        return try {
            val response = apiService.markNotificationAsRead(idFilter = "eq.$notificationId")
            if (response.isSuccessful) {
                db.notificationCacheDao().markRead(notificationId)
                Result.success(Unit)
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_mark_notification_read_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markNotificationsAsRead(): Result<Unit> {
        return try {
            val myId = prefs.getUserId() ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_not_logged_in)))
            val response = apiService.markNotificationsAsRead(recipientFilter = "eq.$myId")
            if (response.isSuccessful) {
                db.notificationCacheDao().markAllRead()
                Result.success(Unit)
            } else {
                Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_mark_notifications_read_failed, response.code().toString())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteNotification(notificationId: String): Result<Unit> {
        return try {
            val response = apiService.deleteNotifications("eq.$notificationId")
            if (response.isSuccessful) {
                db.notificationCacheDao().deleteById(notificationId)
                Result.success(Unit)
            } else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_delete_notification_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteNotifications(notificationIds: List<String>): Result<Unit> {
        if (notificationIds.isEmpty()) return Result.success(Unit)
        return try {
            val filter = "in.(${notificationIds.joinToString(",")})"
            val response = apiService.deleteNotifications(filter)
            if (response.isSuccessful) {
                db.notificationCacheDao().deleteByIds(notificationIds)
                Result.success(Unit)
            } else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_delete_notifications_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrendingHashtags(): Result<List<Pair<String, Int>>> {
        val cacheTimestamp = db.cacheMetaDao().getTimestamp(CacheConfig.META_KEY_TRENDING_HASHTAGS) ?: 0L
        if (CacheConfig.isFresh(cacheTimestamp, CacheConfig.TRENDING_HASHTAGS_TTL_MS)) {
            val cached = db.trendingHashtagCacheDao().getAll().map { it.tag to it.count }
            if (cached.isNotEmpty()) return Result.success(cached)
        }

        return try {
            // Dulu ini narik SELURUH tabel posts cuma buat hitung hashtag -- makin lama
            // makin berat seiring post bertambah. Sekarang dibatasi ke N post TERBARU saja:
            // trending secara alami memang cuma peduli aktivitas belakangan, jadi ini juga
            // lebih benar secara produk, bukan cuma lebih murah.
            val response = apiService.getPosts(limit = TRENDING_HASHTAG_SCAN_WINDOW)
            if (response.isSuccessful && response.body() != null) {
                val posts = response.body()!!
                val hashtagCounts = mutableMapOf<String, Int>()
                val regex = Regex("#(\\w+)")
                for (post in posts) {
                    regex.findAll(post.content).forEach { match ->
                        val tag = match.groups[1]?.value?.lowercase()
                        if (tag != null) {
                            hashtagCounts[tag] = (hashtagCounts[tag] ?: 0) + 1
                        }
                    }
                }
                val sortedTags = hashtagCounts.entries
                    .sortedByDescending { it.value }
                    .map { it.key to it.value }

                val now = System.currentTimeMillis()
                db.trendingHashtagCacheDao().replaceAll(sortedTags.map { TrendingHashtagCacheEntity(it.first, it.second, now) })
                db.cacheMetaDao().upsert(CacheMetaEntity(CacheConfig.META_KEY_TRENDING_HASHTAGS, now))

                Result.success(sortedTags)
            } else {
                val cached = db.trendingHashtagCacheDao().getAll().map { it.tag to it.count }
                Result.success(cached)
            }
        } catch (e: Exception) {
            val cached = db.trendingHashtagCacheDao().getAll().map { it.tag to it.count }
            Result.success(cached)
        }
    }

    override suspend fun registerDeviceToken(fcmToken: String): Result<Unit> {
        return try {
            val userId = prefs.getUserId() ?: return Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_not_logged_in)))
            val response = apiService.upsertDeviceToken(
                UpsertDeviceTokenRequest(user_id = userId, fcm_token = fcmToken)
            )
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_register_device_token_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unregisterDeviceToken(fcmToken: String): Result<Unit> {
        return try {
            val response = apiService.deleteDeviceToken("eq.$fcmToken")
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception(LocaleManager.applyLocale(context).getString(R.string.error_unregister_device_token_failed, response.code().toString())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class AppUpdateRepositoryImpl(
    private val apiService: SupabaseApiService,
    private val prefs: EncryptedPreferencesManager
) : AppUpdateRepository {

    override suspend fun checkForUpdate(): Result<AppUpdateInfo?> {
        return try {
            val response = apiService.getLatestAppVersion()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Error ${response.code()}: gagal mengambil info versi"))
            }
            val latest = response.body()?.firstOrNull() ?: return Result.success(null)
            val currentVersionCode = com.textsocial.app.BuildConfig.VERSION_CODE

            if (latest.version_code <= currentVersionCode) {
                return Result.success(null)
            }

            val isForceUpdate = latest.min_supported_version_code?.let { currentVersionCode < it } ?: false

            Result.success(
                AppUpdateInfo(
                    versionCode = latest.version_code,
                    versionName = latest.version_name,
                    releaseNotes = latest.release_notes,
                    downloadUrl = latest.download_url,
                    isForceUpdate = isForceUpdate
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getDismissedVersionCode(): Int = prefs.getDismissedUpdateVersionCode()

    override fun dismissVersion(versionCode: Int) = prefs.saveDismissedUpdateVersionCode(versionCode)
}