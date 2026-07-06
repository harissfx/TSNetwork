package com.textsocial.app.data.repository

import com.textsocial.app.data.api.SupabaseApiService
import com.textsocial.app.data.api.SupabaseClient
import com.textsocial.app.data.local.EncryptedPreferencesManager
import com.textsocial.app.data.model.*
import com.textsocial.app.domain.model.*
import com.textsocial.app.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SupabaseAuthException(val code: Int, val errorMsg: String) : Exception("Error $code: $errorMsg")

private fun getSupabaseError(code: Int, errorBody: String?): SupabaseAuthException {
    if (errorBody.isNullOrBlank()) {
        return SupabaseAuthException(code, "API error")
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
    private val prefs: EncryptedPreferencesManager
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
                bio = "Welcome to my profile!",
                avatarColor = prefs.getUserAvatarColor(),
                followersCount = 0,
                followingCount = 0,
                postsCount = 0
            )
        }
    }

    override suspend fun login(usernameOrEmail: String, password: String): Result<User> {
        if (usernameOrEmail.isBlank() || password.isBlank()) {
            return Result.failure(Exception("Username/email and password cannot be empty"))
        }

        return try {
            val email = if (usernameOrEmail.contains("@")) {
                usernameOrEmail
            } else {
                val profileResponse = apiService.getProfilesByUsername("eq.$usernameOrEmail")
                if (profileResponse.isSuccessful) {
                    profileResponse.body()?.firstOrNull()?.email
                        ?: return Result.failure(Exception("Username not found"))
                } else {
                    return Result.failure(Exception("Failed to resolve email from username"))
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
                        bio = "Just joined the wave!"
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
                    bio = userProfile.bio ?: "No bio yet",
                    avatarColor = getAvatarColor(userProfile.username),
                    isPrivate = userProfile.is_private
                )
                _currentUser.value = user
                Result.success(user)
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(getSupabaseError(response.code(), errorBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(username: String, email: String, password: String): Result<User> {
        if (username.length < 3) return Result.failure(Exception("Username must be at least 3 characters"))
        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return Result.failure(Exception("Username can only contain letters, numbers, and underscores"))
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
                        bio = "Just joined the open-source wave! 👋",
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
                        isPrivate = false
                    )
                    _currentUser.value = user
                    Result.success(user)
                } else {
                    val errorBody = loginResponse.errorBody()?.string()
                    Result.failure(getSupabaseError(loginResponse.code(), errorBody))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(getSupabaseError(response.code(), errorBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun forgotPassword(email: String): Result<Unit> {
        return try {
            val response = apiService.recoverPassword(com.textsocial.app.data.model.RecoverPasswordRequest(email = email))
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to request password reset"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        prefs.clear()
        _currentUser.value = null
        return Result.success(Unit)
    }

    override suspend fun refreshToken(): Result<Unit> {
        val refreshToken = prefs.getRefreshToken() ?: return Result.failure(Exception("No refresh token available"))
        return try {
            val response = apiService.refreshToken(com.textsocial.app.data.model.RefreshTokenRequest(refresh_token = refreshToken))
            if (response.isSuccessful && response.body() != null) {
                val authData = response.body()!!
                prefs.saveToken(authData.access_token)
                prefs.saveRefreshToken(authData.refresh_token)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to refresh token: ${response.code()}"))
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
    private val prefs: EncryptedPreferencesManager
) : PostRepository {

    private val localPosts = MutableStateFlow<List<Post>>(emptyList())

    override suspend fun getPosts(hashtag: String?): Result<List<Post>> {
        return try {
            val response = apiService.getPosts()
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!
                val myId = prefs.getUserId() ?: ""

                val likesResponse = apiService.getLikes(postIdFilter = null, userIdFilter = "eq.$myId")
                val likedPostIds = if (likesResponse.isSuccessful) {
                    likesResponse.body()?.map { it.post_id }?.toSet() ?: emptySet()
                } else emptySet()

                val posts = dtos.map { dto ->
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
                        isLiked = likedPostIds.contains(dto.id)
                    )
                }

                val filtered = if (hashtag != null) {
                    posts.filter { it.text.contains(hashtag, ignoreCase = true) }
                } else posts

                localPosts.value = filtered
                Result.success(filtered)
            } else {
                Result.failure(Exception("Failed to retrieve posts from server: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createPost(text: String): Result<Unit> {
        if (text.length > 500) {
            return Result.failure(Exception("Post exceeds max 500 characters"))
        }

        return try {
            val myId = prefs.getUserId() ?: ""
            val body = com.textsocial.app.data.model.CreatePostRequest(
                user_id = myId,
                content = text
            )
            val response = apiService.createPost(body)
            if (!response.isSuccessful) {
                return Result.failure(Exception("Failed to publish post: ${response.code()}"))
            }

            // Kirim notifikasi tipe "mention" ke setiap @username yang disebut di teks
            // post, supaya orang yang di-mention tahu ada yang menyinggung mereka.
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
                        // Satu mention gagal notif tidak boleh menggagalkan post yang sudah berhasil dibuat
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePost(postId: String): Result<Unit> {
        return try {
            val response = apiService.deletePost("eq.$postId")
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to delete post: ${response.code()}"))
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
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to like post: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unlikePost(postId: String): Result<Unit> {
        return try {
            val myId = prefs.getUserId() ?: ""
            val response = apiService.unlikePost(postIdFilter = "eq.$postId", userIdFilter = "eq.$myId")
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to unlike post: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getComments(postId: String): Result<List<Comment>> {
        return try {
            val response = apiService.getComments("eq.$postId")
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!
                val myId = prefs.getUserId() ?: ""

                // Ambil semua like untuk komentar-komentar di post ini sekaligus (bukan
                // satu-satu per komentar) supaya tidak N+1 query yang berlebihan.
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
                        text = dto.content,
                        createdAt = dto.created_at,
                        avatarColor = getAvatarColor(commenterUsername),
                        parentId = dto.parent_id,
                        likesCount = likesForComment.size,
                        isLiked = likesForComment.any { it.user_id == myId }
                    )
                }
                Result.success(comments)
            } else {
                Result.failure(Exception("Failed to retrieve comments: ${response.code()}"))
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
                return Result.failure(Exception("Failed to create comment: ${response.code()}"))
            }

            // Kirim notifikasi tipe "mention" ke setiap @username yang disebut di teks
            // komentar, sama seperti pada post, supaya orang yang di-mention di kolom
            // komentar juga kebagian notifikasi.
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
                        // Satu mention gagal notif tidak boleh menggagalkan komentar yang sudah berhasil dibuat
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
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to delete comment: ${response.code()}"))
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
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to like comment: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unlikeComment(commentId: String): Result<Unit> {
        return try {
            val myId = prefs.getUserId() ?: ""
            val response = apiService.unlikeComment(commentIdFilter = "eq.$commentId", userIdFilter = "eq.$myId")
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to unlike comment: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class StoryRepositoryImpl(
    private val apiService: SupabaseApiService,
    private val prefs: EncryptedPreferencesManager
) : StoryRepository {

    override suspend fun getStories(): Result<List<Story>> {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val nowString = sdf.format(Date())
            val response = apiService.getStories("gt.$nowString")
            if (response.isSuccessful && response.body() != null) {
                val stories = response.body()!!.map { dto ->
                    val viewsResponse = apiService.getStoryViews("eq.${dto.id}")
                    val viewerUsernames = if (viewsResponse.isSuccessful) {
                        viewsResponse.body()?.map { it.viewer_username } ?: emptyList()
                    } else emptyList()

                    val senderUsername = dto.users?.username ?: "user"
                    // Jaring pengaman untuk data lama: pastikan username pemilik story sendiri
                    // tidak pernah muncul di daftar viewer-nya sendiri, walau sempat kecatat
                    // sebelum fix ini ada.
                    val filteredViewers = viewerUsernames.filter { it != senderUsername }
                    Story(
                        id = dto.id,
                        userId = dto.user_id,
                        username = senderUsername,
                        text = dto.content,
                        createdAt = dto.created_at,
                        expiresAt = dto.expires_at,
                        avatarColor = getAvatarColor(senderUsername),
                        views = filteredViewers
                    )
                }
                Result.success(stories)
            } else {
                Result.failure(Exception("Failed to fetch stories: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createStory(text: String): Result<Unit> {
        if (text.length > 280) return Result.failure(Exception("Story exceeds 280 characters limit"))
        return try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR, 24)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val expiresString = sdf.format(calendar.time)

            val body = com.textsocial.app.data.model.CreateStoryRequest(
                user_id = (prefs.getUserId() ?: ""),
                content = text,
                expires_at = expiresString
            )
            val response = apiService.createStory(body)
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to share story: ${response.code()}"))
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
                Result.failure(Exception("Failed to get story views: ${response.code()}"))
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
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to record view: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteStory(storyId: String): Result<Unit> {
        return try {
            val response = apiService.deleteStory("eq.$storyId")
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to delete story: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class MessageRepositoryImpl(
    private val apiService: SupabaseApiService,
    private val prefs: EncryptedPreferencesManager
) : MessageRepository {

    private val wsClient = OkHttpClient()
    // Satu WebSocket per conversation (sebelumnya cuma 1 websocket global untuk semua chat,
    // jadi kalau buka chat kedua, koneksi chat pertama ketimpa/rusak).
    private val webSockets = ConcurrentHashMap<String, WebSocket>()
    private val conversationMessages = ConcurrentHashMap<String, List<Message>>()
    private val _messageFlows = ConcurrentHashMap<String, MutableStateFlow<List<Message>>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // PENTING: format ID ini HARUS sama persis dengan yang diharapkan trigger
    // `handle_message_before_insert` di database (lihat README.md), yang mem-parsing
    // conversation_id dengan `split_part(new.conversation_id, '_', 1/2)::uuid`.
    // Trigger itu otomatis membuat baris di tabel `conversations` sebelum pesan pertama
    // disimpan, jadi client TIDAK PERLU (dan TIDAK BOLEH) membuat row conversation sendiri
    // lewat POST /conversations — kolom `id` di tabel itu bertipe text tanpa default,
    // jadi insert tanpa id akan gagal (error 23502 "null value in column id").
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
                // SEBELUMNYA: filter conversation_id ditaruh langsung di dalam nama topic
                // ("realtime:public:messages:conversation_id=eq.XYZ"), format lama yang
                // sudah tidak didukung server Supabase Realtime sekarang — jadi join ini
                // sebenarnya selalu gagal secara diam-diam dan tidak pernah menerima update.
                // SEKARANG: topic bebas (unik per percakapan), dan filter-nya ditaruh di
                // payload.config.postgres_changes sesuai protokol yang berlaku saat ini.
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
                            // Upsert: kalau pesan dengan id ini sudah ada (mis. dari optimistic
                            // update di sendMessage), timpa datanya (misalnya saat pesan ditandai
                            // dihapus via realtime UPDATE event); kalau belum ada, tambahkan baru.
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
        val myId = prefs.getUserId() ?: return Result.failure(Exception("Not logged in"))
        return try {
            val response = apiService.getConversations("(user1_id.eq.$myId,user2_id.eq.$myId)")
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!.mapNotNull { dto ->
                    val otherUserId = if (dto.user1_id == myId) dto.user2_id else dto.user1_id
                    val profileResponse = apiService.getProfile("eq.$otherUserId")
                    val otherProfile = if (profileResponse.isSuccessful) {
                        profileResponse.body()?.firstOrNull()
                    } else null

                    val latestMsg = dto.messages.maxByOrNull { it.created_at }
                    // Belum dibaca = pesan yang dikirim LAWAN bicara (bukan diri sendiri)
                    // dan belum ditandai is_read=true (pesan sendiri tidak pernah dihitung
                    // sebagai "belum dibaca" walau is_read-nya masih false di server).
                    val unreadCount = dto.messages.count { it.sender_id != myId && !it.is_read }

                    Conversation(
                        id = dto.id,
                        otherUserId = otherUserId,
                        otherUsername = otherProfile?.username ?: "user_${otherUserId.take(4)}",
                        otherAvatarColor = getAvatarColor(otherProfile?.username),
                        lastMessage = latestMsg?.content,
                        lastMessageTime = latestMsg?.created_at ?: dto.updated_at,
                        unreadCount = unreadCount
                    )
                }
                Result.success(list.sortedByDescending { it.lastMessageTime })
            } else {
                Result.failure(Exception("Failed to load conversations: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMessages(otherUserId: String): Result<List<Message>> {
        val myId = prefs.getUserId() ?: return Result.failure(Exception("Not logged in"))
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

                connectWebSocket(conversationId)

                Result.success(messages)
            } else {
                Result.failure(Exception("Failed to get messages: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Cache in-memory supaya tidak upsert conversation berulang-ulang untuk pasangan
    // user yang sama pada sesi yang sama.
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
            // 2xx sukses (dibuat atau sudah ada & di-ignore lewat "resolution=ignore-duplicates").
            if (response.isSuccessful) {
                ensuredConversations.add(conversationId)
            }
        } catch (_: Exception) {
            // Kalau gagal (mis. offline), biarkan saja — percobaan insert pesan berikutnya
            // akan tetap gagal dengan pesan error yang jelas, dan ensure akan dicoba lagi
            // di panggilan berikutnya karena belum ditambahkan ke cache.
        }
    }

    override suspend fun sendMessage(otherUserId: String, text: String): Result<Unit> {
        val myId = prefs.getUserId() ?: return Result.failure(Exception("Not logged in"))
        return try {
            // conversation_id dikirim dalam format "uuid1_uuid2" (diurutkan). SEBELUMNYA kode
            // ini hanya mengandalkan trigger database untuk membuat baris `conversations`
            // secara otomatis, tapi trigger itu ternyata tidak selalu aktif/ter-apply di semua
            // project Supabase (menyebabkan error RLS 42501 "new row violates row-level
            // security policy" saat insert pesan pertama). Sekarang klien memastikan sendiri
            // baris conversation-nya ada (upsert) sebelum mengirim pesan.
            val conversationId = getConversationId(myId, otherUserId)
            ensureConversationExists(conversationId, myId, otherUserId)

            val body = com.textsocial.app.data.model.SendMessageRequest(
                conversation_id = conversationId,
                sender_id = myId,
                content = text
            )
            val response = apiService.sendMessage(body)
            if (response.isSuccessful) {
                // SEBELUMNYA: setelah kirim sukses, layar cuma diam menunggu WebSocket
                // realtime mendorong balik pesannya — tapi format "join" WebSocket yang
                // dipakai ternyata sudah tidak cocok dengan protokol Supabase Realtime
                // yang sekarang, jadi update itu tidak pernah datang, dan pesan baru
                // muncul kalau chat ditutup-buka lagi (karena getMessages() fetch ulang).
                // SEKARANG: begitu server konfirmasi pesan tersimpan, langsung tambahkan
                // ke layar (optimistic update) tanpa menunggu realtime sama sekali.
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
                    }
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to send message via API: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markMessagesAsRead(otherUserId: String): Result<Unit> {
        val myId = prefs.getUserId() ?: return Result.failure(Exception("Not logged in"))
        return try {
            val conversationId = getConversationId(myId, otherUserId)
            val response = apiService.markMessagesAsRead(
                conversationFilter = "eq.$conversationId",
                senderFilter = "neq.$myId"
            )
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to mark as read: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMessageForEveryone(otherUserId: String, messageId: String): Result<Unit> {
        val myId = prefs.getUserId() ?: return Result.failure(Exception("Not logged in"))
        return try {
            val conversationId = getConversationId(myId, otherUserId)
            val response = apiService.deleteMessageForEveryone(idFilter = "eq.$messageId")
            if (response.isSuccessful) {
                // Update lokal langsung supaya layar berubah seketika, tidak perlu nunggu realtime.
                val flow = _messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
                val currentList = conversationMessages[conversationId] ?: emptyList()
                val updated = currentList.map {
                    if (it.id == messageId) it.copy(text = "Pesan ini telah dihapus", isDeleted = true) else it
                }
                conversationMessages[conversationId] = updated
                flow.value = updated
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete message: ${response.code()}"))
            }
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
    private val prefs: EncryptedPreferencesManager
) : UserRepository {

    override suspend fun getProfile(userId: String): Result<User> {
        return try {
            val resolvedUserId = if (userId == "me_id") {
                prefs.getUserId() ?: return Result.failure(Exception("Not logged in"))
            } else {
                userId
            }
            val response = apiService.getProfile("eq.$resolvedUserId")
            if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                val p = response.body()!!.first()
                val user = User(
                    id = p.id,
                    username = p.username,
                    email = p.email ?: "",
                    displayName = p.display_name ?: p.username.replaceFirstChar { it.uppercase() },
                    bio = p.bio ?: "Welcome to my open-source profile!",
                    avatarColor = getAvatarColor(p.username),
                    isPrivate = p.is_private
                )
                Result.success(user)
            } else {
                Result.failure(Exception("Profile not found: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getProfileByUsername(username: String): Result<User> {
        return try {
            val response = apiService.getProfilesByUsername("eq.${username.lowercase()}")
            if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                val p = response.body()!!.first()
                val user = User(
                    id = p.id,
                    username = p.username,
                    email = p.email ?: "",
                    displayName = p.display_name ?: p.username.replaceFirstChar { it.uppercase() },
                    bio = p.bio ?: "Welcome to my open-source profile!",
                    avatarColor = getAvatarColor(p.username),
                    isPrivate = p.is_private
                )
                Result.success(user)
            } else {
                Result.failure(Exception("Username profile not found: ${response.code()}"))
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
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to update profile: ${response.code()}"))
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
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to follow user: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unfollowUser(targetUserId: String): Result<Unit> {
        return try {
            val myId = prefs.getUserId() ?: ""
            val response = apiService.unfollowUser(followerFilter = "eq.$myId", followingFilter = "eq.$targetUserId")
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to unfollow user: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isFollowing(targetUserId: String): Result<Boolean> {
        return try {
            val myId = prefs.getUserId() ?: return Result.success(false)
            val response = apiService.getFollowers("eq.$targetUserId")
            if (response.isSuccessful) {
                val followingNow = response.body()?.any { it.follower_id == myId } ?: false
                Result.success(followingNow)
            } else {
                Result.failure(Exception("Failed to check follow status: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFollowCounts(userId: String): Result<Pair<Int, Int>> {
        return try {
            val followersResponse = apiService.getFollowers("eq.$userId")
            val followingResponse = apiService.getFollowing("eq.$userId")
            val followersCount = followersResponse.body()?.size ?: 0
            val followingCount = followingResponse.body()?.size ?: 0
            Result.success(followersCount to followingCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Dipakai sebagai daftar "akun terdekat" default untuk autocomplete mention
    // ketika user baru ketik "@" tanpa query apa pun (belum ada huruf sesudahnya).
    override suspend fun getFollowingUsers(): Result<List<User>> {
        return try {
            val myId = prefs.getUserId() ?: return Result.success(emptyList())
            val followingResponse = apiService.getFollowing("eq.$myId")
            if (!followingResponse.isSuccessful) {
                return Result.failure(Exception("Failed to load following: ${followingResponse.code()}"))
            }
            val followingIds = followingResponse.body()?.map { it.following_id } ?: emptyList()
            val users = followingIds.mapNotNull { id ->
                try {
                    val profileResp = apiService.getProfile("eq.$id")
                    profileResp.body()?.firstOrNull()?.let { p ->
                        User(
                            id = p.id,
                            username = p.username,
                            email = p.email ?: "",
                            displayName = p.display_name,
                            bio = p.bio,
                            avatarColor = getAvatarColor(p.username),
                            isPrivate = p.is_private
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            }
            Result.success(users)
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
                        isPrivate = p.is_private
                    )
                }
                Result.success(users)
            } else {
                Result.failure(Exception("Failed to search profiles: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getNotifications(): Result<List<Notification>> {
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
                        isRead = dto.is_read
                    )
                }
                Result.success(notifications)
            } else {
                Result.failure(Exception("Failed to fetch notifications: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markNotificationsAsRead(): Result<Unit> {
        return try {
            val myId = prefs.getUserId() ?: return Result.failure(Exception("Not logged in"))
            val response = apiService.markNotificationsAsRead(recipientFilter = "eq.$myId")
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark notifications as read: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrendingHashtags(): Result<List<Pair<String, Int>>> {
        return try {
            val response = apiService.getPosts()
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
                Result.success(sortedTags)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }
}