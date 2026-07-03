package com.textsocial.app.presentation.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val CREATE_POST = "create_post"
    const val POST_DETAIL = "post_detail/{postId}"
    const val STORY_VIEW = "story_view"
    const val CREATE_STORY = "create_story"
    const val PROFILE = "profile/{userId}"
    const val EDIT_PROFILE = "edit_profile"
    const val DM_LIST = "dm_list"
    const val DM_CHAT = "dm_chat/{userId}/{username}"
    const val NOTIFICATIONS = "notifications"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    fun postDetail(postId: String): String = "post_detail/$postId"
    fun profile(userId: String): String = "profile/$userId"
    fun dmChat(userId: String, username: String): String = "dm_chat/$userId/$username"
}
