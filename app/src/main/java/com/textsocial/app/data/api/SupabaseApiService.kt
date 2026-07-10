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

    @GET("rest/v1/posts")
    suspend fun getPosts(
        @Query("select") select: String = "*,users:profiles(*)",
        @Query("order") order: String = "created_at.desc"
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

    @GET("rest/v1/comments")
    suspend fun getComments(
        @Query("post_id") postIdFilter: String,
        @Query("select") select: String = "*,users:profiles(*)",
        @Query("order") order: String = "created_at.asc"
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

    // Sembunyikan chat dari daftar DM untuk satu sisi saja (bukan hapus beneran),
    // dipakai lewat 2 endpoint terpisah tergantung posisi user di conversation (user1/user2)
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

    // Dipanggil real-time saat user mengetik link di form Buat Post. Edge Function ini yang
    // fetch & parse HTML situs tujuan (bukan client), lalu simpan hasilnya ke tabel
    // link_previews sebagai cache untuk dibaca ulang oleh getLinkPreviewsCached di bawah.
    @POST("functions/v1/link-preview")
    suspend fun fetchLinkPreview(
        @Body request: LinkPreviewRequest
    ): Response<LinkPreviewDto>

    // Baca cache link_previews untuk sekumpulan URL sekaligus (dipakai saat render feed,
    // supaya tidak fetch 1-per-1 ke Edge Function tiap kali feed di-load).
    @GET("rest/v1/link_previews")
    suspend fun getLinkPreviewsCached(
        @Query("url") urlInFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<LinkPreviewDto>>
}