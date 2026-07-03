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
            val body = com.textsocial.app.data.model.CreatePostRequest(
                user_id = (prefs.getUserId() ?: ""),
                content = text
            )
            val response = apiService.createPost(body)
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to publish post: ${response.code()}"))
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
                val comments = response.body()!!.map { dto ->
                    val commenterUsername = dto.users?.username ?: "user"
                    Comment(
                        id = dto.id,
                        postId = dto.post_id,
                        userId = dto.user_id,
                        username = commenterUsername,
                        text = dto.content,
                        createdAt = dto.created_at,
                        avatarColor = getAvatarColor(commenterUsername)
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

    override suspend fun createComment(postId: String, text: String): Result<Unit> {
        return try {
            val body = com.textsocial.app.data.model.CreateCommentRequest(
                post_id = postId,
                user_id = (prefs.getUserId() ?: ""),
                content = text
            )
            val response = apiService.createComment(body)
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to create comment: ${response.code()}"))
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
                    Story(
                        id = dto.id,
                        userId = dto.user_id,
                        username = senderUsername,
                        text = dto.content,
                        createdAt = dto.created_at,
                        expiresAt = dto.expires_at,
                        avatarColor = getAvatarColor(senderUsername),
                        views = viewerUsernames
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
    private var webSocket: WebSocket? = null
    private val conversationMessages = ConcurrentHashMap<String, List<Message>>()
    private val _messageFlows = ConcurrentHashMap<String, MutableStateFlow<List<Message>>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun getConversationId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }

    private fun connectWebSocket(conversationId: String) {
        if (webSocket != null) return

        val anonKey = SupabaseClient.SUPABASE_ANON_KEY
        val baseUrl = SupabaseClient.SUPABASE_URL
        if (baseUrl.contains("placeholder-project")) return

        val wsUrl = baseUrl.replace("https://", "wss://").replace("http://", "ws://")
        val wsEndpoint = if (wsUrl.endsWith("/")) "${wsUrl}realtime/v1/websocket?apikey=$anonKey" else "$wsUrl/realtime/v1/websocket?apikey=$anonKey"

        val request = Request.Builder().url(wsEndpoint).build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val joinMsg = "{\"topic\":\"realtime:public:messages:conversation_id=eq.$conversationId\",\"event\":\"phx_join\",\"payload\":{},\"ref\":\"1\"}"
                webSocket.send(joinMsg)

                scope.launch {
                    var ref = 2
                    while (this@MessageRepositoryImpl.webSocket != null) {
                        delay(25000)
                        try {
                            this@MessageRepositoryImpl.webSocket?.send("{\"topic\":\"phoenix\",\"event\":\"heartbeat\",\"payload\":{},\"ref\":\"${ref++}\"}")
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

                            val newMessage = Message(
                                id = id,
                                conversationId = convId,
                                senderId = senderId,
                                text = textVal,
                                createdAt = createdAt,
                                isRead = isRead
                            )

                            val flow = _messageFlows.getOrPut(convId) { MutableStateFlow(emptyList()) }
                            val currentList = conversationMessages[convId] ?: emptyList()
                            if (currentList.none { it.id == newMessage.id }) {
                                val updated = currentList + newMessage
                                conversationMessages[convId] = updated
                                flow.value = updated
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                this@MessageRepositoryImpl.webSocket = null
                scope.launch {
                    delay(5000)
                    connectWebSocket(conversationId)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                this@MessageRepositoryImpl.webSocket = null
            }
        })
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

                    Conversation(
                        id = dto.id,
                        otherUserId = otherUserId,
                        otherUsername = otherProfile?.username ?: "user_${otherUserId.take(4)}",
                        otherAvatarColor = getAvatarColor(otherProfile?.username),
                        lastMessage = latestMsg?.content,
                        lastMessageTime = latestMsg?.created_at ?: dto.updated_at
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
        val conversationId = getConversationId(myId, otherUserId)
        return try {
            val response = apiService.getMessages("eq.$conversationId")
            if (response.isSuccessful && response.body() != null) {
                val messages = response.body()!!.map { dto ->
                    Message(
                        id = dto.id,
                        conversationId = dto.conversation_id,
                        senderId = dto.sender_id,
                        text = dto.content,
                        createdAt = dto.created_at,
                        isRead = dto.is_read
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

    override suspend fun sendMessage(otherUserId: String, text: String): Result<Unit> {
        val myId = prefs.getUserId() ?: return Result.failure(Exception("Not logged in"))
        val conversationId = getConversationId(myId, otherUserId)
        return try {
            val body = com.textsocial.app.data.model.SendMessageRequest(
                conversation_id = conversationId,
                sender_id = myId,
                content = text
            )
            val response = apiService.sendMessage(body)
            if (response.isSuccessful) {
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
        val conversationId = getConversationId(myId, otherUserId)
        return try {
            val response = apiService.markMessagesAsRead(
                conversationFilter = "eq.$conversationId",
                senderFilter = "neq.$myId"
            )
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to mark as read: ${response.code()}"))
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
                    val typeText = when (dto.type) {
                        "like" -> "liked your post"
                        "comment" -> "commented on your post"
                        "follow" -> "started following you"
                        else -> "sent you a notification"
                    }
                    Notification(
                        id = dto.id,
                        type = dto.type,
                        senderUsername = senderUsername,
                        senderAvatarColor = senderAvatarColor,
                        text = typeText,
                        createdAt = dto.created_at
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
