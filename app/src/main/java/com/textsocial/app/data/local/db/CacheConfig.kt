package com.textsocial.app.data.local

import java.util.concurrent.TimeUnit

object CacheConfig {
    val POSTS_TTL_MS = TimeUnit.MINUTES.toMillis(2)
    val PROFILE_TTL_MS = TimeUnit.MINUTES.toMillis(10)
    val FOLLOW_COUNTS_TTL_MS = TimeUnit.MINUTES.toMillis(5)
    val STORIES_TTL_MS = TimeUnit.MINUTES.toMillis(2)
    val CONVERSATIONS_TTL_MS = TimeUnit.SECONDS.toMillis(30)
    val MESSAGES_TTL_MS = TimeUnit.SECONDS.toMillis(20)
    val NOTIFICATIONS_TTL_MS = TimeUnit.MINUTES.toMillis(1)
    val TRENDING_HASHTAGS_TTL_MS = TimeUnit.MINUTES.toMillis(5)
    val LINK_PREVIEW_TTL_MS = TimeUnit.HOURS.toMillis(24)

    const val META_KEY_POSTS = "posts_all"
    const val META_KEY_STORIES = "stories_all"
    const val META_KEY_CONVERSATIONS = "conversations_all"
    const val META_KEY_NOTIFICATIONS = "notifications_all"
    const val META_KEY_TRENDING_HASHTAGS = "trending_hashtags_all"
    fun messagesMetaKey(conversationId: String) = "messages_$conversationId"

    fun isFresh(fetchedAt: Long, ttlMs: Long, now: Long = System.currentTimeMillis()): Boolean {
        return now - fetchedAt < ttlMs
    }
}