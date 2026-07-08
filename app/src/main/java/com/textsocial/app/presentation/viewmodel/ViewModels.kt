package com.textsocial.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.textsocial.app.di.ServiceLocator
import com.textsocial.app.domain.model.*
import com.textsocial.app.domain.repository.*
import com.textsocial.app.data.repository.SupabaseAuthException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class MainTabViewModel : ViewModel() {
    private val _currentTab = MutableStateFlow(0)
    val currentTab = _currentTab.asStateFlow()

    fun goToTab(index: Int) {
        _currentTab.value = index
    }
}

class BadgeViewModel : ViewModel() {
    private val msgRepo = ServiceLocator.messageRepository
    private val userRepo = ServiceLocator.userRepository

    private val _unreadMessages = MutableStateFlow(0)
    val unreadMessages = _unreadMessages.asStateFlow()

    private val _unreadNotifications = MutableStateFlow(0)
    val unreadNotifications = _unreadNotifications.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        refreshUnreadMessages()
        refreshUnreadNotifications()
    }

    fun refreshUnreadMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = ServiceLocator.messageRepository.getConversations()
            _unreadMessages.value = result.getOrDefault(emptyList()).sumOf { it.unreadCount }
        }
    }

    fun refreshUnreadNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = userRepo.getNotifications()
            _unreadNotifications.value = result.getOrDefault(emptyList()).count { !it.isRead }
        }
    }

    fun decrementUnreadNotifications() {
        if (_unreadNotifications.value > 0) {
            _unreadNotifications.value -= 1
        }
    }

    fun clearUnreadNotifications() {
        _unreadNotifications.value = 0
    }
}

class SplashViewModel : ViewModel() {
    private val authRepo = ServiceLocator.authRepository
    private val _isUserLoggedIn = MutableStateFlow<Boolean?>(null)
    val isUserLoggedIn = _isUserLoggedIn.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(400)
            val user = authRepo.getSessionUser()
            _isUserLoggedIn.value = (user != null)
        }
    }
}

class LoginViewModel : ViewModel() {
    private val authRepo = ServiceLocator.authRepository

    private val _usernameOrEmail = MutableStateFlow("")
    val usernameOrEmail = _usernameOrEmail.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess = _loginSuccess.asStateFlow()

    fun onUsernameOrEmailChange(value: String) {
        _usernameOrEmail.value = value
    }

    fun onPasswordChange(value: String) {
        _password.value = value
    }

    fun login() {
        if (_usernameOrEmail.value.isBlank() || _password.value.isBlank()) {
            _error.value = "Username/Email and Password cannot be blank"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            val result = authRepo.login(_usernameOrEmail.value, _password.value)
            _isLoading.value = false
            if (result.isSuccess) {
                _loginSuccess.value = true
            } else {
                val exception = result.exceptionOrNull()
                if (exception is SupabaseAuthException) {
                    _error.value = when {
                        exception.code == 400 && exception.errorMsg.contains("Invalid login credentials", ignoreCase = true) -> "Incorrect email or password."
                        exception.code == 429 -> "Too many attempts. Please wait a few minutes."
                        else -> "Login failed: ${exception.errorMsg}"
                    }
                } else {
                    _error.value = "Login failed. Please try again."
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

class RegisterViewModel : ViewModel() {
    private val authRepo = ServiceLocator.authRepository

    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword = _confirmPassword.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _registerSuccess = MutableStateFlow(false)
    val registerSuccess = _registerSuccess.asStateFlow()

    fun onUsernameChange(value: String) {
        _username.value = value
    }

    fun onEmailChange(value: String) {
        _email.value = value
    }

    fun onPasswordChange(value: String) {
        _password.value = value
    }

    fun onConfirmPasswordChange(value: String) {
        _confirmPassword.value = value
    }

    fun register() {
        if (_username.value.isBlank() || _email.value.isBlank() || _password.value.isBlank()) {
            _error.value = "All fields are required"
            return
        }
        if (_password.value != _confirmPassword.value) {
            _error.value = "Passwords do not match"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            val result = authRepo.register(_username.value, _email.value, _password.value)
            _isLoading.value = false
            if (result.isSuccess) {
                _registerSuccess.value = true
            } else {
                val exception = result.exceptionOrNull()
                if (exception is SupabaseAuthException) {
                    _error.value = when {
                        exception.code == 429 -> "Too many attempts. Please wait a few minutes before trying again."
                        exception.code == 400 && exception.errorMsg.contains("User already registered", ignoreCase = true) -> "An account with this email already exists."
                        else -> "Registration failed: ${exception.errorMsg}"
                    }
                } else {
                    val msg = exception?.message
                    if (msg != null && (msg.contains("Username") || msg.contains("Password"))) {
                        _error.value = msg
                    } else {
                        _error.value = "Registration failed. Please try again."
                    }
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

enum class FeedMode { FOR_YOU, LATEST }

class HomeViewModel : ViewModel() {
    private val postRepo = ServiceLocator.postRepository

    private val _rawPosts = MutableStateFlow<List<Post>>(emptyList())

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts = _posts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _activeHashtag = MutableStateFlow<String?>(null)
    val activeHashtag = _activeHashtag.asStateFlow()

    private val _feedMode = MutableStateFlow(FeedMode.FOR_YOU)
    val feedMode = _feedMode.asStateFlow()

    init {
        loadPosts()
    }

    fun loadPosts(hashtag: String? = null) {
        _activeHashtag.value = hashtag
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            val result = postRepo.getPosts(hashtag)
            _isLoading.value = false
            if (result.isSuccess) {
                _rawPosts.value = result.getOrDefault(emptyList())
                _posts.value = sortForMode(_rawPosts.value, _feedMode.value)
            } else {
                _error.value = "Failed to load feed. Swipe down to retry."
            }
        }
    }

    fun setFeedMode(mode: FeedMode) {
        if (_feedMode.value == mode) return
        _feedMode.value = mode
        _posts.value = sortForMode(_rawPosts.value, mode)
    }

    private fun engagementScore(post: Post): Double {
        val ageHours = com.textsocial.app.util.TimeUtils.hoursSince(post.createdAt)
        val engagement = (post.likesCount * 1.5) + (post.commentsCount * 3.0) + 1.0
        return engagement / Math.pow(ageHours + 2.0, 1.5)
    }

    private fun sortForMode(list: List<Post>, mode: FeedMode): List<Post> {
        return when (mode) {
            FeedMode.LATEST -> list.sortedByDescending {
                com.textsocial.app.util.TimeUtils.parseToEpochMillis(it.createdAt) ?: 0L
            }
            FeedMode.FOR_YOU -> list.sortedByDescending { engagementScore(it) }
        }
    }

    fun toggleLike(post: Post) {
        viewModelScope.launch(Dispatchers.IO) {
            if (post.isLiked) {
                postRepo.unlikePost(post.id)
                val updated = { list: List<Post> ->
                    list.map {
                        if (it.id == post.id) it.copy(likesCount = (it.likesCount - 1).coerceAtLeast(0), isLiked = false)
                        else it
                    }
                }
                _rawPosts.update(updated)
                _posts.update(updated)
            } else {
                postRepo.likePost(post.id)
                val updated = { list: List<Post> ->
                    list.map {
                        if (it.id == post.id) it.copy(likesCount = it.likesCount + 1, isLiked = true)
                        else it
                    }
                }
                _rawPosts.update(updated)
                _posts.update(updated)
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = postRepo.deletePost(postId)
            if (result.isSuccess) {
                _rawPosts.update { current -> current.filter { it.id != postId } }
                _posts.update { current -> current.filter { it.id != postId } }
            }
        }
    }
}

class CreatePostViewModel : ViewModel() {
    private val postRepo = ServiceLocator.postRepository

    private val _text = MutableStateFlow("")
    val text = _text.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isFinished = MutableStateFlow(false)
    val isFinished = _isFinished.asStateFlow()

    fun onTextChange(value: String) {
        if (value.length <= 500) {
            _text.value = value
        }
    }

    fun createPost() {
        if (_text.value.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            val result = postRepo.createPost(_text.value)
            _isLoading.value = false
            if (result.isSuccess) {
                _isFinished.value = true
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to publish post"
            }
        }
    }
}

class PostDetailViewModel(private val homeViewModel: HomeViewModel) : ViewModel() {
    private val postRepo = ServiceLocator.postRepository

    private val _post = MutableStateFlow<Post?>(null)
    val post = _post.asStateFlow()

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _commentText = MutableStateFlow("")
    val commentText = _commentText.asStateFlow()

    private val _replyingTo = MutableStateFlow<Comment?>(null)
    val replyingTo = _replyingTo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun setPost(postId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val found = homeViewModel.posts.value.find { it.id == postId }
            if (found != null) {
                _post.value = found
            } else {
                val postsResult = postRepo.getPosts()
                if (postsResult.isSuccess) {
                    val foundFallback = postsResult.getOrNull()?.find { it.id == postId }
                    _post.value = foundFallback
                }
            }
            val commentsResult = postRepo.getComments(postId)
            if (commentsResult.isSuccess) {
                _comments.value = commentsResult.getOrDefault(emptyList())
            }
            _isLoading.value = false
        }
    }

    fun onCommentTextChange(value: String) {
        _commentText.value = value
    }

    fun startReplyTo(comment: Comment) {
        _replyingTo.value = comment
    }

    fun cancelReply() {
        _replyingTo.value = null
    }

    fun addComment() {
        val currentPost = _post.value ?: return
        val text = _commentText.value
        if (text.isBlank()) return
        val parentId = _replyingTo.value?.id

        viewModelScope.launch(Dispatchers.IO) {
            val result = postRepo.createComment(currentPost.id, text, parentId)
            if (result.isSuccess) {
                _commentText.value = ""
                _replyingTo.value = null
                val commentsResult = postRepo.getComments(currentPost.id)
                if (commentsResult.isSuccess) {
                    _comments.value = commentsResult.getOrDefault(emptyList())
                }
                _post.update { it?.copy(commentsCount = it.commentsCount + 1) }
            }
        }
    }

    fun toggleCommentLike(comment: Comment) {
        viewModelScope.launch(Dispatchers.IO) {
            if (comment.isLiked) {
                postRepo.unlikeComment(comment.id)
                _comments.update { current ->
                    current.map {
                        if (it.id == comment.id) it.copy(likesCount = (it.likesCount - 1).coerceAtLeast(0), isLiked = false)
                        else it
                    }
                }
            } else {
                postRepo.likeComment(comment.id)
                _comments.update { current ->
                    current.map {
                        if (it.id == comment.id) it.copy(likesCount = it.likesCount + 1, isLiked = true)
                        else it
                    }
                }
            }
        }
    }

    fun deleteComment(commentId: String) {
        val currentPost = _post.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = postRepo.deleteComment(commentId)
            if (result.isSuccess) {
                val current = _comments.value
                val idsToRemove = mutableSetOf(commentId)
                var changed = true
                while (changed) {
                    changed = false
                    for (c in current) {
                        if (c.parentId != null && idsToRemove.contains(c.parentId) && idsToRemove.add(c.id)) {
                            changed = true
                        }
                    }
                }
                _comments.value = current.filter { it.id !in idsToRemove }
                _post.update { it?.copy(commentsCount = (it.commentsCount - idsToRemove.size).coerceAtLeast(0)) }
            }
        }
    }
}

class StoryViewModel : ViewModel() {
    private val storyRepo = ServiceLocator.storyRepository
    private val userRepo = ServiceLocator.userRepository

    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories = _stories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _storyText = MutableStateFlow("")
    val storyText = _storyText.asStateFlow()

    private val _storyBackgroundColor = MutableStateFlow("#000000")
    val storyBackgroundColor = _storyBackgroundColor.asStateFlow()

    private val _storyTextColor = MutableStateFlow("#FFFFFF")
    val storyTextColor = _storyTextColor.asStateFlow()

    private val _storyFontFamily = MutableStateFlow("default")
    val storyFontFamily = _storyFontFamily.asStateFlow()

    fun onStoryBackgroundColorChange(hex: String) {
        _storyBackgroundColor.value = hex
    }

    fun onStoryTextColorChange(hex: String) {
        _storyTextColor.value = hex
    }

    fun onStoryFontFamilyChange(key: String) {
        _storyFontFamily.value = key
    }

    private val _isFinished = MutableStateFlow(false)
    val isFinished = _isFinished.asStateFlow()

    private val _selectedStoryIndex = MutableStateFlow(0)
    val selectedStoryIndex = _selectedStoryIndex.asStateFlow()

    fun setSelectedStoryIndex(index: Int) {
        _selectedStoryIndex.value = index
    }

    init {
        loadStories()
    }

    fun loadStories() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val result = storyRepo.getStories()
            val rawStories = result.getOrDefault(emptyList())
            val myId = ServiceLocator.encryptedPreferencesManager.getUserId()
            val followingIds = userRepo.getFollowingUsers().getOrDefault(emptyList()).map { it.id }.toSet()
            val visibleStories = rawStories.filter { it.userId == myId || followingIds.contains(it.userId) }
            val grouped = visibleStories
                .groupBy { it.userId }
                .values
                .map { userStories -> userStories.sortedBy { it.createdAt } }
                .sortedByDescending { userGroup -> userGroup.maxOf { it.createdAt } }
                .flatten()
            _stories.value = grouped
            _isLoading.value = false
        }
    }

    fun onStoryTextChange(value: String) {
        if (value.length <= 280) {
            _storyText.value = value
        }
    }

    fun resetComposerState() {
        _storyText.value = ""
        _isFinished.value = false
        _storyBackgroundColor.value = "#000000"
        _storyTextColor.value = "#FFFFFF"
        _storyFontFamily.value = "default"
    }

    fun createStory() {
        if (_storyText.value.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val result = storyRepo.createStory(
                text = _storyText.value,
                backgroundColor = _storyBackgroundColor.value,
                textColor = _storyTextColor.value,
                fontFamily = _storyFontFamily.value
            )
            _isLoading.value = false
            if (result.isSuccess) {
                _isFinished.value = true
                loadStories()
            }
        }
    }

    fun markStoryAsViewed(storyId: String, storyOwnerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storyRepo.recordStoryView(storyId)
            loadStories()
        }
    }

    fun deleteStory(storyId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = storyRepo.deleteStory(storyId)
            if (result.isSuccess) {
                _stories.update { current -> current.filter { it.id != storyId } }
            }
        }
    }
}

class DMListViewModel : ViewModel() {
    private val msgRepo = ServiceLocator.messageRepository

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations = _conversations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val result = msgRepo.getConversations()
            _conversations.value = result.getOrDefault(emptyList())
            _isLoading.value = false
        }
    }
}

class DMChatViewModel : ViewModel() {
    private val msgRepo = ServiceLocator.messageRepository
    private val userRepo = ServiceLocator.userRepository

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _messageText = MutableStateFlow("")
    val messageText = _messageText.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError = _sendError.asStateFlow()

    private val _otherIsVerified = MutableStateFlow(false)
    val otherIsVerified = _otherIsVerified.asStateFlow()

    private val _otherAvatarUrl = MutableStateFlow<String?>(null)
    val otherAvatarUrl = _otherAvatarUrl.asStateFlow()

    private var targetUserId: String = ""

    fun initChat(otherUserId: String, onMessagesRead: () -> Unit = {}) {
        targetUserId = otherUserId

        viewModelScope.launch(Dispatchers.IO) {
            val profileResult = userRepo.getProfile(otherUserId)
            val profile = profileResult.getOrNull()
            _otherIsVerified.value = profile?.isVerified ?: false
            _otherAvatarUrl.value = profile?.avatarUrl
        }

        viewModelScope.launch(Dispatchers.IO) {

            val result = msgRepo.getMessages(targetUserId)
            if (result.isSuccess) {
                _messages.value = result.getOrDefault(emptyList())
            }

            msgRepo.markMessagesAsRead(targetUserId)
            onMessagesRead()

            msgRepo.observeMessages(targetUserId).collect { list ->
                _messages.value = list
            }
        }
    }

    fun onMessageTextChange(value: String) {
        _messageText.value = value
        if (value.isNotBlank()) _sendError.value = null
    }

    fun sendMessage() {
        val text = _messageText.value
        if (text.isBlank() || targetUserId.isEmpty()) return
        _messageText.value = ""
        _sendError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val result = msgRepo.sendMessage(targetUserId, text)
            if (result.isFailure) {
                _messageText.value = text
                _sendError.value = "Pesan gagal terkirim. Coba lagi."
            }
        }
    }

    fun dismissSendError() {
        _sendError.value = null
    }

    fun deleteMessage(messageId: String) {
        if (targetUserId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            msgRepo.deleteMessageForEveryone(targetUserId, messageId)
        }
    }
}

class NotificationViewModel : ViewModel() {
    private val userRepo = ServiceLocator.userRepository

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications = _notifications.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode = _isSelectMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val result = userRepo.getNotifications()
            _notifications.value = result.getOrDefault(emptyList())
            _isLoading.value = false
        }
    }

    fun markAsRead(notification: Notification, onMarked: () -> Unit = {}) {
        if (notification.isRead) return
        _notifications.value = _notifications.value.map {
            if (it.id == notification.id) it.copy(isRead = true) else it
        }
        onMarked()
        viewModelScope.launch(Dispatchers.IO) {
            userRepo.markNotificationAsRead(notification.id)
        }
    }

    fun markAllAsRead(onMarked: () -> Unit = {}) {
        if (_notifications.value.none { !it.isRead }) return
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        onMarked()
        viewModelScope.launch(Dispatchers.IO) {
            userRepo.markNotificationsAsRead()
        }
    }

    fun deleteNotification(notificationId: String) {
        _notifications.update { list -> list.filterNot { it.id == notificationId } }
        viewModelScope.launch(Dispatchers.IO) {
            userRepo.deleteNotification(notificationId)
        }
    }

    fun toggleSelectMode() {
        _isSelectMode.update { !it }
        if (!_isSelectMode.value) _selectedIds.value = emptySet()
    }

    fun enterSelectModeWith(notificationId: String) {
        _isSelectMode.value = true
        _selectedIds.value = setOf(notificationId)
    }

    fun toggleSelected(notificationId: String) {
        _selectedIds.update { current ->
            if (current.contains(notificationId)) current - notificationId else current + notificationId
        }
    }

    fun selectAll() {
        _selectedIds.value = _notifications.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        _notifications.update { list -> list.filterNot { ids.contains(it.id) } }
        _selectedIds.value = emptySet()
        _isSelectMode.value = false
        viewModelScope.launch(Dispatchers.IO) {
            userRepo.deleteNotifications(ids.toList())
        }
    }
}

class SearchViewModel : ViewModel() {
    private val userRepo = ServiceLocator.userRepository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _trendingHashtags = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val trendingHashtags = _trendingHashtags.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadTrending()
    }

    private fun loadTrending() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = userRepo.getTrendingHashtags()
            _trendingHashtags.value = result.getOrDefault(emptyList())
        }
    }

    fun onQueryChange(value: String) {
        _searchQuery.value = value
        if (value.isBlank()) {
            _searchResults.value = emptyList()
        } else {
            searchUsers(value)
        }
    }

    private fun searchUsers(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val result = userRepo.searchUsers(query)
            _searchResults.value = result.getOrDefault(emptyList())
            _isLoading.value = false
        }
    }
}

class ProfileViewModel(private val homeViewModel: HomeViewModel) : ViewModel() {
    private val userRepo = ServiceLocator.userRepository
    private val postRepo = ServiceLocator.postRepository

    private val _user = MutableStateFlow<User?>(null)
    val user = _user.asStateFlow()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts = _posts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _bioText = MutableStateFlow("")
    val bioText = _bioText.asStateFlow()

    private val _displayNameText = MutableStateFlow("")
    val displayNameText = _displayNameText.asStateFlow()

    private val _usernameText = MutableStateFlow("")
    val usernameText = _usernameText.asStateFlow()

    private val _isUsernameSaving = MutableStateFlow(false)
    val isUsernameSaving = _isUsernameSaving.asStateFlow()

    private val _usernameError = MutableStateFlow<String?>(null)
    val usernameError = _usernameError.asStateFlow()

    private val _usernameSaveSuccess = MutableStateFlow(false)
    val usernameSaveSuccess = _usernameSaveSuccess.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing = _isFollowing.asStateFlow()

    private val _followsMe = MutableStateFlow(false)
    val followsMe = _followsMe.asStateFlow()

    private val _isFollowActionLoading = MutableStateFlow(false)
    val isFollowActionLoading = _isFollowActionLoading.asStateFlow()

    private val _isAvatarUploading = MutableStateFlow(false)
    val isAvatarUploading = _isAvatarUploading.asStateFlow()

    private val _avatarUploadError = MutableStateFlow<String?>(null)
    val avatarUploadError = _avatarUploadError.asStateFlow()
    fun uploadAvatar(imageBytes: ByteArray) {
        if (_isAvatarUploading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isAvatarUploading.value = true
            _avatarUploadError.value = null
            val result = userRepo.uploadAvatar(imageBytes)
            if (result.isSuccess) {
                val newUrl = result.getOrNull()
                _user.update { it?.copy(avatarUrl = newUrl) }
            } else {
                _avatarUploadError.value = "Gagal mengunggah foto profil. Coba lagi."
            }
            _isAvatarUploading.value = false
        }
    }

    fun loadProfile(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val userResult = userRepo.getProfile(userId)
            var resolvedUserId = userId
            if (userResult.isSuccess) {
                var profile = userResult.getOrNull()
                resolvedUserId = profile?.id ?: userId

                val countsResult = userRepo.getFollowCounts(resolvedUserId)
                if (countsResult.isSuccess) {
                    val (followers, following) = countsResult.getOrDefault(0 to 0)
                    profile = profile?.copy(followersCount = followers, followingCount = following)
                }

                _user.value = profile
                _bioText.value = profile?.bio ?: ""
                _displayNameText.value = profile?.displayName ?: ""
                _usernameText.value = profile?.username ?: ""

                val myId = com.textsocial.app.di.ServiceLocator.encryptedPreferencesManager.getUserId()
                if (myId != null && resolvedUserId != myId) {
                    val followingResult = userRepo.isFollowing(resolvedUserId)
                    _isFollowing.value = followingResult.getOrDefault(false)
                    val followsMeResult = userRepo.isFollowedBy(resolvedUserId)
                    _followsMe.value = followsMeResult.getOrDefault(false)
                } else {
                    _isFollowing.value = false
                    _followsMe.value = false
                }
            }

            val postsResult = postRepo.getPosts()
            if (postsResult.isSuccess) {
                val userPosts = postsResult.getOrDefault(emptyList()).filter { it.userId == resolvedUserId }
                _posts.value = userPosts
                _user.update { it?.copy(postsCount = userPosts.size) }
            }
            _isLoading.value = false
        }
    }

    fun toggleLike(post: Post) {
        viewModelScope.launch(Dispatchers.IO) {
            if (post.isLiked) {
                postRepo.unlikePost(post.id)
                _posts.update { current ->
                    current.map {
                        if (it.id == post.id) it.copy(likesCount = (it.likesCount - 1).coerceAtLeast(0), isLiked = false)
                        else it
                    }
                }
            } else {
                postRepo.likePost(post.id)
                _posts.update { current ->
                    current.map {
                        if (it.id == post.id) it.copy(likesCount = it.likesCount + 1, isLiked = true)
                        else it
                    }
                }
            }
            homeViewModel.loadPosts()
        }
    }

    fun toggleFollow() {
        val profile = _user.value ?: return
        if (_isFollowActionLoading.value) return
        val wasFollowing = _isFollowing.value
        viewModelScope.launch(Dispatchers.IO) {
            _isFollowActionLoading.value = true
            val result = if (wasFollowing) {
                userRepo.unfollowUser(profile.id)
            } else {
                userRepo.followUser(profile.id)
            }
            if (result.isSuccess) {
                _isFollowing.value = !wasFollowing
                val delta = if (wasFollowing) -1 else 1
                _user.update { it?.copy(followersCount = (it.followersCount + delta).coerceAtLeast(0)) }
            }
            _isFollowActionLoading.value = false
        }
    }

    fun updateProfile(onSuccess: () -> Unit) {
        val current = _user.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val result = userRepo.updateProfile(_displayNameText.value, _bioText.value, current.isPrivate)
            _isLoading.value = false
            if (result.isSuccess) {
                _user.value = current.copy(displayName = _displayNameText.value, bio = _bioText.value)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            }
        }
    }

    fun onBioChange(value: String) {
        _bioText.value = value
    }

    fun onDisplayNameChange(value: String) {
        _displayNameText.value = value
    }

    fun onUsernameChange(value: String) {
        _usernameText.value = value
        _usernameError.value = null
        _usernameSaveSuccess.value = false
    }

    fun updateUsername() {
        if (_isUsernameSaving.value) return
        val current = _user.value ?: return
        val newUsername = _usernameText.value.trim()
        if (newUsername.equals(current.username, ignoreCase = true)) return

        viewModelScope.launch(Dispatchers.IO) {
            _isUsernameSaving.value = true
            _usernameError.value = null
            val result = userRepo.updateUsername(newUsername)
            if (result.isSuccess) {
                _user.value = current.copy(username = newUsername.lowercase())
                _usernameText.value = newUsername.lowercase()
                _usernameSaveSuccess.value = true
            } else {
                _usernameError.value = result.exceptionOrNull()?.message ?: "Gagal mengubah username"
            }
            _isUsernameSaving.value = false
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = postRepo.deletePost(postId)
            if (result.isSuccess) {
                _posts.value = _posts.value.filter { it.id != postId }
                homeViewModel.loadPosts()
            }
        }
    }
}

class FollowListViewModel : ViewModel() {
    private val userRepo = ServiceLocator.userRepository
    private val prefs = ServiceLocator.encryptedPreferencesManager

    private val _followers = MutableStateFlow<List<FollowListEntry>>(emptyList())
    val followers = _followers.asStateFlow()

    private val _following = MutableStateFlow<List<FollowListEntry>>(emptyList())
    val following = _following.asStateFlow()

    private val _isLoadingFollowers = MutableStateFlow(false)
    val isLoadingFollowers = _isLoadingFollowers.asStateFlow()

    private val _isLoadingFollowing = MutableStateFlow(false)
    val isLoadingFollowing = _isLoadingFollowing.asStateFlow()

    private val _isFollowingListHidden = MutableStateFlow(false)
    val isFollowingListHidden = _isFollowingListHidden.asStateFlow()

    private val _followActionLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    val followActionLoadingIds = _followActionLoadingIds.asStateFlow()

    private var targetUserId: String = ""

    fun load(targetUserId: String) {
        this.targetUserId = targetUserId
        val myId = prefs.getUserId()
        val isOwnProfile = myId != null && myId == targetUserId

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingFollowers.value = true
            _isLoadingFollowing.value = true

            val myFollowingIds = if (myId != null) {
                userRepo.getFollowingUsers().getOrDefault(emptyList()).map { it.id }.toSet()
            } else emptySet()
            val myFollowerIds = if (myId != null) {
                userRepo.getFollowersOf(myId).getOrDefault(emptyList()).map { it.id }.toSet()
            } else emptySet()

            val targetProfile = userRepo.getProfile(targetUserId).getOrNull()
            _isFollowingListHidden.value = !isOwnProfile && (targetProfile?.hideFollowingList == true)

            val followersResult = userRepo.getFollowersOf(targetUserId)
            _followers.value = followersResult.getOrDefault(emptyList()).map { u ->
                FollowListEntry(
                    user = u,
                    isFollowedByMe = myFollowingIds.contains(u.id),
                    followsMe = myFollowerIds.contains(u.id)
                )
            }
            _isLoadingFollowers.value = false

            if (!_isFollowingListHidden.value) {
                val followingResult = userRepo.getFollowingOf(targetUserId)
                _following.value = followingResult.getOrDefault(emptyList()).map { u ->
                    FollowListEntry(
                        user = u,
                        isFollowedByMe = myFollowingIds.contains(u.id),
                        followsMe = myFollowerIds.contains(u.id)
                    )
                }
            } else {
                _following.value = emptyList()
            }
            _isLoadingFollowing.value = false
        }
    }

    fun toggleFollow(entry: FollowListEntry) {
        if (_followActionLoadingIds.value.contains(entry.user.id)) return
        viewModelScope.launch(Dispatchers.IO) {
            _followActionLoadingIds.update { it + entry.user.id }
            val result = if (entry.isFollowedByMe) {
                userRepo.unfollowUser(entry.user.id)
            } else {
                userRepo.followUser(entry.user.id)
            }
            if (result.isSuccess) {
                val newValue = !entry.isFollowedByMe
                fun updateList(list: List<FollowListEntry>) = list.map {
                    if (it.user.id == entry.user.id) it.copy(isFollowedByMe = newValue) else it
                }
                _followers.value = updateList(_followers.value)
                _following.value = updateList(_following.value)
            }
            _followActionLoadingIds.update { it - entry.user.id }
        }
    }
}