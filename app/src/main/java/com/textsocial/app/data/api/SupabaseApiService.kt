package com.textsocial.app.data.api

import com.textsocial.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApiService {

    // --- Supabase Auth ---
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

    // --- Profiles (using profiles table) ---
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

    // --- Posts ---
    @GET("rest/v1/posts")
    suspend fun getPosts(
        @Query("select") select: String = "*,users:profiles(*)",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<PostDto>>

    @POST("rest/v1/posts")
    suspend fun createPost(
        @Body post: CreatePostRequest
    ): Response<Void>

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

    // --- Likes ---
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

    // --- Comments ---
    @GET("rest/v1/comments")
    suspend fun getComments(
        @Query("post_id") postIdFilter: String,
        @Query("select") select: String = "*,users:profiles(*)",
        @Query("order") order: String = "created_at.asc"
    ): Response<List<CommentDto>>

    @POST("rest/v1/comments")
    suspend fun createComment(
        @Body comment: CreateCommentRequest
    ): Response<Void>

    // --- Follows ---
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

    // --- Stories ---
    @GET("rest/v1/stories")
    suspend fun getStories(
        @Query("expires_at") expiresFilter: String, // e.g. "gt.NOW"
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

    // --- Messages (DMs) ---
    @GET("rest/v1/messages")
    suspend fun getMessages(
        @Query("conversation_id") conversationFilter: String, // e.g. "eq.XYZ"
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.asc"
    ): Response<List<MessageDto>>

    @GET("rest/v1/conversations")
    suspend fun getConversations(
        @Query("or") filter: String, // e.g. "(user1_id.eq.userId,user2_id.eq.userId)"
        @Query("select") select: String = "*,messages(*)",
        @Query("order") order: String = "updated_at.desc"
    ): Response<List<ConversationDto>>

    @POST("rest/v1/messages")
    suspend fun sendMessage(
        @Body message: SendMessageRequest
    ): Response<Void>

    @PATCH("rest/v1/messages")
    suspend fun markMessagesAsRead(
        @Query("conversation_id") conversationFilter: String,
        @Query("sender_id") senderFilter: String, // e.g. "not.eq.my_id"
        @Body updates: MarkAsReadRequest = MarkAsReadRequest()
    ): Response<Void>

    // --- Notifications ---
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
}
