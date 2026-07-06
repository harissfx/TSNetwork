package com.textsocial.app.presentation.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val MAIN = "main"
    const val HOME = "home"
    const val CREATE_POST = "create_post"
    const val POST_DETAIL = "post_detail/{postId}?commentId={commentId}"
    const val STORY_VIEW = "story_view"
    const val CREATE_STORY = "create_story"
    const val PROFILE = "profile/{userId}"
    const val EDIT_PROFILE = "edit_profile"
    const val DM_LIST = "dm_list"
    const val DM_CHAT = "dm_chat/{userId}/{username}"
    const val NOTIFICATIONS = "notifications"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    // commentId opsional: dipakai supaya PostDetailScreen bisa auto-scroll + highlight
    // ke komentar tertentu kalau dibuka dari notifikasi komentar/reply.
    fun postDetail(postId: String, commentId: String? = null): String =
        if (commentId != null) "post_detail/$postId?commentId=$commentId" else "post_detail/$postId"
    fun profile(userId: String): String = "profile/$userId"
    fun dmChat(userId: String, username: String): String = "dm_chat/$userId/$username"
}