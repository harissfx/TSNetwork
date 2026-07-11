package com.textsocial.app.data.api

import android.content.Context
import com.textsocial.app.BuildConfig
import com.textsocial.app.data.local.EncryptedPreferencesManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.CacheControl
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

        // HANYA aktif di build debug. Level BODY mencetak seluruh request/response --
        // termasuk header Authorization (access token) & payload (mis. email, pesan DM) --
        // ke Logcat. Di build release ini bisa kebaca lewat `adb logcat` di device mana pun
        // yang punya USB debugging aktif, jadi WAJIB dimatikan sebelum rilis.
        val loggingInterceptor = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        } else null

        // Disk cache HTTP (lapisan tambahan di luar cache Room aplikasi).
        // Berguna untuk request GET identik yang terjadi berdekatan waktu
        // (mis. dua screen yang sama-sama minta profil user yang sama),
        // dan sebagai fallback singkat saat koneksi terputus sesaat.
        val httpCache = Cache(
            directory = java.io.File(context.cacheDir, "http_cache"),
            maxSize = 10L * 1024 * 1024 // 10 MB
        )

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cache(httpCache)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                if (originalRequest.header("Content-Type") == null) {
                    requestBuilder.addHeader("Content-Type", "application/json")
                }

                prefs.getToken()?.let { token ->
                    if (token.isNotEmpty()) {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                }

                chain.proceed(requestBuilder.build())
            }
            // Supabase REST tidak mengirim header Cache-Control, jadi OkHttp
            // tidak akan pernah menyimpan responsnya ke disk cache tanpa ini.
            // Network interceptor menambahkan Cache-Control singkat khusus
            // untuk request GET supaya request identik yang terjadi hampir
            // bersamaan tidak perlu menembus jaringan dua kali.
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (request.method == "GET") {
                    response.newBuilder()
                        .header("Cache-Control", "public, max-age=15")
                        .removeHeader("Pragma")
                        .build()
                } else {
                    response
                }
            }
            .authenticator(object : Authenticator {
                override fun authenticate(route: Route?, response: Response): Request? {
                    val refreshToken = prefs.getRefreshToken() ?: return null
                    synchronized(this) {
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
            .apply {
                // Ditambahkan PALING TERAKHIR (kalau ada) supaya yang ke-log adalah request
                // final setelah semua interceptor lain (auth header, dsb) diterapkan.
                loggingInterceptor?.let { addInterceptor(it) }
            }
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