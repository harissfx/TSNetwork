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
        viewModelScope.launch {
            com.textsocial.app.util.NotificationEventBus.events.collect { type ->
                if (type == "dm") refreshUnreadMessages() else refreshUnreadNotifications()
            }
        }
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
                com.textsocial.app.util.PushNotificationManager.registerCurrentDeviceToken()
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
                com.textsocial.app.util.PushNotificationManager.registerCurrentDeviceToken()
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

    // Loading indicator KHUSUS untuk "ambil halaman berikutnya" (infinite scroll),
    // dipisah dari _isLoading (yang dipakai untuk refresh/load awal) supaya UI bisa
    // menampilkan spinner kecil di bawah list tanpa mengganggu list yang sudah ada.
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    // Masih ada halaman berikutnya di server atau tidak -- dipakai buat berhenti
    // memicu loadMorePosts() begitu sudah mentok ke post paling lama.
    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _activeHashtag = MutableStateFlow<String?>(null)
    val activeHashtag = _activeHashtag.asStateFlow()

    private val _feedMode = MutableStateFlow(FeedMode.FOR_YOU)
    val feedMode = _feedMode.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()

    fun clearActionError() {
        _actionError.value = null
    }

    fun reportActionError(message: String) {
        _actionError.value = message
    }

    fun insertOptimisticPost(post: Post) {
        _rawPosts.update { listOf(post) + it }
        _posts.update { listOf(post) + it }
    }

    fun removePostById(postId: String) {
        _rawPosts.update { current -> current.filterNot { it.id == postId } }
        _posts.update { current -> current.filterNot { it.id == postId } }
    }

    fun applyLikeStateToPost(postId: String, isLiked: Boolean, likesCount: Int) {
        fun apply(list: List<Post>): List<Post> = list.map {
            if (it.id == postId) it.copy(isLiked = isLiked, likesCount = likesCount) else it
        }
        _rawPosts.update(::apply)
        _posts.update(::apply)
    }

    fun restorePost(post: Post, atIndex: Int) {
        fun reinsert(list: List<Post>): List<Post> {
            if (list.any { it.id == post.id }) return list
            val idx = atIndex.coerceIn(0, list.size)
            return list.toMutableList().apply { add(idx, post) }
        }
        _rawPosts.update(::reinsert)
        _posts.update(::reinsert)
    }

    init {
        loadPosts()
    }

    fun loadPosts(hashtag: String? = null) {
        _activeHashtag.value = hashtag
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            val result = postRepo.getPosts(hashtag = hashtag)
            _isLoading.value = false
            if (result.isSuccess) {
                val page = result.getOrThrow()
                _rawPosts.value = page.posts
                _posts.value = sortForMode(_rawPosts.value, _feedMode.value)
                _hasMore.value = page.hasMore
            } else {
                _error.value = "Failed to load feed. Swipe down to retry."
            }
        }
    }

    /**
     * Ambil halaman post berikutnya (dipanggil dari UI saat scroll mendekati akhir list)
     * dan sambung ke list yang sudah ada, alih-alih menimpanya. Cursor-nya adalah
     * created_at dari post PALING LAMA yang sudah kita punya sekarang.
     */
    fun loadMorePosts() {
        if (_isLoadingMore.value || _isLoading.value || !_hasMore.value) return
        val cursor = _rawPosts.value.lastOrNull()?.createdAt ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val result = postRepo.getPosts(hashtag = _activeHashtag.value, before = cursor)
            _isLoadingMore.value = false
            if (result.isSuccess) {
                val page = result.getOrThrow()
                val existingIds = _rawPosts.value.map { it.id }.toSet()
                val newPosts = page.posts.filterNot { it.id in existingIds }
                _rawPosts.value = _rawPosts.value + newPosts
                _posts.value = sortForMode(_rawPosts.value, _feedMode.value)
                _hasMore.value = page.hasMore
            }
            // Gagal load-more secara diam-diam tidak apa-apa -- list yang sudah ada tetap
            // tampil, user tinggal scroll lagi untuk retry (tidak perlu error besar-besar).
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
        if (!com.textsocial.app.util.RateLimiter.tryAcquire(
                "like_post:${post.id}",
                com.textsocial.app.util.RateLimiter.COOLDOWN_LIKE_MS
            )
        ) {
            return
        }

        val wasLiked = post.isLiked
        val previousCount = post.likesCount

        fun applyLocalState(list: List<Post>): List<Post> = list.map {
            if (it.id == post.id) {
                if (wasLiked) it.copy(likesCount = (previousCount - 1).coerceAtLeast(0), isLiked = false)
                else it.copy(likesCount = previousCount + 1, isLiked = true)
            } else it
        }
        _rawPosts.update(::applyLocalState)
        _posts.update(::applyLocalState)

        viewModelScope.launch(Dispatchers.IO) {
            val result = if (wasLiked) postRepo.unlikePost(post.id) else postRepo.likePost(post.id)
            if (result.isFailure) {
                fun rollback(list: List<Post>): List<Post> = list.map {
                    if (it.id == post.id) it.copy(likesCount = previousCount, isLiked = wasLiked) else it
                }
                _rawPosts.update(::rollback)
                _posts.update(::rollback)
                _actionError.value = "Gagal memproses like. Coba lagi."
            }
        }
    }

    fun deletePost(postId: String) {
        val currentList = _rawPosts.value
        val index = currentList.indexOfFirst { it.id == postId }
        if (index == -1) return
        val removedPost = currentList[index]

        removePostById(postId)

        viewModelScope.launch(Dispatchers.IO) {
            val result = postRepo.deletePost(postId)
            if (result.isFailure) {
                restorePost(removedPost, index)
                _actionError.value = "Gagal menghapus postingan. Coba lagi."
            }
        }
    }
}

class CreatePostViewModel(private val homeViewModel: HomeViewModel) : ViewModel() {
    private val postRepo = ServiceLocator.postRepository
    private val prefs = ServiceLocator.encryptedPreferencesManager

    private val _text = MutableStateFlow("")
    val text = _text.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isFinished = MutableStateFlow(false)
    val isFinished = _isFinished.asStateFlow()

    // Preview link real-time (mirip WA) yang muncul saat user mengetik URL di form Buat Post.
    private val _linkPreview = MutableStateFlow<LinkPreview?>(null)
    val linkPreview = _linkPreview.asStateFlow()

    private val _isLoadingLinkPreview = MutableStateFlow(false)
    val isLoadingLinkPreview = _isLoadingLinkPreview.asStateFlow()

    private var isSubmitting = false
    private var linkPreviewJob: kotlinx.coroutines.Job? = null
    private var lastCheckedUrl: String? = null

    fun onTextChange(value: String) {
        _text.value = if (value.length > 3000) value.take(3000) else value
        checkForLinkPreview(_text.value)
    }

    private fun checkForLinkPreview(text: String) {
        val url = com.textsocial.app.util.LinkUtils.extractFirstUrl(text)

        if (url == null) {
            linkPreviewJob?.cancel()
            lastCheckedUrl = null
            _linkPreview.value = null
            _isLoadingLinkPreview.value = false
            return
        }

        if (url == lastCheckedUrl) return
        lastCheckedUrl = url

        linkPreviewJob?.cancel()
        linkPreviewJob = viewModelScope.launch {
            _isLoadingLinkPreview.value = true
            kotlinx.coroutines.delay(600)
            val result = postRepo.getLinkPreview(url)
            if (lastCheckedUrl == url) {
                _linkPreview.value = result.getOrNull()
                _isLoadingLinkPreview.value = false
            }
        }
    }

    fun resetComposerState() {
        _text.value = ""
        _isFinished.value = false
        linkPreviewJob?.cancel()
        lastCheckedUrl = null
        _linkPreview.value = null
        _isLoadingLinkPreview.value = false
    }

    fun createPost() {
        val text = _text.value
        if (text.isBlank() || isSubmitting) return
        val myId = com.textsocial.app.di.ServiceLocator.encryptedPreferencesManager.getUserId() ?: "me_id"
        if (!com.textsocial.app.util.RateLimiter.tryAcquire(
                "create_post:$myId",
                com.textsocial.app.util.RateLimiter.COOLDOWN_CREATE_POST_MS
            )
        ) {
            _error.value = "Terlalu cepat memposting. Tunggu sebentar ya."
            return
        }
        isSubmitting = true

        val myUsername = prefs.getUsername() ?: "you"
        val tempPost = Post(
            id = "temp-${java.util.UUID.randomUUID()}",
            userId = myId,
            username = myUsername,
            displayName = myUsername,
            userAvatarColor = prefs.getUserAvatarColor(),
            text = text,
            createdAt = com.textsocial.app.util.TimeUtils.nowIso(),
            likesCount = 0,
            commentsCount = 0,
            isLiked = false
        )

        homeViewModel.insertOptimisticPost(tempPost)
        _text.value = ""
        _isFinished.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val result = postRepo.createPost(text)
            if (result.isSuccess) {
                homeViewModel.removePostById(tempPost.id)
                homeViewModel.loadPosts()
            } else {
                homeViewModel.removePostById(tempPost.id)
                homeViewModel.reportActionError(
                    result.exceptionOrNull()?.message ?: "Gagal memposting. Coba lagi."
                )
            }
            isSubmitting = false
        }
    }
}

class PostDetailViewModel(private val homeViewModel: HomeViewModel) : ViewModel() {
    private val postRepo = ServiceLocator.postRepository

    private val _post = MutableStateFlow<Post?>(null)
    val post = _post.asStateFlow()

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments = _comments.asStateFlow()

    // Masih ada komentar lebih baru yang belum dimuat di server (post viral dgn >200
    // komentar) -- dipakai UI utk nampilin/nyembunyiin tombol "Muat komentar lainnya".
    private val _hasMoreComments = MutableStateFlow(false)
    val hasMoreComments = _hasMoreComments.asStateFlow()

    private val _isLoadingMoreComments = MutableStateFlow(false)
    val isLoadingMoreComments = _isLoadingMoreComments.asStateFlow()

    private val _commentText = MutableStateFlow("")
    val commentText = _commentText.asStateFlow()

    private val _replyingTo = MutableStateFlow<Comment?>(null)
    val replyingTo = _replyingTo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()

    private val _isPostDeleted = MutableStateFlow(false)
    val isPostDeleted = _isPostDeleted.asStateFlow()

    fun deletePost() {
        val currentPost = _post.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = postRepo.deletePost(currentPost.id)
            if (result.isSuccess) {
                homeViewModel.removePostById(currentPost.id)
                _isPostDeleted.value = true
            } else {
                _actionError.value = "Gagal menghapus postingan. Coba lagi."
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun setPost(postId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val found = homeViewModel.posts.value.find { it.id == postId }
            if (found != null) {
                _post.value = found
            } else {
                // Dulu fallback-nya narik SELURUH tabel posts cuma buat cari 1 post (mis.
                // saat dibuka dari notifikasi push dan belum ada di cache feed lokal).
                // Sekarang query langsung by id di server.
                val postResult = postRepo.getPostById(postId)
                if (postResult.isSuccess) {
                    _post.value = postResult.getOrNull()
                }
            }
            val commentsResult = postRepo.getComments(postId)
            if (commentsResult.isSuccess) {
                val page = commentsResult.getOrThrow()
                _comments.value = page.comments
                _hasMoreComments.value = page.hasMore
            }
            _isLoading.value = false
        }
    }

    /** Dipanggil dari tombol "Muat komentar lainnya" saat post viral punya >200 komentar. */
    fun loadMoreComments() {
        val currentPost = _post.value ?: return
        if (_isLoadingMoreComments.value || !_hasMoreComments.value) return
        val cursor = _comments.value.lastOrNull()?.createdAt ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMoreComments.value = true
            val result = postRepo.getComments(currentPost.id, after = cursor)
            _isLoadingMoreComments.value = false
            if (result.isSuccess) {
                val page = result.getOrThrow()
                val existingIds = _comments.value.map { it.id }.toSet()
                val newComments = page.comments.filterNot { it.id in existingIds }
                _comments.value = _comments.value + newComments
                _hasMoreComments.value = page.hasMore
            }
            // Gagal load-more diam-diam -- komentar yang sudah ada tetap tampil, user
            // tinggal tap tombol lagi kalau mau retry (sama polanya dgn loadMorePosts).
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
        val prefs = ServiceLocator.encryptedPreferencesManager
        val myId = prefs.getUserId() ?: "me_id"
        if (!com.textsocial.app.util.RateLimiter.tryAcquire(
                "create_comment:$myId",
                com.textsocial.app.util.RateLimiter.COOLDOWN_CREATE_COMMENT_MS
            )
        ) {
            _actionError.value = "Terlalu cepat mengirim komentar. Tunggu sebentar ya."
            return
        }
        val parentId = _replyingTo.value?.id
        val myUsername = prefs.getUsername() ?: "you"
        val tempComment = Comment(
            id = "temp-${java.util.UUID.randomUUID()}",
            postId = currentPost.id,
            userId = myId,
            username = myUsername,
            displayName = myUsername,
            text = text,
            createdAt = com.textsocial.app.util.TimeUtils.nowIso(),
            avatarColor = prefs.getUserAvatarColor(),
            parentId = parentId
        )
        _comments.update { it + tempComment }
        _post.update { it?.copy(commentsCount = it.commentsCount + 1) }
        _commentText.value = ""
        _replyingTo.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val result = postRepo.createComment(currentPost.id, text, parentId)
            if (result.isSuccess) {
                val commentsResult = postRepo.getComments(currentPost.id)
                if (commentsResult.isSuccess) {
                    val page = commentsResult.getOrThrow()
                    _comments.value = page.comments
                    _hasMoreComments.value = page.hasMore
                }
            } else {
                _comments.update { list -> list.filterNot { it.id == tempComment.id } }
                _post.update { it?.copy(commentsCount = (it.commentsCount - 1).coerceAtLeast(0)) }
                _actionError.value = "Gagal mengirim komentar. Coba lagi."
            }
        }
    }

    fun toggleCommentLike(comment: Comment) {
        if (!com.textsocial.app.util.RateLimiter.tryAcquire(
                "like_comment:${comment.id}",
                com.textsocial.app.util.RateLimiter.COOLDOWN_LIKE_MS
            )
        ) {
            return
        }

        val wasLiked = comment.isLiked
        val previousCount = comment.likesCount

        fun applyLocalState(list: List<Comment>): List<Comment> = list.map {
            if (it.id == comment.id) {
                if (wasLiked) it.copy(likesCount = (previousCount - 1).coerceAtLeast(0), isLiked = false)
                else it.copy(likesCount = previousCount + 1, isLiked = true)
            } else it
        }
        _comments.update(::applyLocalState)

        viewModelScope.launch(Dispatchers.IO) {
            val result = if (wasLiked) postRepo.unlikeComment(comment.id) else postRepo.likeComment(comment.id)
            if (result.isFailure) {
                _comments.update { list ->
                    list.map { if (it.id == comment.id) it.copy(likesCount = previousCount, isLiked = wasLiked) else it }
                }
                _actionError.value = "Gagal memproses like komentar. Coba lagi."
            }
        }
    }

    fun deleteComment(commentId: String) {
        val currentPost = _post.value ?: return
        val previousComments = _comments.value
        val previousCommentsCount = currentPost.commentsCount

        val idsToRemove = mutableSetOf(commentId)
        var changed = true
        while (changed) {
            changed = false
            for (c in previousComments) {
                if (c.parentId != null && idsToRemove.contains(c.parentId) && idsToRemove.add(c.id)) {
                    changed = true
                }
            }
        }

        _comments.value = previousComments.filter { it.id !in idsToRemove }
        _post.update { it?.copy(commentsCount = (it.commentsCount - idsToRemove.size).coerceAtLeast(0)) }

        viewModelScope.launch(Dispatchers.IO) {
            val result = postRepo.deleteComment(commentId)
            if (result.isFailure) {
                _comments.value = previousComments
                _post.update { it?.copy(commentsCount = previousCommentsCount) }
                _actionError.value = "Gagal menghapus komentar. Coba lagi."
            }
        }
    }
}

class StoryViewModel : ViewModel() {
    private val storyRepo = ServiceLocator.storyRepository
    private val userRepo = ServiceLocator.userRepository

    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories = _stories.asStateFlow()

    private val _allStories = MutableStateFlow<List<Story>>(emptyList())
    val allStories = _allStories.asStateFlow()

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

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()

    fun clearActionError() {
        _actionError.value = null
    }

    fun setSelectedStoryIndex(index: Int) {
        _selectedStoryIndex.value = index
    }

    private var singleUserViewId: String? = null

    init {
        loadStories()
    }

    fun loadStories() {
        singleUserViewId = null
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val rawStories = fetchAndApplyAllStories()
            applyFollowingGroupedStories(rawStories)
            _isLoading.value = false
        }
    }

    fun viewSingleUserStories(userId: String) {
        val userStories = _allStories.value.filter { it.userId == userId }.sortedBy { it.createdAt }
        if (userStories.isEmpty()) return
        singleUserViewId = userId
        _stories.value = userStories
        _selectedStoryIndex.value = 0
    }

    private suspend fun fetchAndApplyAllStories(): List<Story> {
        val result = storyRepo.getStories()
        val rawStories = result.getOrDefault(emptyList())
        _allStories.value = rawStories
        return rawStories
    }

    private suspend fun applyFollowingGroupedStories(rawStories: List<Story>) {
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
        val prefs = ServiceLocator.encryptedPreferencesManager
        val myId = prefs.getUserId() ?: "me_id"
        val myUsername = prefs.getUsername() ?: "you"
        val tempStory = Story(
            id = "temp-${java.util.UUID.randomUUID()}",
            userId = myId,
            username = myUsername,
            text = _storyText.value,
            createdAt = com.textsocial.app.util.TimeUtils.nowIso(),
            expiresAt = com.textsocial.app.util.TimeUtils.isoPlusHours(24),
            avatarColor = prefs.getUserAvatarColor(),
            backgroundColor = _storyBackgroundColor.value,
            textColor = _storyTextColor.value,
            fontFamily = _storyFontFamily.value
        )

        _stories.update { listOf(tempStory) + it }
        val text = _storyText.value
        val backgroundColor = _storyBackgroundColor.value
        val textColor = _storyTextColor.value
        val fontFamily = _storyFontFamily.value
        _isFinished.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val result = storyRepo.createStory(
                text = text,
                backgroundColor = backgroundColor,
                textColor = textColor,
                fontFamily = fontFamily
            )
            if (result.isSuccess) {
                loadStories()
            } else {
                _stories.update { current -> current.filterNot { it.id == tempStory.id } }
                _actionError.value = "Gagal membuat story. Coba lagi."
            }
        }
    }

    fun markStoryAsViewed(storyId: String, storyOwnerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storyRepo.recordStoryView(storyId)
            val rawStories = fetchAndApplyAllStories()
            val viewingUserId = singleUserViewId
            if (viewingUserId != null) {
                val refreshedUserStories = rawStories.filter { it.userId == viewingUserId }.sortedBy { it.createdAt }
                if (refreshedUserStories.isNotEmpty()) {
                    _stories.value = refreshedUserStories
                }
            } else {
                applyFollowingGroupedStories(rawStories)
            }
        }
    }

    fun deleteStory(storyId: String) {
        val currentList = _stories.value
        val index = currentList.indexOfFirst { it.id == storyId }
        if (index == -1) return
        val removedStory = currentList[index]

        _stories.update { it.filterNot { s -> s.id == storyId } }

        viewModelScope.launch(Dispatchers.IO) {
            val result = storyRepo.deleteStory(storyId)
            if (result.isFailure) {
                _stories.update { current ->
                    if (current.any { it.id == storyId }) current
                    else current.toMutableList().apply { add(index.coerceIn(0, size), removedStory) }
                }
                _actionError.value = "Gagal menghapus story. Coba lagi."
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

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode = _isSelectMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()

    fun clearActionError() {
        _actionError.value = null
    }

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

    fun toggleSelectMode() {
        _isSelectMode.update { !it }
        if (!_isSelectMode.value) _selectedIds.value = emptySet()
    }

    fun enterSelectModeWith(conversationId: String) {
        _isSelectMode.value = true
        _selectedIds.value = setOf(conversationId)
    }

    fun toggleSelected(conversationId: String) {
        _selectedIds.update { current ->
            if (current.contains(conversationId)) current - conversationId else current + conversationId
        }
    }

    fun selectAll() {
        _selectedIds.value = _conversations.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        val previousList = _conversations.value
        val removedInOrder = previousList.filter { ids.contains(it.id) }
        val otherUserIds = removedInOrder.map { it.otherUserId }

        _conversations.update { list -> list.filterNot { ids.contains(it.id) } }
        _selectedIds.value = emptySet()
        _isSelectMode.value = false

        viewModelScope.launch(Dispatchers.IO) {
            val result = msgRepo.hideConversations(otherUserIds)
            if (result.isFailure) {
                _conversations.update { current ->
                    (current + removedInOrder.filterNot { r -> current.any { it.id == r.id } })
                        .sortedByDescending { com.textsocial.app.util.TimeUtils.parseToEpochMillis(it.lastMessageTime) ?: 0L }
                }
                _actionError.value = "Gagal menghapus chat terpilih."
            }
        }
    }
}

class DMChatViewModel : ViewModel() {
    private val msgRepo = ServiceLocator.messageRepository
    private val userRepo = ServiceLocator.userRepository
    private val prefs = ServiceLocator.encryptedPreferencesManager
    private val _serverMessages = MutableStateFlow<List<Message>>(emptyList())
    private val _pendingMessages = MutableStateFlow<List<Message>>(emptyList())
    private val _pendingDeleteIds = MutableStateFlow<Set<String>>(emptySet())

    val messages: StateFlow<List<Message>> = combine(
        _serverMessages, _pendingMessages, _pendingDeleteIds
    ) { server, pending, pendingDeletes ->
        val mergedServer = server.map {
            if (pendingDeletes.contains(it.id)) it.copy(text = "Pesan ini telah dihapus", isDeleted = true) else it
        }
        (mergedServer + pending).sortedBy { com.textsocial.app.util.TimeUtils.parseToEpochMillis(it.createdAt) ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                _serverMessages.value = result.getOrDefault(emptyList())
            }

            msgRepo.markMessagesAsRead(targetUserId)
            onMessagesRead()

            msgRepo.observeMessages(targetUserId).collect { list ->
                _serverMessages.value = list
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

        val myId = prefs.getUserId() ?: "me_id"
        val tempMessage = Message(
            id = "temp-${java.util.UUID.randomUUID()}",
            conversationId = "",
            senderId = myId,
            text = text,
            createdAt = com.textsocial.app.util.TimeUtils.nowIso(),
            isRead = false,
            isPending = true
        )

        _pendingMessages.update { it + tempMessage }

        viewModelScope.launch(Dispatchers.IO) {
            val result = msgRepo.sendMessage(targetUserId, text)
            if (result.isSuccess) {
                _pendingMessages.update { list -> list.filterNot { it.id == tempMessage.id } }
            } else {
                _pendingMessages.update { list ->
                    list.map { if (it.id == tempMessage.id) it.copy(isPending = false, isFailed = true) else it }
                }
                _sendError.value = "Pesan gagal terkirim. Ketuk pesan untuk mencoba lagi."
            }
        }
    }

    fun retryMessage(message: Message) {
        if (!message.isFailed) return
        _pendingMessages.update { list ->
            list.map { if (it.id == message.id) it.copy(isPending = true, isFailed = false) else it }
        }
        _sendError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = msgRepo.sendMessage(targetUserId, message.text)
            if (result.isSuccess) {
                _pendingMessages.update { list -> list.filterNot { it.id == message.id } }
            } else {
                _pendingMessages.update { list ->
                    list.map { if (it.id == message.id) it.copy(isPending = false, isFailed = true) else it }
                }
                _sendError.value = "Pesan gagal terkirim. Ketuk pesan untuk mencoba lagi."
            }
        }
    }

    fun discardFailedMessage(messageId: String) {
        _pendingMessages.update { list -> list.filterNot { it.id == messageId } }
    }

    fun dismissSendError() {
        _sendError.value = null
    }

    fun deleteMessage(messageId: String) {
        if (targetUserId.isEmpty()) return

        _pendingDeleteIds.update { it + messageId }

        viewModelScope.launch(Dispatchers.IO) {
            val result = msgRepo.deleteMessageForEveryone(targetUserId, messageId)
            if (result.isFailure) {
                _pendingDeleteIds.update { it - messageId }
                _sendError.value = "Gagal menghapus pesan. Coba lagi."
            }
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

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()

    private val _selectedFilter = MutableStateFlow("all")
    val selectedFilter = _selectedFilter.asStateFlow()

    val filteredNotifications: StateFlow<List<Notification>> = combine(
        _notifications, _selectedFilter
    ) { list, filter ->
        if (filter == "all") list else list.filter { it.type == filter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun clearActionError() {
        _actionError.value = null
    }

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
            val result = userRepo.markNotificationAsRead(notification.id)
            if (result.isFailure) {
                _notifications.update { list ->
                    list.map { if (it.id == notification.id) it.copy(isRead = false) else it }
                }
                _actionError.value = "Gagal menandai sebagai dibaca."
            }
        }
    }

    fun markAllAsRead(onMarked: () -> Unit = {}) {
        if (_notifications.value.none { !it.isRead }) return
        val previousUnreadIds = _notifications.value.filterNot { it.isRead }.map { it.id }.toSet()
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        onMarked()
        viewModelScope.launch(Dispatchers.IO) {
            val result = userRepo.markNotificationsAsRead()
            if (result.isFailure) {
                _notifications.update { list ->
                    list.map { if (previousUnreadIds.contains(it.id)) it.copy(isRead = false) else it }
                }
                _actionError.value = "Gagal menandai semua sebagai dibaca."
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        val previousList = _notifications.value
        val index = previousList.indexOfFirst { it.id == notificationId }
        if (index == -1) return
        val removed = previousList[index]
        _notifications.update { list -> list.filterNot { it.id == notificationId } }
        viewModelScope.launch(Dispatchers.IO) {
            val result = userRepo.deleteNotification(notificationId)
            if (result.isFailure) {
                _notifications.update { current ->
                    if (current.any { it.id == notificationId }) current
                    else current.toMutableList().apply { add(index.coerceIn(0, size), removed) }
                }
                _actionError.value = "Gagal menghapus notifikasi."
            }
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
        _selectedIds.value = filteredNotifications.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        val previousList = _notifications.value
        val removedInOrder = previousList.filter { ids.contains(it.id) }
        _notifications.update { list -> list.filterNot { ids.contains(it.id) } }
        _selectedIds.value = emptySet()
        _isSelectMode.value = false
        viewModelScope.launch(Dispatchers.IO) {
            val result = userRepo.deleteNotifications(ids.toList())
            if (result.isFailure) {
                _notifications.update { current ->
                    (current + removedInOrder.filterNot { r -> current.any { it.id == r.id } })
                        .sortedByDescending { com.textsocial.app.util.TimeUtils.parseToEpochMillis(it.createdAt) ?: 0L }
                }
                _actionError.value = "Gagal menghapus notifikasi terpilih."
            }
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

    private val _isLoadingMorePosts = MutableStateFlow(false)
    val isLoadingMorePosts = _isLoadingMorePosts.asStateFlow()

    private val _hasMorePosts = MutableStateFlow(true)
    val hasMorePosts = _hasMorePosts.asStateFlow()

    // Disimpan supaya loadMorePosts() tahu profil siapa yang sedang di-page-kan.
    private var currentProfileUserId: String? = null

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

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()

    fun clearActionError() {
        _actionError.value = null
    }
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

            // Dulu ini narik SELURUH post di platform lalu difilter di client cuma buat
            // dapat post milik 1 user -- sangat tidak scalable. Sekarang difilter di server,
            // dan jumlah totalnya (bukan cuma yang di-load) diambil dari Content-Range.
            currentProfileUserId = resolvedUserId
            val postsResult = postRepo.getPostsByUser(resolvedUserId)
            if (postsResult.isSuccess) {
                val page = postsResult.getOrThrow()
                _posts.value = page.posts
                _hasMorePosts.value = page.hasMore
                _user.update { it?.copy(postsCount = page.totalCount ?: page.posts.size) }
            }
            _isLoading.value = false
        }
    }

    /** Ambil halaman post berikutnya milik profil yang sedang dibuka (infinite scroll). */
    fun loadMorePosts() {
        val userId = currentProfileUserId ?: return
        if (_isLoadingMorePosts.value || _isLoading.value || !_hasMorePosts.value) return
        val cursor = _posts.value.lastOrNull()?.createdAt ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMorePosts.value = true
            val result = postRepo.getPostsByUser(userId, before = cursor)
            _isLoadingMorePosts.value = false
            if (result.isSuccess) {
                val page = result.getOrThrow()
                val existingIds = _posts.value.map { it.id }.toSet()
                _posts.value = _posts.value + page.posts.filterNot { it.id in existingIds }
                _hasMorePosts.value = page.hasMore
            }
        }
    }

    fun toggleLike(post: Post) {
        val wasLiked = post.isLiked
        val previousCount = post.likesCount

        fun applyLocalState(list: List<Post>): List<Post> = list.map {
            if (it.id == post.id) {
                if (wasLiked) it.copy(likesCount = (previousCount - 1).coerceAtLeast(0), isLiked = false)
                else it.copy(likesCount = previousCount + 1, isLiked = true)
            } else it
        }
        _posts.update(::applyLocalState)
        homeViewModel.applyLikeStateToPost(post.id, isLiked = !wasLiked, likesCount = if (wasLiked) (previousCount - 1).coerceAtLeast(0) else previousCount + 1)

        viewModelScope.launch(Dispatchers.IO) {
            val result = if (wasLiked) postRepo.unlikePost(post.id) else postRepo.likePost(post.id)
            if (result.isFailure) {
                fun rollback(list: List<Post>): List<Post> = list.map {
                    if (it.id == post.id) it.copy(likesCount = previousCount, isLiked = wasLiked) else it
                }
                _posts.update(::rollback)
                homeViewModel.applyLikeStateToPost(post.id, isLiked = wasLiked, likesCount = previousCount)
                _actionError.value = "Gagal memproses like. Coba lagi."
            }
        }
    }

    fun toggleFollow() {
        val profile = _user.value ?: return
        if (_isFollowActionLoading.value) return
        val wasFollowing = _isFollowing.value
        val previousFollowersCount = profile.followersCount

        _isFollowing.value = !wasFollowing
        val delta = if (wasFollowing) -1 else 1
        _user.update { it?.copy(followersCount = (it.followersCount + delta).coerceAtLeast(0)) }
        _isFollowActionLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val result = if (wasFollowing) {
                userRepo.unfollowUser(profile.id)
            } else {
                userRepo.followUser(profile.id)
            }
            if (result.isFailure) {
                _isFollowing.value = wasFollowing
                _user.update { it?.copy(followersCount = previousFollowersCount) }
                _actionError.value = if (wasFollowing) "Gagal berhenti mengikuti. Coba lagi." else "Gagal mengikuti. Coba lagi."
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
        val previousList = _posts.value
        val index = previousList.indexOfFirst { it.id == postId }
        if (index == -1) return
        val removedPost = previousList[index]

        _posts.update { it.filterNot { p -> p.id == postId } }
        homeViewModel.removePostById(postId)

        viewModelScope.launch(Dispatchers.IO) {
            val result = postRepo.deletePost(postId)
            if (result.isFailure) {
                _posts.update { current ->
                    if (current.any { it.id == postId }) current
                    else current.toMutableList().apply { add(index.coerceIn(0, size), removedPost) }
                }
                homeViewModel.restorePost(removedPost, index)
                _actionError.value = "Gagal menghapus postingan. Coba lagi."
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

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()

    fun clearActionError() {
        _actionError.value = null
    }

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
        val wasFollowing = entry.isFollowedByMe
        val newValue = !wasFollowing

        fun updateList(list: List<FollowListEntry>) = list.map {
            if (it.user.id == entry.user.id) it.copy(isFollowedByMe = newValue) else it
        }
        _followers.value = updateList(_followers.value)
        _following.value = updateList(_following.value)
        _followActionLoadingIds.update { it + entry.user.id }

        viewModelScope.launch(Dispatchers.IO) {
            val result = if (wasFollowing) {
                userRepo.unfollowUser(entry.user.id)
            } else {
                userRepo.followUser(entry.user.id)
            }
            if (result.isFailure) {
                fun revertList(list: List<FollowListEntry>) = list.map {
                    if (it.user.id == entry.user.id) it.copy(isFollowedByMe = wasFollowing) else it
                }
                _followers.value = revertList(_followers.value)
                _following.value = revertList(_following.value)
                _actionError.value = if (wasFollowing) "Gagal berhenti mengikuti. Coba lagi." else "Gagal mengikuti. Coba lagi."
            }
            _followActionLoadingIds.update { it - entry.user.id }
        }
    }
}
class AppUpdateViewModel : ViewModel() {
    private val appUpdateRepo = ServiceLocator.appUpdateRepository

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    init {
        // Push "app_update" bisa masuk kapan saja lewat FCM, termasuk selagi app sedang
        // dibuka (LaunchedEffect(Unit) di AppNavGraph cuma jalan sekali di awal, tidak
        // otomatis re-check). Dengerin NotificationEventBus (pola sama dengan BadgeViewModel
        // buat live-update badge) supaya popup ikut muncul saat itu juga, tanpa user
        // perlu tutup-buka app dulu.
        viewModelScope.launch {
            com.textsocial.app.util.NotificationEventBus.events.collect { type ->
                if (type == "app_update") checkForUpdate()
            }
        }
    }

    /** Dipanggil dari AppNavGraph tiap app dibuka, dan otomatis lagi tiap push "app_update"
     *  diterima selagi app kebuka. Kalau update-nya sudah pernah di-dismiss user (dan bukan
     *  force-update), tidak perlu tampilkan dialog lagi. */
    fun checkForUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = appUpdateRepo.checkForUpdate()
            val info = result.getOrNull() ?: return@launch
            val alreadyDismissed = !info.isForceUpdate && info.versionCode <= appUpdateRepo.getDismissedVersionCode()
            if (!alreadyDismissed) {
                _updateInfo.value = info
            }
        }
    }

    fun dismiss() {
        val info = _updateInfo.value ?: return
        if (info.isForceUpdate) return
        appUpdateRepo.dismissVersion(info.versionCode)
        _updateInfo.value = null
    }
}