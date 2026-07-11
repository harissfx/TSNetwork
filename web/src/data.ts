import { FeatureItem, ValueProposition, DocSection } from "./types";

export const VALUE_PROPOSITIONS: ValueProposition[] = [
  {
    title: "Ringan Tanpa Media",
    description: "Postingan teks murni (maks 3000 karakter) memastikan konsumsi kuota yang minim dan kecepatan loading super instan tanpa bloatware gambar/video.",
    iconName: "Zap"
  },
  {
    title: "Privasi Utama",
    description: "Kami tidak melacak Anda, tidak menjual data Anda, dan tidak menampilkan iklan. Anda memegang kendali penuh dengan fitur Akun Privat.",
    iconName: "Shield"
  },
  {
    title: "100% Open Source",
    description: "Seluruh kode sumber aplikasi Android dan konfigurasi backend terbuka lebar untuk diaudit, dipelajari, maupun dikembangkan oleh komunitas.",
    iconName: "Code"
  },
  {
    title: "Bebas & Gratis",
    description: "Didesain sebagai media komunikasi murni, OpenText sepenuhnya gratis untuk digunakan tanpa ada biaya tersembunyi atau pembelian dalam aplikasi.",
    iconName: "Heart"
  }
];

export const APP_FEATURES: FeatureItem[] = [
  {
    id: "post_teks",
    title: "Post Teks Murni",
    description: "Ekspresikan pemikiran Anda hingga 3000 karakter lengkap dengan dukungan tagar (#) dan pemformatan teks minimalis.",
    iconName: "FileText"
  },
  {
    id: "stories_24h",
    title: "Cerita Teks 24 Jam",
    description: "Bagikan status temporal yang akan terhapus otomatis dalam 24 jam, lengkap dengan kustomisasi font dan warna latar belakang yang ekspresif.",
    iconName: "Clock"
  },
  {
    id: "komentar_berjenjang",
    title: "Komentar Berjenjang",
    description: "Lakukan diskusi mendalam yang rapi dengan sistem balasan (reply) berulir yang terorganisir dengan baik.",
    iconName: "MessageSquareCode"
  },
  {
    id: "like_system",
    title: "Sistem Reaksi & Like",
    description: "Berikan tanda suka pada postingan atau komentar favorit Anda dengan sinkronisasi instan.",
    iconName: "HeartHandshake"
  },
  {
    id: "follow_privacy",
    title: "Follower & Akun Privat",
    description: "Bangun jaringan pertemanan Anda. Aktifkan opsi Akun Privat untuk menyetujui permintaan follow secara manual.",
    iconName: "Users"
  },
  {
    id: "realtime_dm",
    title: "Direct Message Real-Time",
    description: "Kirim pesan teks langsung ke teman Anda dengan kecepatan pesan masuk real-time, didukung penuh oleh Supabase Realtime Channels.",
    iconName: "Send"
  },
  {
    id: "push_notifications",
    title: "Notifikasi Instan & Push",
    description: "Tetap terhubung dengan push notification FCM untuk aktivitas penting seperti chat masuk, like, komentar baru, dan pengikut baru.",
    iconName: "Bell"
  },
  {
    id: "link_preview",
    title: "Preview Link Otomatis",
    description: "Bagikan tautan web (URL) dan sistem akan secara otomatis memunculkan cuplikan judul, deskripsi, dan metadata web layaknya aplikasi perpesanan modern.",
    iconName: "Link"
  },
  {
    id: "dark_mode_lang",
    title: "Dark Mode & Multi-Bahasa",
    description: "Antarmuka gelap bawaan yang ramah di mata di malam hari, dilengkapi pilihan bahasa pengantar untuk audiens lokal maupun global.",
    iconName: "Languages"
  },
  {
    id: "inapp_updates",
    title: "Pengecekan Update In-App",
    description: "Mengingat aplikasi diinstal via APK sideload, OpenText memiliki modul bawaan untuk mengecek pembaruan versi baru secara otomatis demi keamanan Anda.",
    iconName: "Sparkles"
  }
];

export const DOC_SECTIONS: DocSection[] = [
  {
    id: "arsitektur",
    title: "1. Arsitektur & Alur Sistem",
    description: "OpenText dibangun dengan Clean Architecture + MVVM di sisi Android (Kotlin, Jetpack Compose). Berbeda dari kebanyakan proyek Supabase-Android lain, OpenText TIDAK memakai SDK supabase-kt, melainkan berbicara langsung ke REST API Supabase (PostgREST & GoTrue) lewat Retrofit + Moshi, plus koneksi WebSocket manual (OkHttp) ke endpoint Supabase Realtime untuk chat. Push notification berjalan lewat Firebase Cloud Messaging yang dipicu Database Webhook + Supabase Edge Function.",
    codeSnippet: `// Gambaran Alur Komunikasi Data
[Android App (Kotlin, Jetpack Compose, MVVM)]
       │
       ├─► Retrofit + Moshi ──► PostgREST & GoTrue (REST API Supabase)
       ├─► OkHttp WebSocket ──► Supabase Realtime (protokol Phoenix, khusus DM)
       └─► Firebase Cloud Messaging ◄── Database Webhook ◄── Edge Function "send-push"
`,
    language: "text"
  },
  {
    id: "supabase_schema",
    title: "2. Skema Database Supabase (PostgreSQL)",
    description: "Cuplikan skema inti dari SQL lengkap di README repo (semua tabel pakai Row Level Security). Skrip penuhnya juga membuat trigger otomatis untuk notifikasi (like/comment/follow), rate limiting anti-spam, dan penghitungan likes_count/comments_count.",
    codeSnippet: `-- Profil pengguna, tersinkron otomatis dari auth.users lewat trigger
create table public.profiles (
    id uuid references auth.users on delete cascade primary key,
    username text unique not null,
    email text unique not null,
    display_name text,
    bio text,
    avatar_color text not null default '#FF5722',
    is_private boolean default false,
    is_verified boolean not null default false,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- Postingan teks murni, maksimal 3000 karakter
create table public.posts (
    id uuid default uuid_generate_v4() primary key,
    user_id uuid references public.profiles(id) on delete cascade not null,
    content text not null check (char_length(content) <= 3000),
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    likes_count integer default 0 not null,
    comments_count integer default 0 not null
);

-- Notifikasi (like, comment, follow, mention) -- dibuat otomatis lewat trigger
create table public.notifications (
    id uuid default uuid_generate_v4() primary key,
    recipient_id uuid references public.profiles(id) on delete cascade not null,
    sender_id uuid references public.profiles(id) on delete cascade not null,
    type text not null,
    post_id uuid references public.posts(id) on delete cascade,
    comment_id uuid references public.comments(id) on delete cascade,
    is_read boolean default false not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- Token FCM per device, dipakai Edge Function buat kirim push notification
create table public.device_tokens (
    id uuid default uuid_generate_v4() primary key,
    user_id uuid references public.profiles(id) on delete cascade not null,
    fcm_token text not null unique,
    platform text not null default 'android',
    updated_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- Sumber kebenaran untuk fitur cek update in-app (aplikasi didistribusikan via sideload)
create table public.app_versions (
    id uuid default uuid_generate_v4() primary key,
    platform text not null default 'android',
    version_code integer not null,
    version_name text not null,
    release_notes text,
    download_url text not null,
    min_supported_version_code integer, -- diisi utk memaksa update wajib
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- Skrip lengkap (semua tabel, trigger, RLS policy) ada di README repo`,
    language: "sql"
  },
  {
    id: "retrofit_setup",
    title: "3. Koneksi ke Supabase Lewat Retrofit (Bukan supabase-kt)",
    description: "OpenText sengaja tidak memakai SDK resmi supabase-kt agar dependency lebih ringan dan penuh kontrol atas caching lokal (Room). Semua endpoint Supabase REST (PostgREST) diakses lewat interface Retrofit biasa, dengan header anon key & Bearer JWT dipasang lewat OkHttp Interceptor.",
    codeSnippet: `// SupabaseApiService.kt -- interface Retrofit, representatif
interface SupabaseApiService {
    @GET("rest/v1/posts")
    suspend fun getPosts(
        @Query("order") order: String = "created_at.desc"
    ): Response<List<PostDto>>

    @POST("rest/v1/posts")
    suspend fun createPost(@Body body: CreatePostRequest): Response<Unit>

    @GET("rest/v1/notifications")
    suspend fun getNotifications(
        @Query("recipient_id") recipientId: String
    ): Response<List<NotificationDto>>
}

// SupabaseClient.kt -- OkHttp interceptor pasang API key & JWT otomatis
val client = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer \${prefs.getAccessToken()}")
            .build()
        chain.proceed(request)
    }
    .build()`,
    language: "kotlin"
  },
  {
    id: "realtime_dm_android",
    title: "4. Chat Real-time Lewat WebSocket Manual (Protokol Phoenix)",
    description: "Direct message berjalan real-time dengan membuka koneksi WebSocket langsung (OkHttp) ke endpoint Supabase Realtime, lalu subscribe ke perubahan tabel messages via protokol Phoenix -- bukan lewat library Realtime bawaan supabase-kt.",
    codeSnippet: `// Potongan dari RepositoryImpls.kt (disederhanakan)
val wsEndpoint = "\$supabaseUrl/realtime/v1/websocket?apikey=\$anonKey"
val request = Request.Builder().url(wsEndpoint).build()

wsClient.newWebSocket(request, object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        // Join channel & subscribe ke perubahan tabel "messages"
        // untuk conversation_id tertentu lewat event phx_join
        val joinMsg = JSONObject().apply {
            put("topic", "realtime:messages:\$conversationId")
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("postgres_changes", JSONArray().apply {
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "messages")
                            put("filter", "conversation_id=eq.\$conversationId")
                        })
                    })
                })
            })
        }
        webSocket.send(joinMsg.toString())
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        // Parse event "postgres_changes" -> ambil payload.data.record
        // -> update UI pesan baru secara instan
    }
})`,
    language: "kotlin"
  },
  {
    id: "fcm_push",
    title: "5. Push Notification: Data-Only FCM Message",
    description: "Berbeda dari contoh FCM pada umumnya yang memakai payload 'notification', OpenText sengaja mengirim data-only message dari Edge Function. Ini memastikan onMessageReceived SELALU dipanggil di client -- termasuk saat aplikasi sedang dibuka -- sehingga badge notifikasi & popup update bisa langsung sinkron real-time, bukan cuma saat aplikasi ditutup.",
    codeSnippet: `// push/FcmService.kt
class FcmService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data.isEmpty()) return

        // Data-only message -> fungsi ini SELALU jalan, foreground maupun background
        NotificationHelper.showFromPushData(applicationContext, message.data)

        // Broadcast ke ViewModel yang aktif (badge count, popup update, dsb)
        // via in-memory event bus supaya UI ikut ter-refresh real-time
        NotificationEventBus.notifyPushReceived(message.data["type"] ?: "general")
    }
}

// supabase/functions/send-push/index.ts -- payload FCM data-only (tanpa "notification")
await fetch(\`https://fcm.googleapis.com/v1/projects/\${projectId}/messages:send\`, {
    method: "POST",
    headers: { Authorization: \`Bearer \${accessToken}\` },
    body: JSON.stringify({
        message: {
            token,
            data: { ...data, title, body }, // data-only, bukan top-level "notification"
            android: { priority: data.type === "dm" ? "high" : "normal" }
        }
    })
});`,
    language: "kotlin"
  }
];
