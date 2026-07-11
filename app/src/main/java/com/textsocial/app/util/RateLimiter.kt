package com.textsocial.app.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Rate limiter sisi client, sederhana: cooldown minimum antar aksi per KEY (mis. per jenis
 * aksi, atau per jenis+target kalau mau lebih granular). Ini LAPISAN PERTAMA saja untuk
 * mencegah spam tap yang tidak sengaja (mis. double-tap cepat, atau user yang nge-spam tombol
 * post/comment/like) -- BUKAN pengganti rate limiting di sisi server/Postgres RLS, yang tetap
 * jadi pertahanan sesungguhnya terhadap abuse yang disengaja (client bisa saja di-bypass).
 *
 * Dipakai di ViewModel sebelum memanggil repository, supaya UI langsung kasih tahu user
 * ("Terlalu cepat, coba lagi sebentar") tanpa perlu bolak-balik ke server dulu.
 */
object RateLimiter {

    // Cooldown default per jenis aksi (ms). Post & komentar dikasih jeda lebih lama karena
    // biayanya lebih mahal (insert + refetch) dibanding like yang cuma toggle boolean.
    const val COOLDOWN_CREATE_POST_MS = 3_000L
    const val COOLDOWN_CREATE_COMMENT_MS = 2_000L
    const val COOLDOWN_LIKE_MS = 400L

    private val lastActionAt = ConcurrentHashMap<String, Long>()

    /**
     * @return true kalau aksi dengan [key] boleh jalan sekarang (dan langsung mencatat
     *   waktunya), false kalau masih dalam cooldown dan aksi harus ditolak/di-drop.
     */
    fun tryAcquire(key: String, cooldownMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val previous = lastActionAt.put(key, now)
        if (previous != null && now - previous < cooldownMs) {
            // Kembalikan timestamp lama supaya tap berikutnya masih dihitung dari
            // aksi yang BENAR-BENAR berhasil, bukan dari percobaan yang ditolak ini.
            lastActionAt[key] = previous
            return false
        }
        return true
    }
}