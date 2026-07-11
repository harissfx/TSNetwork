package com.textsocial.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedPreferencesManager(context: Context) {

    fun preInitialize() {
        val unused = sharedPreferences
    }

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "secure_user_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("secure_user_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun saveToken(token: String) {
        sharedPreferences.edit().putString(KEY_JWT_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return sharedPreferences.getString(KEY_JWT_TOKEN, null)
    }

    fun saveRefreshToken(token: String) {
        sharedPreferences.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    fun getRefreshToken(): String? {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    }

    fun saveUserId(userId: String) {
        sharedPreferences.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun getUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }

    fun saveUsername(username: String) {
        sharedPreferences.edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(): String? {
        return sharedPreferences.getString(KEY_USERNAME, null)
    }

    fun saveUserAvatarColor(color: String) {
        sharedPreferences.edit().putString(KEY_AVATAR_COLOR, color).apply()
    }

    fun getUserAvatarColor(): String {
        return sharedPreferences.getString(KEY_AVATAR_COLOR, "#FF5722") ?: "#FF5722"
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    /** Version_code terakhir yang di-dismiss lewat tombol "Nanti" di popup update, supaya
     *  popup tidak nongol lagi tiap app dibuka untuk versi yang sama -- tetap muncul lagi
     *  begitu ada version_code baru yang lebih besar dari nilai ini. */
    fun saveDismissedUpdateVersionCode(versionCode: Int) {
        sharedPreferences.edit().putInt(KEY_DISMISSED_UPDATE_VERSION_CODE, versionCode).apply()
    }

    fun getDismissedUpdateVersionCode(): Int {
        return sharedPreferences.getInt(KEY_DISMISSED_UPDATE_VERSION_CODE, 0)
    }

    companion object {
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_AVATAR_COLOR = "avatar_color"
        private const val KEY_DISMISSED_UPDATE_VERSION_CODE = "dismissed_update_version_code"
    }
}