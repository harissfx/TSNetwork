package com.textsocial.app.di

import android.content.Context
import com.textsocial.app.data.api.SupabaseClient
import com.textsocial.app.data.local.EncryptedPreferencesManager
import com.textsocial.app.data.local.db.AppDatabase
import com.textsocial.app.data.repository.*
import com.textsocial.app.domain.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ServiceLocator {

    private var context: Context? = null

    fun init(appContext: Context) {
        context = appContext.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                encryptedPreferencesManager.preInitialize()
                val unusedService = apiService
                val unusedDb = appDatabase
                val unusedAuth = authRepository
            } catch (e: Exception) {
            }
        }
    }

    private fun getContext(): Context {
        return context ?: throw IllegalStateException("ServiceLocator is not initialized. Call init(context) first.")
    }

    val encryptedPreferencesManager: EncryptedPreferencesManager by lazy {
        EncryptedPreferencesManager(getContext())
    }

    val apiService by lazy {
        SupabaseClient.createService(getContext())
    }

    /** Database Room untuk cache lokal (posts, profil, pesan, dll) supaya
     *  aplikasi tidak selalu memukul server tiap buka layar yang sama. */
    val appDatabase: AppDatabase by lazy {
        AppDatabase.getInstance(getContext())
    }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(apiService, encryptedPreferencesManager, getContext(), appDatabase)
    }

    val postRepository: PostRepository by lazy {
        PostRepositoryImpl(apiService, encryptedPreferencesManager, getContext(), appDatabase)
    }

    val storyRepository: StoryRepository by lazy {
        StoryRepositoryImpl(apiService, encryptedPreferencesManager, getContext(), appDatabase)
    }

    val messageRepository: MessageRepository by lazy {
        MessageRepositoryImpl(apiService, encryptedPreferencesManager, getContext(), appDatabase)
    }

    val userRepository: UserRepository by lazy {
        UserRepositoryImpl(apiService, encryptedPreferencesManager, getContext(), appDatabase)
    }

    val appUpdateRepository: AppUpdateRepository by lazy {
        AppUpdateRepositoryImpl(apiService, encryptedPreferencesManager)
    }
}