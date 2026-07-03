package com.textsocial.app.data.api

import android.content.Context
import com.textsocial.app.BuildConfig
import com.textsocial.app.data.local.EncryptedPreferencesManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object SupabaseClient {

    var SUPABASE_URL: String = BuildConfig.SUPABASE_URL
    var SUPABASE_ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY

    fun createService(context: Context): SupabaseApiService {
        val prefs = EncryptedPreferencesManager(context)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")

                // Add bearer auth if token exists
                prefs.getToken()?.let { token ->
                    if (token.isNotEmpty()) {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                }

                chain.proceed(requestBuilder.build())
            }
            .authenticator(object : Authenticator {
                override fun authenticate(route: Route?, response: Response): Request? {
                    val refreshToken = prefs.getRefreshToken() ?: return null
                    synchronized(this) {
                        // Check if the token was already refreshed by another thread
                        val currentToken = prefs.getToken()
                        val requestToken = response.request.header("Authorization")?.replace("Bearer ", "")
                        if (currentToken != requestToken && currentToken != null) {
                            return response.request.newBuilder()
                                .header("Authorization", "Bearer $currentToken")
                                .build()
                        }

                        try {
                            val json = "{\"refresh_token\":\"$refreshToken\"}"
                            val mediaType = "application/json".toMediaTypeOrNull()
                            val body = mediaType?.let { json.toRequestBody(it) } ?: json.toRequestBody()
                            val baseUrl = if (SUPABASE_URL.endsWith("/")) SUPABASE_URL else "$SUPABASE_URL/"
                            val refreshRequest = Request.Builder()
                                .url("${baseUrl}auth/v1/token?grant_type=refresh_token")
                                .post(body)
                                .addHeader("apikey", SUPABASE_ANON_KEY)
                                .build()

                            val refreshClient = OkHttpClient.Builder()
                                .connectTimeout(15, TimeUnit.SECONDS)
                                .readTimeout(15, TimeUnit.SECONDS)
                                .build()

                            val refreshResponse = refreshClient.newCall(refreshRequest).execute()
                            if (refreshResponse.isSuccessful) {
                                val bodyString = refreshResponse.body?.string() ?: ""
                                val jsonObject = JSONObject(bodyString)
                                val newAccessToken = jsonObject.getString("access_token")
                                val newRefreshToken = jsonObject.getString("refresh_token")

                                prefs.saveToken(newAccessToken)
                                prefs.saveRefreshToken(newRefreshToken)

                                return response.request.newBuilder()
                                    .header("Authorization", "Bearer $newAccessToken")
                                    .build()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        return null
                    }
                }
            })
            .addInterceptor(loggingInterceptor)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(if (SUPABASE_URL.endsWith("/")) SUPABASE_URL else "$SUPABASE_URL/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(SupabaseApiService::class.java)
    }
}

