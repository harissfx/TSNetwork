package com.textsocial.app.data.api

import com.textsocial.app.data.model.*
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApiService {

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Body request: SupabaseSignUpRequestBody
    ): Response<SupabaseSignUpResponse>

    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(
        @Body request: SupabaseSignInRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/recover")
    suspend fun recoverPassword(
        @Body request: RecoverPasswordRequest
    ): Response<Void>

    // Butuh Authorization: Bearer <access_token> milik user yang sedang login --
    // otomatis ditambahkan oleh interceptor di SupabaseClient, jadi tidak perlu
    // dikirim manual di sini.
    @PUT("auth/v1/user")
    suspend fun updatePassword(
        @Body request: UpdatePasswordRequest
    ): Response<Void>

    @GET("rest/v1/profiles")
    suspend fun getProfile(
        @Query("id") filter: String,
        @Query("select") select: String = "*"
    ): Response<List<ProfileDto>>

    @GET("rest/v1/profiles")
    suspend fun getProfilesByUsername(
        @Query("username") filter: String,
        @Query("select") select: String = "*"
    ): Response<List<ProfileDto>>

    @POST("rest/v1/profiles")
    suspend fun createProfile(
        @Body profile: ProfileDto,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<ProfileDto>>

    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Query("id") filter: String,
        @Body updates: UpdateProfileRequest
    ): Response<Void>

    @PATCH("rest/v1/profiles")
    suspend fun updateAvatarUrl(
        @Query("id") filter: String,
        @Body updates: UpdateAvatarRequest
    ): Response<Void>

    @PATCH("rest/v1/profiles")
    suspend fun updateFollowListPrivacy(
        @Query("id") filter: String,
        @Body updates: UpdateFollowListPrivacyRequest
    ): Response<Void>

    @PATCH("rest/v1/profiles")
    suspend fun updateUsername(
        @Query("id") filter: String,
        @Body updates: UpdateUsernameRequest
    ): Response<Void>

    @POST("storage/v1/object/{bucketPath}")
    suspend fun uploadAvatar(
        @Path(value = "bucketPath", encoded = true) bucketPath: String,
        @Body file: RequestBody,
        @Header("Content-Type") contentType: String,
        @Header("x-upsert") upsert: String = "true"
    ): Response<Void>

    // Feed utama, dipaginasi pakai keyset cursor (bukan offset) supaya query tetap
    // cepat walau tabel posts sudah berisi jutaan baris -- ditopang index
    // idx_posts_created_at yang sudah ada. `createdBefore` diisi created_at dari post
    // TERAKHIR di halaman sebelumnya (format "lt.<ISO8601>"), null untuk halaman pertama.
    // Retrofit otomatis membuang query param yang null, jadi aman dipanggil tanpa cursor.
    @GET("rest/v1/posts")
    suspend fun getPosts(
        @Query("select") select: String = "*,users:profiles(*)",
        @Query("order") order: String = "created_at.desc",
        @Query("created_at") createdBefore: String? = null,
        @Query("limit") limit: Int = 20
    ): Response<List<PostDto>>

    // Ambil satu post spesifik lewat id -- dipakai saat post yang dituju (mis. dari deep
    // link notifikasi) belum ada di cache feed lokal, supaya TIDAK perlu narik seluruh
    // tabel posts cuma buat cari 1 baris.
    @GET("rest/v1/posts")
    suspend fun getPostById(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*,users:profiles(*)",
        @Query("limit") limit: Int = 1
    ): Response<List<PostDto>>

    // Post milik satu user (dipakai di halaman profil), difilter & dipaginasi di server
    // lewat query, bukan ambil semua post lalu difilter di client.
    // Prefer: count=exact bikin Postgrest hitungkan TOTAL baris yang match filter dan
    // kirim lewat header Content-Range (mis. "0-19/153"), tanpa perlu server ngirim ke
    // kita semua barisnya -- dipakai buat isi "jumlah post" di halaman profil secara
    // akurat walau yang benar-benar di-fetch cuma 1 halaman.
    @Headers("Prefer: count=exact")
    @GET("rest/v1/posts")
    suspend fun getPostsByUser(
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "*,users:profiles(*)",
        @Query("order") order: String = "created_at.desc",
        @Query("created_at") createdBefore: String? = null,
        @Query("limit") limit: Int = 20
    ): Response<List<PostDto>>

    @POST("rest/v1/posts")
    suspend fun createPost(
        @Body post: CreatePostRequest,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<PostDto>>

    @DELETE("rest/v1/posts")
    suspend fun deletePost(
        @Query("id") filter: String
    ): Response<Void>

    @DELETE("rest/v1/comments")
    suspend fun deleteComment(
        @Query("id") filter: String
    ): Response<Void>

    @DELETE("rest/v1/stories")
    suspend fun deleteStory(
        @Query("id") filter: String
    ): Response<Void>

    // NOTE: postIdFilter WAJIB diisi dari caller (mis. "in.(id1,id2,...)") supaya query
    // di-scope ke halaman post yang lagi ditampilkan, BUKAN seluruh riwayat like user.
    // Dulu dipanggil dengan postIdFilter = null (cuma userIdFilter), yang narik SEMUA like
    // milik user tanpa limit -- baik-baik saja untuk user baru, tapi user aktif dengan
    // ribuan like historis bikin payload & waktu query getPosts/getPostById/getPostsByUser
    // membengkak terus padahal yang dibutuhkan cuma status like utk beberapa post di layar.
    @GET("rest/v1/likes")
    suspend fun getLikes(
        @Query("post_id") postIdFilter: String?,
        @Query("user_id") userIdFilter: String?,
        @Query("select") select: String = "*"
    ): Response<List<LikeDto>>

    @POST("rest/v1/likes")
    suspend fun likePost(
        @Body like: LikePostRequest
    ): Response<Void>

    @DELETE("rest/v1/likes")
    suspend fun unlikePost(
        @Query("post_id") postIdFilter: String,
        @Query("user_id") userIdFilter: String
    ): Response<Void>

    // Dipaginasi pakai keyset cursor (sama pola dengan getPosts), bukan offset, supaya
    // post yang benar-benar viral (>200 komentar) tetap bisa dimuat lewat "load more"
    // alih-alih terpotong permanen di komentar ke-200. Order-nya ASC (komentar terlama
    // dulu), jadi cursor "load more" ambil komentar yang LEBIH BARU dari komentar
    // TERAKHIR yang sudah dimuat (format createdAfter = "gt.<ISO8601>"), null untuk
    // halaman pertama. Retrofit otomatis buang query param null.
    @GET("rest/v1/comments")
    suspend fun getComments(
        @Query("post_id") postIdFilter: String,
        @Query("select") select: String = "*,users:profiles(*)",
        @Query("order") order: String = "created_at.asc",
        @Query("created_at") createdAfter: String? = null,
        @Query("limit") limit: Int = 200
    ): Response<List<CommentDto>>

    @POST("rest/v1/comments")
    suspend fun createComment(
        @Body comment: CreateCommentRequest,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<CommentDto>>

    @GET("rest/v1/comment_likes")
    suspend fun getCommentLikes(
        @Query("comment_id") commentIdFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<CommentLikeDto>>

    @POST("rest/v1/comment_likes")
    suspend fun likeComment(
        @Body like: CreateCommentLikeRequest
    ): Response<Void>

    @DELETE("rest/v1/comment_likes")
    suspend fun unlikeComment(
        @Query("comment_id") commentIdFilter: String,
        @Query("user_id") userIdFilter: String
    ): Response<Void>

    @GET("rest/v1/follows")
    suspend fun getFollowers(
        @Query("following_id") filter: String,
        @Query("select") select: String = "*"
    ): Response<List<FollowDto>>

    @GET("rest/v1/follows")
    suspend fun getFollowing(
        @Query("follower_id") filter: String,
        @Query("select") select: String = "*"
    ): Response<List<FollowDto>>

    // Cek satu pasangan follower/following spesifik langsung lewat unique constraint
    // (follower_id, following_id) -- O(1) index lookup, BUKAN narik seluruh daftar follower
    // satu akun cuma buat cek "apakah user X ada di dalamnya". Dipakai oleh isFollowing()/
    // isFollowedBy() yang dulu manggil getFollowers() tanpa limit tiap kali profil dibuka.
    @GET("rest/v1/follows")
    suspend fun checkFollowExists(
        @Query("follower_id") followerIdFilter: String,
        @Query("following_id") followingIdFilter: String,
        @Query("select") select: String = "follower_id",
        @Query("limit") limit: Int = 1
    ): Response<List<FollowDto>>

    // Hitung jumlah followers/following lewat header Content-Range (Prefer: count=exact),
    // BUKAN dengan narik seluruh baris lalu di-.size() di client. limit=1 supaya body-nya
    // minimal -- count di header tetap akurat terhadap SELURUH baris yang match filter,
    // tidak terpengaruh oleh limit (sama pola dengan getPostsByUser).
    @Headers("Prefer: count=exact")
    @GET("rest/v1/follows")
    suspend fun getFollowersCount(
        @Query("following_id") filter: String,
        @Query("select") select: String = "id",
        @Query("limit") limit: Int = 1
    ): Response<List<FollowDto>>

    @Headers("Prefer: count=exact")
    @GET("rest/v1/follows")
    suspend fun getFollowingCount(
        @Query("follower_id") filter: String,
        @Query("select") select: String = "id",
        @Query("limit") limit: Int = 1
    ): Response<List<FollowDto>>

    @POST("rest/v1/follows")
    suspend fun followUser(
        @Body follow: FollowUserRequest
    ): Response<Void>

    @DELETE("rest/v1/follows")
    suspend fun unfollowUser(
        @Query("follower_id") followerFilter: String,
        @Query("following_id") followingFilter: String
    ): Response<Void>

    @GET("rest/v1/stories")
    suspend fun getStories(
        @Query("expires_at") expiresFilter: String,
        @Query("select") select: String = "*,users:profiles(*)",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<StoryDto>>

    @POST("rest/v1/stories")
    suspend fun createStory(
        @Body story: CreateStoryRequest
    ): Response<Void>

    @GET("rest/v1/story_views")
    suspend fun getStoryViews(
        @Query("story_id") storyIdFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<StoryViewDto>>

    @POST("rest/v1/story_views")
    suspend fun recordStoryView(
        @Body view: RecordStoryViewRequest
    ): Response<Void>

    @GET("rest/v1/messages")
    suspend fun getMessages(
        @Query("conversation_id") conversationFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.asc"
    ): Response<List<MessageDto>>

    @GET("rest/v1/conversations")
    suspend fun getConversations(
        @Query("or") filter: String,
        @Query("select") select: String = "*,messages(*)",
        @Query("order") order: String = "updated_at.desc"
    ): Response<List<ConversationDto>>

    @POST("rest/v1/conversations?on_conflict=id")
    suspend fun upsertConversation(
        @Body request: UpsertConversationRequest,
        @Header("Prefer") prefer: String = "resolution=ignore-duplicates,return=minimal"
    ): Response<Void>

    @POST("rest/v1/messages")
    suspend fun sendMessage(
        @Body message: SendMessageRequest,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<MessageDto>>

    @PATCH("rest/v1/messages")
    suspend fun markMessagesAsRead(
        @Query("conversation_id") conversationFilter: String,
        @Query("sender_id") senderFilter: String,
        @Body updates: MarkAsReadRequest = MarkAsReadRequest()
    ): Response<Void>

    @PATCH("rest/v1/messages")
    suspend fun deleteMessageForEveryone(
        @Query("id") idFilter: String,
        @Body updates: DeleteMessageRequest = DeleteMessageRequest()
    ): Response<Void>

    @PATCH("rest/v1/conversations")
    suspend fun hideConversationsForUser1(
        @Query("id") idFilter: String,
        @Body updates: HideConversationForUser1Request = HideConversationForUser1Request()
    ): Response<Void>

    @PATCH("rest/v1/conversations")
    suspend fun hideConversationsForUser2(
        @Query("id") idFilter: String,
        @Body updates: HideConversationForUser2Request = HideConversationForUser2Request()
    ): Response<Void>

    @GET("rest/v1/notifications")
    suspend fun getNotifications(
        @Query("recipient_id") recipientIdFilter: String,
        @Query("select") select: String = "*,sender:profiles!sender_id(*)",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<NotificationDto>>

    @POST("rest/v1/notifications")
    suspend fun createNotification(
        @Body notification: CreateNotificationRequest
    ): Response<Void>

    @PATCH("rest/v1/notifications")
    suspend fun markNotificationAsRead(
        @Query("id") idFilter: String,
        @Body updates: MarkAsReadRequest = MarkAsReadRequest()
    ): Response<Void>

    @PATCH("rest/v1/notifications")
    suspend fun markNotificationsAsRead(
        @Query("recipient_id") recipientFilter: String,
        @Body updates: MarkAsReadRequest = MarkAsReadRequest()
    ): Response<Void>

    @DELETE("rest/v1/notifications")
    suspend fun deleteNotifications(
        @Query("id") filter: String
    ): Response<Void>

    @POST("rest/v1/device_tokens?on_conflict=fcm_token")
    suspend fun upsertDeviceToken(
        @Body request: UpsertDeviceTokenRequest,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal"
    ): Response<Void>

    @DELETE("rest/v1/device_tokens")
    suspend fun deleteDeviceToken(
        @Query("fcm_token") tokenFilter: String
    ): Response<Void>

    @POST("functions/v1/link-preview")
    suspend fun fetchLinkPreview(
        @Body request: LinkPreviewRequest
    ): Response<LinkPreviewDto>

    @GET("rest/v1/link_previews")
    suspend fun getLinkPreviewsCached(
        @Query("url") urlInFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<LinkPreviewDto>>

    // Ambil rilis Android terbaru yang terdaftar -- diisi manual lewat Supabase dashboard
    // tiap ada versi baru (lihat README bagian 7). limit=1 + order desc by version_code
    // cukup buat dapat baris terbaru tanpa perlu narik seluruh riwayat rilis.
    @GET("rest/v1/app_versions")
    suspend fun getLatestAppVersion(
        @Query("platform") platformFilter: String = "eq.android",
        @Query("select") select: String = "*",
        @Query("order") order: String = "version_code.desc",
        @Query("limit") limit: Int = 1
    ): Response<List<AppVersionDto>>
}