package com.textsocial.app.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseSignUpRequestBody(
    val email: String,
    val password: String,
    val data: SignUpUserData? = null
)

@JsonClass(generateAdapter = true)
data class SignUpUserData(
    val username: String,
    val display_name: String
)

@JsonClass(generateAdapter = true)
data class UserMetadataDto(
    val username: String? = null,
    val display_name: String? = null,
    val email_verified: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseSignUpResponse(
    val id: String?,
    val email: String?,
    val email_verified: Boolean? = null,
    val user_metadata: UserMetadataDto? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseSignInRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthResponse(
    val access_token: String,
    val refresh_token: String,
    val token_type: String,
    val expires_in: Int,
    val user: SupabaseUser
)

@JsonClass(generateAdapter = true)
data class SupabaseUser(
    val id: String,
    val email: String,
    val email_verified: Boolean? = null,
    val user_metadata: UserMetadataDto? = null
)

@JsonClass(generateAdapter = true)
data class ProfileDto(
    val id: String,
    val username: String,
    val email: String?,
    val display_name: String?,
    val bio: String?,
    val is_private: Boolean = false,
    val is_verified: Boolean = false,
    val avatar_url: String? = null,
    val hide_following_list: Boolean = false
)

@JsonClass(generateAdapter = true)
data class PostDto(
    val id: String,
    val user_id: String,
    val content: String,
    val created_at: String,
    @Json(name = "likes_count") val like_count: Int = 0,
    @Json(name = "comments_count") val comment_count: Int = 0,
    val users: ProfileDto? = null
)

@JsonClass(generateAdapter = true)
data class StoryDto(
    val id: String,
    val user_id: String,
    val content: String,
    val created_at: String,
    val expires_at: String,
    val background_color: String? = null,
    val text_color: String? = null,
    val font_family: String? = null,
    val users: ProfileDto? = null
)

@JsonClass(generateAdapter = true)
data class StoryViewDto(
    val id: String,
    val story_id: String,
    val viewer_username: String,
    val viewed_at: String
)

@JsonClass(generateAdapter = true)
data class CommentDto(
    val id: String,
    val post_id: String,
    val user_id: String,
    val parent_id: String? = null,
    val content: String,
    val created_at: String,
    val users: ProfileDto? = null
)

@JsonClass(generateAdapter = true)
data class MessageDto(
    val id: String,
    val sender_id: String,
    val conversation_id: String,
    val content: String,
    val created_at: String,
    val is_read: Boolean = false,
    val is_deleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class FollowDto(
    val id: String,
    val follower_id: String,
    val following_id: String,
    val created_at: String
)

@JsonClass(generateAdapter = true)
data class LikeDto(
    val id: String,
    val post_id: String,
    val user_id: String,
    val created_at: String
)

@JsonClass(generateAdapter = true)
data class NotificationDto(
    val id: String,
    val recipient_id: String,
    val sender_id: String,
    val type: String,
    val post_id: String? = null,
    val comment_id: String? = null,
    val is_read: Boolean = false,
    val created_at: String,
    val sender: ProfileDto? = null
)

@JsonClass(generateAdapter = true)
data class TrendingHashtagDto(
    val tag: String,
    val count: Int
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    val refresh_token: String
)

@JsonClass(generateAdapter = true)
data class RecoverPasswordRequest(
    val email: String
)

@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(
    val display_name: String?,
    val bio: String?,
    val is_private: Boolean?
)

@JsonClass(generateAdapter = true)
data class UpdateAvatarRequest(
    val avatar_url: String
)

@JsonClass(generateAdapter = true)
data class UpdateFollowListPrivacyRequest(
    val hide_following_list: Boolean
)

@JsonClass(generateAdapter = true)
data class UpdateUsernameRequest(
    val username: String
)

@JsonClass(generateAdapter = true)
data class CreatePostRequest(
    val user_id: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class LikePostRequest(
    val post_id: String,
    val user_id: String
)

@JsonClass(generateAdapter = true)
data class CreateCommentRequest(
    val post_id: String,
    val user_id: String,
    val content: String,
    val parent_id: String? = null
)

@JsonClass(generateAdapter = true)
data class CommentLikeDto(
    val id: String,
    val comment_id: String,
    val user_id: String,
    val created_at: String
)

@JsonClass(generateAdapter = true)
data class CreateCommentLikeRequest(
    val comment_id: String,
    val user_id: String
)

@JsonClass(generateAdapter = true)
data class FollowUserRequest(
    val follower_id: String,
    val following_id: String
)

@JsonClass(generateAdapter = true)
data class CreateStoryRequest(
    val user_id: String,
    val content: String,
    val expires_at: String,
    val background_color: String = "#000000",
    val text_color: String = "#FFFFFF",
    val font_family: String = "default"
)

@JsonClass(generateAdapter = true)
data class RecordStoryViewRequest(
    val story_id: String,
    val viewer_username: String
)

@JsonClass(generateAdapter = true)
data class SendMessageRequest(
    val conversation_id: String,
    val sender_id: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class UpsertConversationRequest(
    val id: String,
    val user1_id: String,
    val user2_id: String
)

@JsonClass(generateAdapter = true)
data class MarkAsReadRequest(
    val is_read: Boolean = true
)

@JsonClass(generateAdapter = true)
data class DeleteMessageRequest(
    val is_deleted: Boolean = true,
    val content: String = "Pesan ini telah dihapus"
)

@JsonClass(generateAdapter = true)
data class CreateNotificationRequest(
    val recipient_id: String,
    val sender_id: String,
    val type: String,
    val post_id: String? = null,
    val comment_id: String? = null,
    val is_read: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ConversationDto(
    val id: String,
    val user1_id: String,
    val user2_id: String,
    val created_at: String,
    val updated_at: String,
    val messages: List<MessageDto> = emptyList()
)