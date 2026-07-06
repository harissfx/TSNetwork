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

// 0. MAIN TAB VIEWMODEL
// Menyimpan tab mana yang sedang aktif di HorizontalPager MainScreen (Home/Search/
// CreatePost/Notifications/Profile). Dipakai supaya layar-layar yang di-push di atas
// MainScreen (mis. profil orang lain) tetap bisa "pindah tab" dengan cara pop kembali
// ke MainScreen lalu memberi tahu tab mana yang harus ditampilkan.
class MainTabViewModel : ViewModel() {
    private val _currentTab = MutableStateFlow(0)
    val currentTab = _currentTab.asStateFlow()

    fun goToTab(index: Int) {
        _currentTab.value = index
    }
}

// 0B. BADGE VIEWMODEL
// Dibuat sekali di AppNavGraph (sama seperti MainTabViewModel) supaya angka belum-dibaca
// tetap sinkron di berbagai layar sekaligus: badge ikon Notifikasi di BottomNavigationBar,
// badge ikon DM/pesan di TopAppBar HomeScreen, dan indikator per-percakapan di DMListScreen.
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

    // Dipanggil begitu tab Notifikasi dibuka: badge langsung hilang di UI (optimistic),
    // sambil PATCH is_read=true dikirim ke server di belakang layar.
    fun markNotificationsRead() {
        if (_unreadNotifications.value == 0) return
        _unreadNotifications.value = 0
        viewModelScope.launch(Dispatchers.IO) {
            userRepo.markNotificationsAsRead()
        }
    }
}

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
// Mode urutan feed: LATEST = kronologis murni (paling baru di atas), FOR_YOU =
// pendekatan "algoritma" sederhana yang menggabungkan seberapa baru postingan
// dengan seberapa besar engagement-nya (like + komentar), supaya postingan yang
// rame direspon tetap kelihatan walau bukan yang paling baru — mirip prinsip
// feed di media sosial modern, tapi dihitung di sisi klien (tanpa server ranking).
enum class FeedMode { FOR_YOU, LATEST }

class HomeViewModel : ViewModel() {
    private val postRepo = ServiceLocator.postRepository

    // Data mentah hasil fetch dari server (urutan kronologis apa adanya).
    private val _rawPosts = MutableStateFlow<List<Post>>(emptyList())

    // Data yang benar-benar ditampilkan ke UI, sudah diurutkan sesuai `feedMode`.
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

    /** Ganti mode urutan feed tanpa perlu fetch ulang ke server — cukup re-sort data yang sudah ada. */
    fun setFeedMode(mode: FeedMode) {
        if (_feedMode.value == mode) return
        _feedMode.value = mode
        _posts.value = sortForMode(_rawPosts.value, mode)
    }

    // Skor ala "hot ranking" (terinspirasi dari formula gravity Hacker News):
    // makin banyak like/komentar -> skor naik, tapi skor meluruh seiring waktu
    // supaya postingan lama tidak selamanya mendominasi feed hanya karena
    // sempat viral. Komentar diberi bobot lebih besar dari like karena
    // merefleksikan engagement yang lebih aktif.
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

    private val _replyingTo = MutableStateFlow<Comment?>(null)
    val replyingTo = _replyingTo.asStateFlow()

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
                // Hapus komentar itu sendiri sekaligus SEMUA turunannya (balasan, balasan
                // dari balasan, dst — bukan cuma anak langsung) supaya konsisten dengan
                // "on delete cascade" di database.
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

// 7. STORY VIEWMODEL
class StoryViewModel : ViewModel() {
    private val storyRepo = ServiceLocator.storyRepository
    private val userRepo = ServiceLocator.userRepository

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
            val rawStories = result.getOrDefault(emptyList())

            // BUG SEBELUMNYA: semua story dari SEMUA user ditampilkan, padahal seharusnya
            // hanya story milik sendiri + story dari user yang sudah kita follow.
            val myId = ServiceLocator.encryptedPreferencesManager.getUserId()
            val followingIds = userRepo.getFollowingUsers().getOrDefault(emptyList()).map { it.id }.toSet()
            val visibleStories = rawStories.filter { it.userId == myId || followingIds.contains(it.userId) }

            // Kelompokkan story per user supaya user yang punya beberapa story
            // tampil sebagai SATU bubble (kayak Instagram), bukan beberapa bubble
            // terpisah. Di dalam satu user, urutkan dari yang paling lama ke
            // terbaru; antar user, yang paling baru posting story ditaruh duluan.
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

    // Dipanggil setiap kali layar "Buat Story" dibuka, supaya sisa state dari sesi
    // sebelumnya (misal isFinished = true dari story terakhir yang berhasil dibuat)
    // tidak membuat layar langsung ke-pop lagi sebelum sempat dipakai.
    fun resetComposerState() {
        _storyText.value = ""
        _isFinished.value = false
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

    // BUG SEBELUMNYA: fungsi ini dipanggil untuk SEMUA story yang dibuka, termasuk story
    // milik sendiri, jadi nama/akun sendiri ikut tercatat sebagai "viewer" story sendiri.
    // Sekarang skip pencatatan kalau story yang dibuka adalah story sendiri.
    fun markStoryAsViewed(storyId: String, storyOwnerId: String) {
        val myId = ServiceLocator.encryptedPreferencesManager.getUserId()
        if (myId != null && myId == storyOwnerId) return
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

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError = _sendError.asStateFlow()

    private var targetUserId: String = ""

    fun initChat(otherUserId: String, onMessagesRead: () -> Unit = {}) {
        targetUserId = otherUserId
        viewModelScope.launch(Dispatchers.IO) {
            // SEBELUMNYA: histori pesan lama tidak pernah dimuat di sini, hanya
            // observeMessages() yang menunggu update realtime lewat WebSocket. Jadi
            // begitu chat dibuka, layar terlihat kosong sampai ada pesan baru masuk.
            val result = msgRepo.getMessages(targetUserId)
            if (result.isSuccess) {
                _messages.value = result.getOrDefault(emptyList())
            }

            // Mark read -- lalu beri tahu pemanggil (mis. BadgeViewModel) supaya badge
            // jumlah pesan belum-dibaca di ikon DM/BottomNavigationBar ikut diperbarui.
            msgRepo.markMessagesAsRead(targetUserId)
            onMessagesRead()

            // Dynamic flow observation representing WebSockets / Supabase Realtime in a highly responsive manner!
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
                // Sebelumnya kegagalan ini didiamkan total: teks sudah terlanjur
                // dikosongkan dan tidak ada bubble/pesan error apa pun yang muncul,
                // jadi kelihatan seperti "halaman chat kosong". Sekarang teks yang
                // gagal terkirim dikembalikan ke kolom input, dan errornya ditampilkan.
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

    // Dipanggil tiap tab Notifikasi baru saja DIBUKA. SEBELUMNYA: notifikasi ditandai
    // "sudah dibaca" cuma di server (lewat BadgeViewModel), sementara daftar `_notifications`
    // di sini tidak pernah ikut di-update -- jadi walau server sudah is_read=true, list yang
    // sedang tampil di layar tetap pakai data lama (isRead=false), makanya tetap kelihatan
    // biru terus walaupun user sudah tap salah satu notifikasi dan kembali lagi.
    // SEKARANG: begitu list terbaru selesai dimuat, kita langsung set semuanya isRead=true
    // SECARA LOKAL di state ini juga (bukan cuma di server), jadi tampilannya seketika tidak
    // biru lagi tanpa perlu reload/tab-switch tambahan supaya berubah.
    fun loadAndMarkAsRead(onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val result = userRepo.getNotifications()
            val fresh = result.getOrDefault(emptyList())
            val hadUnread = fresh.any { !it.isRead }
            _notifications.value = fresh.map { it.copy(isRead = true) }
            _isLoading.value = false
            if (hadUnread) {
                userRepo.markNotificationsAsRead()
            }
            onDone()
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

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing = _isFollowing.asStateFlow()

    private val _isFollowActionLoading = MutableStateFlow(false)
    val isFollowActionLoading = _isFollowActionLoading.asStateFlow()

    fun loadProfile(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val userResult = userRepo.getProfile(userId)
            // getProfile() resolves the "me_id" placeholder to the real UUID internally,
            // so we must use profile.id (not the raw userId param) when matching posts below.
            var resolvedUserId = userId
            if (userResult.isSuccess) {
                var profile = userResult.getOrNull()
                resolvedUserId = profile?.id ?: userId

                // Ambil jumlah followers/following yang sebenarnya dari tabel follows
                val countsResult = userRepo.getFollowCounts(resolvedUserId)
                if (countsResult.isSuccess) {
                    val (followers, following) = countsResult.getOrDefault(0 to 0)
                    profile = profile?.copy(followersCount = followers, followingCount = following)
                }

                _user.value = profile
                _bioText.value = profile?.bio ?: ""
                _displayNameText.value = profile?.displayName ?: ""

                // Cek apakah user saat ini sudah follow profil ini (skip untuk profil sendiri)
                val myId = com.textsocial.app.di.ServiceLocator.encryptedPreferencesManager.getUserId()
                if (myId != null && resolvedUserId != myId) {
                    val followingResult = userRepo.isFollowing(resolvedUserId)
                    _isFollowing.value = followingResult.getOrDefault(false)
                } else {
                    _isFollowing.value = false
                }
            }

            // Load user's own posts
            val postsResult = postRepo.getPosts()
            if (postsResult.isSuccess) {
                val userPosts = postsResult.getOrDefault(emptyList()).filter { it.userId == resolvedUserId }
                _posts.value = userPosts
                // BUG SEBELUMNYA: profile.postsCount tidak pernah dihitung dari data asli,
                // jadi selalu menampilkan 0 di layar Profile walau user sudah punya postingan.
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
            // Sinkronkan juga ke feed Home supaya status like konsisten di kedua layar.
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