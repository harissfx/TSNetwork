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

// 1. SPLASH VIEWMODEL
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

// 2. LOGIN VIEWMODEL
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

// 3. REGISTER VIEWMODEL
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

// 4. HOME VIEWMODEL (FEED WITH PAGINATION)
class HomeViewModel : ViewModel() {
    private val postRepo = ServiceLocator.postRepository

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts = _posts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _activeHashtag = MutableStateFlow<String?>(null)
    val activeHashtag = _activeHashtag.asStateFlow()

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
                _posts.value = result.getOrDefault(emptyList())
            } else {
                _error.value = "Failed to load feed. Swipe down to retry."
            }
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
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = postRepo.deletePost(postId)
            if (result.isSuccess) {
                _posts.update { current -> current.filter { it.id != postId } }
            }
        }
    }
}

// 5. CREATE POST VIEWMODEL
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

// 6. POST DETAIL VIEWMODEL (COMMENTS)
class PostDetailViewModel(private val homeViewModel: HomeViewModel) : ViewModel() {
    private val postRepo = ServiceLocator.postRepository

    private val _post = MutableStateFlow<Post?>(null)
    val post = _post.asStateFlow()

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _commentText = MutableStateFlow("")
    val commentText = _commentText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun setPost(postId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            // Find post from shared HomeViewModel list
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
            // Fetch comments
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

    fun addComment() {
        val currentPost = _post.value ?: return
        val text = _commentText.value
        if (text.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val result = postRepo.createComment(currentPost.id, text)
            if (result.isSuccess) {
                _commentText.value = ""
                // Refresh comments
                val commentsResult = postRepo.getComments(currentPost.id)
                if (commentsResult.isSuccess) {
                    _comments.value = commentsResult.getOrDefault(emptyList())
                }
                // Refresh post comment count
                _post.update { it?.copy(commentsCount = it.commentsCount + 1) }
            }
        }
    }

    fun deleteComment(commentId: String) {
        val currentPost = _post.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = postRepo.deleteComment(commentId)
            if (result.isSuccess) {
                _comments.update { current -> current.filter { it.id != commentId } }
                _post.update { it?.copy(commentsCount = (it.commentsCount - 1).coerceAtLeast(0)) }
            }
        }
    }
}

// 7. STORY VIEWMODEL
class StoryViewModel : ViewModel() {
    private val storyRepo = ServiceLocator.storyRepository

    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories = _stories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _storyText = MutableStateFlow("")
    val storyText = _storyText.asStateFlow()

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
            _stories.value = result.getOrDefault(emptyList())
            _isLoading.value = false
        }
    }

    fun onStoryTextChange(value: String) {
        if (value.length <= 280) {
            _storyText.value = value
        }
    }

    fun createStory() {
        if (_storyText.value.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val result = storyRepo.createStory(_storyText.value)
            _isLoading.value = false
            if (result.isSuccess) {
                _isFinished.value = true
                loadStories()
            }
        }
    }

    fun markStoryAsViewed(storyId: String) {
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

// 8. DM LIST VIEWMODEL
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

// 9. DM CHAT VIEWMODEL
class DMChatViewModel : ViewModel() {
    private val msgRepo = ServiceLocator.messageRepository

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _messageText = MutableStateFlow("")
    val messageText = _messageText.asStateFlow()

    private var targetUserId: String = ""

    fun initChat(otherUserId: String) {
        targetUserId = otherUserId
        viewModelScope.launch(Dispatchers.IO) {
            // Mark read
            msgRepo.markMessagesAsRead(targetUserId)

            // Dynamic flow observation representing WebSockets / Supabase Realtime in a highly responsive manner!
            msgRepo.observeMessages(targetUserId).collect { list ->
                _messages.value = list
            }
        }
    }

    fun onMessageTextChange(value: String) {
        _messageText.value = value
    }

    fun sendMessage() {
        val text = _messageText.value
        if (text.isBlank() || targetUserId.isEmpty()) return
        _messageText.value = ""

        viewModelScope.launch(Dispatchers.IO) {
            msgRepo.sendMessage(targetUserId, text)
        }
    }
}

// 10. NOTIFICATION VIEWMODEL
class NotificationViewModel : ViewModel() {
    private val userRepo = ServiceLocator.userRepository

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications = _notifications.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

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
}

// 11. SEARCH VIEWMODEL
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

// 12. PROFILE VIEWMODEL
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

    fun loadProfile(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val userResult = userRepo.getProfile(userId)
            // getProfile() resolves the "me_id" placeholder to the real UUID internally,
            // so we must use profile.id (not the raw userId param) when matching posts below.
            var resolvedUserId = userId
            if (userResult.isSuccess) {
                val profile = userResult.getOrNull()
                _user.value = profile
                _bioText.value = profile?.bio ?: ""
                _displayNameText.value = profile?.displayName ?: ""
                resolvedUserId = profile?.id ?: userId
            }

            // Load user's own posts
            val postsResult = postRepo.getPosts()
            if (postsResult.isSuccess) {
                _posts.value = postsResult.getOrDefault(emptyList()).filter { it.userId == resolvedUserId }
            }
            _isLoading.value = false
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
                // onSuccess() triggers navigation (popBackStack), which must run on the main thread.
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