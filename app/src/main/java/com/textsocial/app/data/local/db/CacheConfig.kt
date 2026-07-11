package com.textsocial.app.data.local

import java.util.concurrent.TimeUnit

/**
 * Pusat pengaturan "berapa lama data boleh dianggap masih segar" (Time To Live)
 * sebelum aplikasi mengambil data baru dari server lagi.
 *
 * Semakin besar nilainya, semakin jarang aplikasi memanggil server -> makin
 * ringan beban server, tapi data yang ditampilkan makin mungkin sedikit basi.
 * Nilai-nilai di bawah ini dipilih berdasarkan seberapa sering data tsb
 * biasanya berubah.
 */
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

    // Meta cache keys untuk data berbentuk list (lihat CacheMetaEntity)
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