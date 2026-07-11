# OpenText: Aplikasi Media Sosial Khusus Teks

Platform media sosial berbasis teks yang ringan, aman, dan mengutamakan privasi yang dibangun untuk sistem operasi Android. OpenText menghindari beban basis data multimedia (gambar/video) dengan memprioritaskan kecepatan, kemudahan penggunaan, dan isolasi status lokal.

---

## 🛠️ TEKNOLOGI YANG DIGUNAKAN (TECH STACK)

- **Frontend:** Android Studio dengan Jetpack Compose (Kotlin), Material 3, Navigation Compose, Retrofit + Moshi (konversi JSON), Coil (loading gambar), dan `SharedPreferences`/`EncryptedSharedPreferences` untuk penyimpanan lokal (tema, bahasa, token JWT).
- **Backend:** Supabase PostgreSQL Database, Supabase Auth (berbasis JWT), Supabase Realtime untuk pertukaran pesan instan (DM), dan Supabase Storage (bucket `avatars` untuk foto profil).
- **Push Notification:** Firebase Cloud Messaging (FCM) + Supabase Edge Function `send-push`, dipicu lewat Database Webhook (lihat bagian 5).
- **Keamanan:** RLS di semua tabel Supabase, trigger `prevent_self_verification`, trigger rate limiting anti-spam untuk insert post/komentar/like (`enforce_rate_limit`, lihat bagian 1 & 4), token JWT disimpan via `EncryptedSharedPreferences`, dan klien OkHttp dengan `usesCleartextTraffic="false"`.

---

## ⚙️ 0. KONFIGURASI PROYEK

Kredensial Supabase **tidak di-hardcode** — diambil dari `BuildConfig.SUPABASE_URL` dan `BuildConfig.SUPABASE_ANON_KEY` (lihat `SupabaseClient.kt`), digenerate oleh [Secrets Gradle Plugin](https://github.com/google/secrets-gradle-plugin) dari file `.env` di root proyek (`app/build.gradle.kts` → blok `secrets { propertiesFileName = ".env" }`). File `.env` tidak disertakan di repo — isinya:

```properties
SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
SUPABASE_ANON_KEY=isi-anon-public-key-dari-dashboard-supabase
```

Kedua nilai ada di Dashboard Supabase → **Project Settings → API**. `local.properties` cuma berisi `sdk.dir`, bukan tempat kredensial. Untuk push notification, `app/google-services.json` (kredensial Firebase) sudah termasuk di repo.

---

## 💾 1. SKEMA DATABASE & KEBIJAKAN SUPABASE (SQL LENGKAP)

> [!WARNING]
> Skrip ini berisi `drop table ... cascade` — didesain untuk setup dari nol di project Supabase yang masih kosong. **Jangan dijalankan ulang di project yang sudah punya data**, karena akan menghapus semuanya. Untuk menyusulkan tabel/kolom yang belum ada ke project yang sudah jalan, salin manual bagian `create table`/`alter table` yang relevan saja.

Jalankan lewat **Dashboard Supabase → SQL Editor**.

```sql
-- Mengaktifkan ekstensi UUID generator
create extension if not exists "uuid-ossp";

-- Bersihkan tabel lama jika ada (agar skrip ini bisa dijalankan ulang tanpa error)
drop trigger if exists on_auth_user_created on auth.users;
drop table if exists public.app_versions cascade;
drop table if exists public.link_previews cascade;
drop table if exists public.device_tokens cascade;
drop table if exists public.notifications cascade;
drop table if exists public.messages cascade;
drop table if exists public.conversations cascade;
drop table if exists public.follows cascade;
drop table if exists public.likes cascade;
drop table if exists public.comment_likes cascade;
drop table if exists public.comments cascade;
drop table if exists public.story_views cascade;
drop table if exists public.stories cascade;
drop table if exists public.posts cascade;
drop table if exists public.profiles cascade;

-- 1. TABEL PROFIL (PROFILES)
create table public.profiles (
    id uuid references auth.users on delete cascade primary key,
    username text unique not null,
    email text unique not null,
    display_name text,
    bio text,
    avatar_color text not null default '#FF5722',
    avatar_url text,
    is_private boolean default false,
    is_verified boolean not null default false,
    hide_following_list boolean not null default false,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    updated_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- 2. TABEL POSTINGAN (POSTS)
create table public.posts (
    id uuid default uuid_generate_v4() primary key,
    user_id uuid references public.profiles(id) on delete cascade not null,
    content text not null check (char_length(content) <= 3000),
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    likes_count integer default 0 not null,
    comments_count integer default 0 not null
);

-- 3. TABEL CERITA / STATUS 24 JAM (STORIES)
create table public.stories (
    id uuid default uuid_generate_v4() primary key,
    user_id uuid references public.profiles(id) on delete cascade not null,
    content text not null check (char_length(content) <= 280),
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    expires_at timestamp with time zone not null,
    -- Kustomisasi tampilan story yang dipilih pengguna saat membuat: warna latar,
    -- warna teks, dan font. Defaultnya hitam/putih/default, sama seperti tampilan
    -- story sebelum fitur kustomisasi ini ada.
    background_color text not null default '#000000',
    text_color text not null default '#FFFFFF',
    font_family text not null default 'default'
);

-- 4. TABEL RIWAYAT DILIHAT UNTUK CERITA (STORY VIEWS)
create table public.story_views (
    id uuid default uuid_generate_v4() primary key,
    story_id uuid references public.stories(id) on delete cascade not null,
    viewer_username text not null,
    viewed_at timestamp with time zone default timezone('utc'::text, now()) not null,
    unique(story_id, viewer_username)
);

-- 5. TABEL KOMENTAR (COMMENTS)
create table public.comments (
    id uuid default uuid_generate_v4() primary key,
    post_id uuid references public.posts(id) on delete cascade not null,
    user_id uuid references public.profiles(id) on delete cascade not null,
    parent_id uuid references public.comments(id) on delete cascade, -- diisi kalau komentar ini adalah balasan (reply) dari komentar lain
    content text not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- 5B. TABEL LIKE KOMENTAR (COMMENT_LIKES)
create table public.comment_likes (
    id uuid default uuid_generate_v4() primary key,
    comment_id uuid references public.comments(id) on delete cascade not null,
    user_id uuid references public.profiles(id) on delete cascade not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    unique(comment_id, user_id)
);

-- 6. TABEL LIKES (LIKES)
create table public.likes (
    id uuid default uuid_generate_v4() primary key,
    post_id uuid references public.posts(id) on delete cascade not null,
    user_id uuid references public.profiles(id) on delete cascade not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    unique(post_id, user_id)
);

-- 7. TABEL MENGIKUTI (FOLLOWS)
create table public.follows (
    id uuid default uuid_generate_v4() primary key,
    follower_id uuid references public.profiles(id) on delete cascade not null,
    following_id uuid references public.profiles(id) on delete cascade not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    unique(follower_id, following_id)
);

-- 8. TABEL PERCAKAPAN CHAT (CONVERSATIONS)
create table public.conversations (
    id text primary key, -- Format ID: "user1Id_user2Id" (diurutkan secara alfabetis dari aplikasi)
    user1_id uuid references public.profiles(id) on delete cascade not null,
    user2_id uuid references public.profiles(id) on delete cascade not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    updated_at timestamp with time zone default timezone('utc'::text, now()) not null,
    -- Fitur "hapus chat" di daftar DM: bukan hapus beneran, cuma disembunyikan dari
    -- daftar milik salah satu pihak saja. Lawan bicara tetap bisa melihat percakapannya,
    -- dan kalau ada pesan baru masuk, kedua flag ini otomatis di-reset ke false lagi
    -- (lihat trigger handle_message_inserted di bawah).
    hidden_for_user1 boolean not null default false,
    hidden_for_user2 boolean not null default false,
    unique(user1_id, user2_id)
);

-- 9. TABEL PESAN CHAT (MESSAGES)
create table public.messages (
    id uuid default uuid_generate_v4() primary key,
    conversation_id text references public.conversations(id) on delete cascade not null,
    sender_id uuid references public.profiles(id) on delete cascade not null,
    content text not null,
    is_read boolean default false not null,
    is_deleted boolean default false not null, -- fitur "hapus pesan untuk semua orang": bukan dihapus beneran, cuma ditandai + kontennya diganti placeholder
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- 10. TABEL NOTIFIKASI (NOTIFICATIONS)
create table public.notifications (
    id uuid default uuid_generate_v4() primary key,
    recipient_id uuid references public.profiles(id) on delete cascade not null,
    sender_id uuid references public.profiles(id) on delete cascade not null,
    type text not null, -- 'like', 'comment', 'follow', 'mention'
    post_id uuid references public.posts(id) on delete cascade,
    comment_id uuid references public.comments(id) on delete cascade,
    is_read boolean default false not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- 11. TABEL TOKEN PERANGKAT UNTUK PUSH NOTIFICATION (DEVICE_TOKENS)
-- Menyimpan token FCM (Firebase Cloud Messaging) tiap device yang login, supaya backend
-- tahu ke mana push notification harus dikirim -- termasuk saat aplikasi tertutup.
-- Satu token cuma boleh terikat ke satu baris (unique): kalau device yang sama daftar
-- ulang (misal tiap kali app dibuka), baris lama di-update, bukan bikin baris baru.
create table public.device_tokens (
    id uuid default uuid_generate_v4() primary key,
    user_id uuid references public.profiles(id) on delete cascade not null,
    fcm_token text not null unique,
    platform text not null default 'android',
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    updated_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- 12. TABEL CACHE PREVIEW LINK (LINK_PREVIEWS)
-- Cache metadata Open Graph (judul, deskripsi, gambar) dari URL yang dibagikan di postingan,
-- supaya app tidak perlu fetch & parse HTML situs lain berulang kali tiap kali post ditampilkan.
-- Diisi HANYA oleh Edge Function `link-preview` (lewat service_role) -- lihat bagian 6.
create table public.link_previews (
    url text primary key,
    title text,
    description text,
    image_url text,
    site_name text,
    fetch_failed boolean not null default false,
    fetched_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- 13. TABEL VERSI APLIKASI (APP_VERSIONS)
-- Sumber kebenaran untuk fitur "cek update" -- app membandingkan BuildConfig.VERSION_CODE
-- miliknya dengan baris platform='android' yang version_code-nya PALING BESAR di tabel ini.
-- Diisi MANUAL oleh pemilik project tiap kali rilis baru (lewat Supabase Dashboard/SQL
-- Editor), bukan lewat app -- lihat bagian 7. Insert baris baru ke tabel ini juga otomatis
-- memicu broadcast push notification ke SEMUA device lewat Database Webhook (lihat bagian 5).
create table public.app_versions (
    id uuid default uuid_generate_v4() primary key,
    platform text not null default 'android',
    version_code integer not null,
    version_name text not null,
    release_notes text,
    download_url text not null,
    -- Kalau diisi (bukan null) dan version_code app di HP user < nilai ini, dialog update
    -- jadi WAJIB (tidak bisa di-skip/dismiss). Biarkan null untuk update biasa (opsional).
    min_supported_version_code integer,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);


-- INDEKS OPTIMALISASI KINERJA (PERFORMANCE INDEXES)
create index idx_posts_created_at on public.posts(created_at desc);
create index idx_messages_conversation_id on public.messages(conversation_id);
create index idx_stories_expires_at on public.stories(expires_at);
create index idx_likes_post_id on public.likes(post_id);
create index idx_comments_post_id on public.comments(post_id);
create index idx_comment_likes_comment_id on public.comment_likes(comment_id);
create index idx_notifications_recipient_id on public.notifications(recipient_id);
create index idx_device_tokens_user_id on public.device_tokens(user_id);
create index idx_app_versions_platform_version_code on public.app_versions(platform, version_code desc);
-- unique(follower_id, following_id) di tabel follows otomatis bikin index dengan
-- follower_id di depan (dipakai getFollowing), TAPI TIDAK meng-cover query yang filter
-- by following_id saja (dipakai getFollowers/checkFollowExists/getFollowersCount) --
-- tanpa index ini, query itu full table scan begitu tabel follows membesar.
create index idx_follows_following_id on public.follows(following_id);


-- FUNGSI & TRIGGER DATABASE OTOMATIS (TRIGGERS)

-- 1. Menghitung Jumlah Like Otomatis pada Postingan
create or replace function public.handle_like_change()
returns trigger as $$
begin
    if (TG_OP = 'INSERT') then
        update public.posts
        set likes_count = likes_count + 1
        where id = new.post_id;
    elsif (TG_OP = 'DELETE') then
        update public.posts
        set likes_count = greatest(0, likes_count - 1)
        where id = old.post_id;
    end if;
    return null;
end;
$$ language plpgsql security definer;

create trigger on_like_change
after insert or delete on public.likes
for each row execute function public.handle_like_change();

-- 2. Menghitung Jumlah Komentar Otomatis pada Postingan
create or replace function public.handle_comment_change()
returns trigger as $$
begin
    if (TG_OP = 'INSERT') then
        update public.posts
        set comments_count = comments_count + 1
        where id = new.post_id;
    elsif (TG_OP = 'DELETE') then
        update public.posts
        set comments_count = greatest(0, comments_count - 1)
        where id = old.post_id;
    end if;
    return null;
end;
$$ language plpgsql security definer;

create trigger on_comment_change
after insert or delete on public.comments
for each row execute function public.handle_comment_change();

-- 3. Memperbarui Kolom updated_at pada Percakapan secara otomatis saat pesan baru dikirim,
--    sekaligus memunculkan lagi chat yang sempat disembunyikan (dihapus dari daftar DM) oleh
--    salah satu / kedua pihak, supaya pesan baru selalu terlihat -- mirip aplikasi chat lain.
create or replace function public.handle_message_inserted()
returns trigger as $$
begin
    update public.conversations
    set updated_at = timezone('utc'::text, now()),
        hidden_for_user1 = false,
        hidden_for_user2 = false
    where id = new.conversation_id;
    return new;
end;
$$ language plpgsql security definer;

create trigger on_message_inserted
after insert on public.messages
for each row execute function public.handle_message_inserted();

-- 4. Membuat Profil Pengguna Baru secara otomatis di public.profiles saat mendaftar lewat Supabase Auth
create or replace function public.handle_new_user()
returns trigger as $$
begin
    insert into public.profiles (id, username, email, display_name, avatar_color, bio, is_private)
    values (
        new.id,
        coalesce(new.raw_user_meta_data->>'username', split_part(new.email, '@', 1)),
        new.email,
        coalesce(new.raw_user_meta_data->>'display_name', split_part(new.email, '@', 1)),
        '#FF5722',
        'Welcome to my profile!',
        false
    )
    on conflict (id) do nothing;
    return new;
end;
$$ language plpgsql security definer;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

-- 5. Membuat Notifikasi Otomatis saat Ada Like Baru
create or replace function public.handle_new_like_notification()
returns trigger as $$
declare
    post_owner uuid;
begin
    select user_id into post_owner from public.posts where id = new.post_id;
    if post_owner is not null and post_owner != new.user_id then
        insert into public.notifications (recipient_id, sender_id, type, post_id)
        values (post_owner, new.user_id, 'like', new.post_id);
    end if;
    return new;
end;
$$ language plpgsql security definer;

create trigger on_new_like_notify
after insert on public.likes
for each row execute function public.handle_new_like_notification();

-- 6. Membuat Notifikasi Otomatis saat Ada Komentar Baru
create or replace function public.handle_new_comment_notification()
returns trigger as $$
declare
    post_owner uuid;
begin
    select user_id into post_owner from public.posts where id = new.post_id;
    if post_owner is not null and post_owner != new.user_id then
        insert into public.notifications (recipient_id, sender_id, type, post_id, comment_id)
        values (post_owner, new.user_id, 'comment', new.post_id, new.id);
    end if;
    return new;
end;
$$ language plpgsql security definer;

create trigger on_new_comment_notify
after insert on public.comments
for each row execute function public.handle_new_comment_notification();

-- 7. Membuat Notifikasi Otomatis saat Ada Follower Baru
create or replace function public.handle_new_follow_notification()
returns trigger as $$
begin
    insert into public.notifications (recipient_id, sender_id, type)
    values (new.following_id, new.follower_id, 'follow');
    return new;
end;
$$ language plpgsql security definer;

create trigger on_new_follow_notify
after insert on public.follows
for each row execute function public.handle_new_follow_notification();

-- 8. Membuat Baris Percakapan (Conversations) secara Otomatis sebelum Pesan Pertama Dikirim
-- Klien Android hanya mengirim pesan dengan conversation_id gabungan ("user1Id_user2Id")
-- tanpa pernah membuat baris di tabel conversations terlebih dahulu. Trigger BEFORE INSERT
-- ini mencegah kegagalan foreign key / RLS pada pesan pertama antara dua pengguna baru.
create or replace function public.handle_message_before_insert()
returns trigger as $$
declare
    uid1 uuid;
    uid2 uuid;
begin
    uid1 := split_part(new.conversation_id, '_', 1)::uuid;
    uid2 := split_part(new.conversation_id, '_', 2)::uuid;
    insert into public.conversations (id, user1_id, user2_id)
    values (new.conversation_id, uid1, uid2)
    on conflict (id) do nothing;
    return new;
end;
$$ language plpgsql security definer;

create trigger on_message_before_insert
before insert on public.messages
for each row execute function public.handle_message_before_insert();

-- 9b. Perbarui kolom updated_at di device_tokens setiap kali token yang sama didaftarkan ulang
create or replace function public.handle_device_token_updated()
returns trigger as $$
begin
    new.updated_at = timezone('utc'::text, now());
    return new;
end;
$$ language plpgsql security definer;

create trigger on_device_token_updated
before update on public.device_tokens
for each row execute function public.handle_device_token_updated();

-- 9. Cegah user biasa mengubah status verifikasi (is_verified) miliknya sendiri
--    lewat app, walaupun policy "Allow owners to edit profile" mengizinkan
--    mereka update baris profil sendiri. Perubahan is_verified hanya "nempel"
--    kalau dilakukan dari SQL Editor / Table Editor Supabase (bukan lewat
--    request API yang membawa JWT role 'authenticated').
create or replace function public.prevent_self_verification()
returns trigger as $$
begin
    if new.is_verified is distinct from old.is_verified then
        if auth.role() = 'authenticated' then
            new.is_verified := old.is_verified;
        end if;
    end if;
    return new;
end;
$$ language plpgsql security definer;

drop trigger if exists enforce_verified_badge on public.profiles;
create trigger enforce_verified_badge
before update on public.profiles
for each row execute function public.prevent_self_verification();

-- 10. RATE LIMITING (ANTI-SPAM) DI LEVEL DATABASE untuk insert post/komentar/like.
--     Client Android sudah punya cooldown ringan sebelum request dikirim (lihat RateLimiter.kt
--     di kode aplikasi) supaya UI langsung kasih feedback tanpa nunggu round-trip -- tapi itu
--     CUMA lapisan UX, gampang dilewati kalau request dikirim di luar app (mis. curl pakai
--     token yang di-intercept, client custom, dsb). Trigger di bawah ini jalan di database,
--     jadi berlaku untuk SEMUA jalur insert apa pun clientnya -- ini pertahanan yang
--     sesungguhnya, RateLimiter.kt di app cuma UX tambahan di atasnya.
--
--     Fungsi generik, dipakai ulang di 3 tabel lewat argumen trigger (TG_ARGV): argumen
--     pertama = jumlah maksimum insert yang diizinkan, argumen kedua = panjang window waktu.
--     Threshold sengaja dibuat lebih longgar dari cooldown di client (yang menahan burst tap
--     dalam hitungan detik) -- trigger ini jaring pengaman terhadap penyalahgunaan yang
--     lebih gigih/otomatis dalam window yang lebih panjang, bukan menduplikasi cooldown UX.
create or replace function public.enforce_rate_limit()
returns trigger as $$
declare
    v_max_count int := TG_ARGV[0]::int;
    v_window interval := TG_ARGV[1]::interval;
    v_count int;
begin
    execute format(
        'select count(*) from %I.%I where user_id = $1 and created_at > now() - $2',
        TG_TABLE_SCHEMA, TG_TABLE_NAME
    )
    into v_count
    using new.user_id, v_window;

    if v_count >= v_max_count then
        raise exception 'Rate limit terlampaui: maksimum % aksi per % pada %', v_max_count, v_window, TG_TABLE_NAME
            using errcode = 'P0001';
    end if;

    return new;
end;
$$ language plpgsql security definer;

-- Post: maks 5 post baru per menit per user.
drop trigger if exists enforce_post_rate_limit on public.posts;
create trigger enforce_post_rate_limit
before insert on public.posts
for each row execute function public.enforce_rate_limit(5, '1 minute');

-- Komentar: maks 10 komentar baru per menit per user.
drop trigger if exists enforce_comment_rate_limit on public.comments;
create trigger enforce_comment_rate_limit
before insert on public.comments
for each row execute function public.enforce_rate_limit(10, '1 minute');

-- Like: maks 60 like baru per menit per user (lebih longgar krn wajar dipicu scroll-scroll cepat).
drop trigger if exists enforce_like_rate_limit on public.likes;
create trigger enforce_like_rate_limit
before insert on public.likes
for each row execute function public.enforce_rate_limit(60, '1 minute');

-- SINKRONISASI: Salin data pengguna yang sudah terlanjur mendaftar tetapi belum memiliki profil
insert into public.profiles (id, username, email, display_name, avatar_color, bio, is_private)
select 
    id,
    coalesce(raw_user_meta_data->>'username', split_part(email, '@', 1)),
    email,
    coalesce(raw_user_meta_data->>'display_name', split_part(email, '@', 1)),
    '#FF5722',
    'Welcome to my profile!',
    false
from auth.users
on conflict (id) do nothing;


-- KEBIJAKAN KEAMANAN ROW LEVEL SECURITY (RLS)

-- Aktifkan keamanan RLS di semua tabel
alter table public.profiles enable row level security;
alter table public.posts enable row level security;
alter table public.stories enable row level security;
alter table public.story_views enable row level security;
alter table public.comments enable row level security;
alter table public.comment_likes enable row level security;
alter table public.likes enable row level security;
alter table public.follows enable row level security;
alter table public.conversations enable row level security;
alter table public.messages enable row level security;
alter table public.notifications enable row level security;
alter table public.device_tokens enable row level security;
alter table public.link_previews enable row level security;
alter table public.app_versions enable row level security;

-- PROFIL: Semua orang bisa membaca profil; hanya pemilik yang bisa mengubah profil sendiri
create policy "Allow public profile reading" on public.profiles for select using (true);
create policy "Allow owners to edit profile" on public.profiles for update using (auth.uid() = id);

-- POSTINGAN: Post terlihat sesuai privasi akun (lihat catatan di bawah); hanya pengguna terautentikasi yang bisa membuat; pemilik yang bisa menghapus
create policy "Posts visible respecting private accounts" on public.posts
for select
using (
    -- Pemilik post selalu bisa lihat post-nya sendiri
    auth.uid() = user_id
    -- Atau akun penulis post memang tidak privat
    or exists (
        select 1 from public.profiles p
        where p.id = posts.user_id and coalesce(p.is_private, false) = false
    )
    -- Atau yang melihat sudah mem-follow penulis post
    or exists (
        select 1 from public.follows f
        where f.follower_id = auth.uid() and f.following_id = posts.user_id
    )
);
create policy "Allow authenticated creation of posts" on public.posts for insert with check (auth.role() = 'authenticated');
create policy "Allow owner deletion of posts" on public.posts for delete using (auth.uid() = user_id);

-- CERITA (STORIES): Semua orang bisa membaca cerita; hanya pengguna terautentikasi yang bisa membuat; pemilik yang bisa menghapus
create policy "Allow public reading of stories" on public.stories for select using (true);
create policy "Allow authenticated creation of stories" on public.stories for insert with check (auth.role() = 'authenticated');
create policy "Allow owner deletion of stories" on public.stories for delete using (auth.uid() = user_id);

-- RIWAYAT LIHAT CERITA (STORY VIEWS): Semua orang bisa membaca riwayat lihat cerita; hanya pengguna terautentikasi yang bisa menyisipkan
create policy "Allow public reading of story views" on public.story_views for select using (true);
create policy "Allow authenticated insertion of story views" on public.story_views for insert with check (auth.role() = 'authenticated');

-- KOMENTAR: Komentar ikut aturan privasi post induknya (lihat catatan di bawah); hanya pengguna terautentikasi yang bisa membuat; pemilik yang bisa menghapus
-- Kalau post induknya tidak boleh dilihat (akun privat & bukan follower), komentarnya juga tidak boleh kebaca.
create policy "Comments visible respecting private accounts" on public.comments
for select
using (
    exists (
        select 1 from public.posts po
        where po.id = comments.post_id
        and (
            auth.uid() = po.user_id
            or exists (
                select 1 from public.profiles p
                where p.id = po.user_id and coalesce(p.is_private, false) = false
            )
            or exists (
                select 1 from public.follows f
                where f.follower_id = auth.uid() and f.following_id = po.user_id
            )
        )
    )
);
create policy "Allow authenticated creation of comments" on public.comments for insert with check (auth.role() = 'authenticated');
create policy "Allow owner deletion of comments" on public.comments for delete using (auth.uid() = user_id);

-- LIKE KOMENTAR: Semua orang bisa membaca; hanya pemilik like yang bisa membuat/menghapus like miliknya sendiri
create policy "Allow public reading of comment likes" on public.comment_likes for select using (true);
create policy "Allow authenticated creation of comment likes" on public.comment_likes for insert with check (auth.uid() = user_id);
create policy "Allow owner deletion of comment likes" on public.comment_likes for delete using (auth.uid() = user_id);

-- LIKES: Semua orang bisa membaca likes; hanya pengguna terautentikasi yang bisa membuat; pemilik yang bisa menghapus
create policy "Allow public reading of likes" on public.likes for select using (true);
create policy "Allow authenticated creation of likes" on public.likes for insert with check (auth.role() = 'authenticated');
create policy "Allow owner deletion of likes" on public.likes for delete using (auth.uid() = user_id);

-- MENGIKUTI (FOLLOWS): Daftar followers selalu terbuka; daftar following mengikuti pengaturan privasi hide_following_list; hanya pengguna terautentikasi yang bisa membuat; pemilik yang bisa menghapus
create policy "Follows readable respecting following-list privacy"
on public.follows for select
using (
    -- Selalu boleh lihat following list milik sendiri
    auth.uid() = follower_id
    -- Selalu boleh lihat siapa saja yang mengikuti diri sendiri (followers list
    -- TIDAK termasuk pengaturan privasi ini)
    or auth.uid() = following_id
    -- Atau pemilik akun follower_id memang tidak menyembunyikan following list-nya
    or exists (
        select 1 from public.profiles p
        where p.id = follows.follower_id
        and coalesce(p.hide_following_list, false) = false
    )
);
create policy "Allow authenticated creation of follows" on public.follows for insert with check (auth.role() = 'authenticated');
create policy "Allow owner deletion of follows" on public.follows for delete using (auth.uid() = follower_id);

-- PERCAKAPAN CHAT: Hanya pengguna yang terlibat di dalam chat yang dapat melihat/memodifikasinya
create policy "Allow selective reading of conversations" on public.conversations for select
    using (auth.uid() = user1_id or auth.uid() = user2_id);
create policy "Allow authenticated creation of conversations" on public.conversations for insert
    with check (auth.uid() = user1_id or auth.uid() = user2_id);
create policy "Allow update of conversations" on public.conversations for update
    using (auth.uid() = user1_id or auth.uid() = user2_id);

-- PESAN CHAT: Hanya pengguna yang terlibat dalam percakapan yang dapat mengirim dan melihat pesan
create policy "Allow selective reading of messages" on public.messages for select
    using (
        exists (
            select 1 from public.conversations c 
            where c.id = conversation_id 
            and (auth.uid() = c.user1_id or auth.uid() = c.user2_id)
        )
    );
create policy "Allow sending of messages" on public.messages for insert
    with check (
        auth.uid() = sender_id 
        and exists (
            select 1 from public.conversations c 
            where c.id = conversation_id 
            and (auth.uid() = c.user1_id or auth.uid() = c.user2_id)
        )
    );
create policy "Allow marking messages as read" on public.messages for update
    using (
        exists (
            select 1 from public.conversations c 
            where c.id = conversation_id 
            and (auth.uid() = c.user1_id or auth.uid() = c.user2_id)
        )
    );


-- NOTIFIKASI: Pengguna hanya bisa melihat/menghapus notifikasi mereka sendiri; siapa pun yang terautentikasi bisa menyisipkannya
create policy "Allow selective reading of notifications" on public.notifications for select
    using (auth.uid() = recipient_id);
create policy "Allow insertion of notifications" on public.notifications for insert
    with check (auth.uid() = sender_id);
create policy "Allow deletion of own notifications" on public.notifications for delete
    using (auth.uid() = recipient_id);
-- Dibutuhkan supaya penerima notifikasi bisa menandai notifikasinya sendiri sebagai
-- sudah dibaca (is_read = true), dipakai oleh fitur badge jumlah belum-dibaca.
create policy "Allow marking own notifications as read" on public.notifications for update
    using (auth.uid() = recipient_id)
    with check (auth.uid() = recipient_id);

-- DEVICE_TOKENS: setiap user hanya boleh melihat/mendaftarkan/mengubah/menghapus token miliknya sendiri.
-- Kebijakan UPDATE dibutuhkan karena aplikasi Android mendaftarkan token lewat upsert
-- (insert ... on conflict (fcm_token) do update), bukan insert murni.
create policy "Allow reading own device tokens" on public.device_tokens for select
    using (auth.uid() = user_id);
create policy "Allow registering own device tokens" on public.device_tokens for insert
    with check (auth.uid() = user_id);
create policy "Allow updating own device tokens" on public.device_tokens for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);
create policy "Allow deleting own device tokens" on public.device_tokens for delete
    using (auth.uid() = user_id);

-- LINK_PREVIEWS: boleh dibaca siapa saja (anon & authenticated) supaya card preview link
-- muncul di feed. TIDAK ada policy insert/update/delete untuk anon/authenticated -- hanya
-- Edge Function (service_role) yang boleh menulis, supaya client tidak bisa menyuntik
-- judul/gambar palsu sendiri ke cache ini.
create policy "Anyone can read link previews" on public.link_previews for select
    using (true);

-- APP_VERSIONS: boleh dibaca siapa saja (anon & authenticated) supaya popup cek update
-- jalan sebelum/sesudah login. SENGAJA tidak ada policy insert/update/delete untuk
-- anon/authenticated -- meski grant di bagian 8 di bawah bersifat luas (all tables),
-- RLS di sini yang jadi penjaga sesungguhnya: baris baru HANYA bisa ditambahkan manual
-- lewat Supabase Dashboard/SQL Editor (pakai koneksi yang bypass RLS), bukan dari app.
create policy "Anyone can read app versions" on public.app_versions for select
    using (true);

-- 8. HAK AKSES (GRANTS)
-- Memberikan hak akses tabel ke role API (anon dan authenticated)
grant select on all tables in schema public to anon, authenticated;
grant insert, update, delete on all tables in schema public to authenticated;
-- service_role dipakai Edge Function send-push: baca profiles (nama pengirim) & device_tokens
-- (token FCM tujuan) saat mengirim push, dan menghapus device_tokens yang basi/tidak valid.
grant select on all tables in schema public to service_role;
grant delete on public.device_tokens to service_role;
-- service_role dipakai Edge Function link-preview buat nge-cache hasil fetch metadata OG.
-- SELECT wajib ikut di-grant (bukan cuma insert/update) karena UPSERT butuh SELECT juga
-- buat ngecek row yang sudah ada -- kalau lupa, upsert-nya gagal diam-diam tanpa error jelas.
grant select, insert, update on public.link_previews to service_role;
grant select on public.link_previews to anon, authenticated;
grant usage, select on all sequences in schema public to authenticated, anon;

-- 9. STORAGE: BUCKET FOTO PROFIL (AVATARS)
-- Bucket dibuat PUBLIC supaya foto bisa ditampilkan langsung lewat URL publik
-- tanpa perlu signed URL. Konvensi nama file: "{user_id}/profile.jpg".
insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', true)
on conflict (id) do nothing;

-- Siapa saja boleh MELIHAT (bucket public)
drop policy if exists "Avatar public read" on storage.objects;
create policy "Avatar public read"
on storage.objects for select
using (bucket_id = 'avatars');

-- User yang login HANYA boleh upload/ubah/hapus file miliknya sendiri
drop policy if exists "Users can upload their own avatar" on storage.objects;
create policy "Users can upload their own avatar"
on storage.objects for insert
to authenticated
with check (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "Users can update their own avatar" on storage.objects;
create policy "Users can update their own avatar"
on storage.objects for update
to authenticated
using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "Users can delete their own avatar" on storage.objects;
create policy "Users can delete their own avatar"
on storage.objects for delete
to authenticated
using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
);
```

---

---

## 📡 2. ENDPOINT REST API BACKEND

Klien Retrofit (`SupabaseApiService.kt`) berkomunikasi dengan API Supabase lewat endpoint REST
standar Supabase (GoTrue untuk auth, PostgREST untuk database, Storage untuk file). Daftar di
bawah ini contoh representatif -- semua entitas (posts, stories, comments, comment_likes, likes,
follows, conversations, messages, notifications, device_tokens) punya pola CRUD yang serupa:

- **Otentikasi (GoTrue):**
  - `POST /auth/v1/signup` -- membuat kredensial pengguna baru
  - `POST /auth/v1/token?grant_type=password` -- login, mengembalikan access & refresh token JWT
  - `POST /auth/v1/token?grant_type=refresh_token` -- memperbarui access token yang kedaluwarsa
  - `POST /auth/v1/recover` -- mengirim email lupa password
- **Database (PostgREST):**
  - `GET /rest/v1/posts` -- mengambil daftar postingan terbaru
  - `POST /rest/v1/posts` -- membuat postingan baru (maks. 3000 karakter, ditegakkan lewat `check` constraint)
  - `GET /rest/v1/profiles?username=eq.{username}` -- mengambil profil berdasarkan username
  - `PATCH /rest/v1/profiles?id=eq.{id}` -- memperbarui bio, nama tampilan, `is_private`, `hide_following_list`, dll.
  - `GET /rest/v1/messages?conversation_id=eq.{id}` -- mengambil riwayat pesan sebuah percakapan
  - `POST /rest/v1/messages` -- mengirim pesan chat baru (trigger otomatis membuat baris `conversations` kalau belum ada)
  - `PATCH /rest/v1/conversations?id=eq.{id}` -- menyembunyikan/"menghapus" chat dari daftar DM milik satu pengguna saja, lewat kolom `hidden_for_user1`/`hidden_for_user2`
  - `POST /rest/v1/device_tokens?on_conflict=fcm_token` -- mendaftarkan/memperbarui token FCM device (upsert)
- **Storage:**
  - `POST /storage/v1/object/avatars/{user_id}/profile.jpg` -- mengunggah/mengganti foto profil

---

## 📂 3. STRUKTUR FOLDER PROYEK

OpenText menerapkan arsitektur **Clean Architecture + MVVM** dengan struktur folder berikut (package root: `com.textsocial.app`, lihat `app/src/main/java/com/textsocial/app/`):

```text
com.textsocial.app/
│
├── App.kt                        # Application class
├── MainActivity.kt               # Single-activity host, minta izin notifikasi & handle deep link push
│
├── data/
│   ├── api/                      # SupabaseApiService.kt (endpoint Retrofit), SupabaseClient.kt (OkHttp + Moshi + auto refresh token)
│   ├── local/                    # EncryptedPreferencesManager.kt (penyimpanan token JWT terenkripsi)
│   ├── model/                    # Dtos.kt (object transfer data request & response API)
│   └── repository/               # RepositoryImpls.kt (implementasi Auth, Posts, Stories, DMs, dll.)
│
├── domain/
│   ├── model/                    # Models.kt (entitas User, Post, Story, Comment, Message, dll.)
│   └── repository/               # Interfaces.kt (kontrak repositori)
│
├── presentation/
│   ├── components/               # BottomNavigationBar, ExpandableLinkText, LinkTextComponent,
│   │                              # StoryStyleOptions, UserAvatarComponent, VerifiedBadge
│   ├── navigation/                # Routes.kt, AppNavGraph.kt (NavHost Jetpack Compose)
│   ├── screens/                  # SplashScreen, LoginScreen, RegisterScreen, MainScreen, HomeScreen,
│   │                              # CreatePostScreen, PostDetailScreen, StoryScreen, CreateStoryScreen,
│   │                              # ProfileScreen, EditProfileScreen, FollowListScreen, DMListScreen,
│   │                              # DMChatScreen, NotificationScreen, SearchScreen, SettingsScreen
│   └── viewmodel/                # ViewModels.kt (semua ViewModel screen di atas)
│
├── push/
│   └── FcmService.kt             # Menerima push FCM & meneruskannya ke NotificationHelper
│
├── ui/theme/                     # Color.kt, Theme.kt, Type.kt (tema Material 3, light/dark)
│
├── util/                         # LocaleManager, ThemeManager (SharedPreferences: bahasa & tema),
│                                  # NotificationHelper, PushNotificationManager, TimeUtils, RateLimiter
│
└── di/
    └── ServiceLocator.kt         # Container Dependency Injection (DI) manual/tanpa Hilt
```

---

## 🛡️ 4. FITUR KEAMANAN

1. **Row Level Security (RLS):** semua tabel Supabase, policy per-operasi (select/insert/update/delete), termasuk visibilitas yang menghormati akun privat (`is_private`) & privasi following (`hide_following_list`) — lihat bagian 1.
2. **Rate limiting anti-spam (DB-level):** trigger `enforce_rate_limit` (generik, dipakai di `posts`/`comments`/`likes` lewat argumen trigger) menolak insert kalau user melebihi batas — 5 post/menit, 10 komentar/menit, 60 like/menit. Ini pertahanan sesungguhnya, berlaku di jalur insert apa pun. `RateLimiter.kt` di sisi Android cuma cooldown UX (feedback instan sebelum request dikirim), bukan pengganti trigger ini.
3. **Proteksi badge verifikasi:** trigger `prevent_self_verification` mencegah user mengubah `is_verified` miliknya sendiri lewat API biasa — kolom ini cuma bisa diubah manual lewat SQL Editor/Table Editor.
4. **Transport security:** `usesCleartextTraffic="false"` mewajibkan HTTPS di semua request.
5. **Encrypted storage:** token JWT disimpan via `EncryptedSharedPreferences` (AES-256) lewat `EncryptedPreferencesManager`, fallback ke `SharedPreferences` biasa kalau enkripsi gagal diinisialisasi.
6. **Storage access control:** bucket `avatars` public-read, tapi upload/update/delete dibatasi lewat policy Storage supaya user cuma bisa ubah foto miliknya sendiri (folder `{user_id}/`).

---

## 🔔 5. PUSH NOTIFICATION (FCM)

Notifikasi sistem (like, komentar, follow, DM) muncul walau aplikasi tertutup total, lewat **Firebase Cloud Messaging**.

**Sisi Android (sudah otomatis jalan):** `push/FcmService.kt` (terima & tampilkan push lewat `NotificationHelper`), `PushNotificationManager` (daftar/hapus FCM token ke tabel `device_tokens`, dipanggil setelah login/register/logout), `MainActivity` (minta izin notifikasi + deep link saat notifikasi di-tap).

**Sisi Supabase (perlu disiapkan manual):**
- Tabel `device_tokens` — sudah ada di skrip bagian 1.
- Service account Firebase: Firebase Console → Project Settings → Service Accounts → Generate new private key (JANGAN masuk repo, beda dari `google-services.json`).
- Deploy Edge Function `supabase/functions/send-push/index.ts`:
  ```bash
  supabase link --project-ref <project-id>
  supabase secrets set FIREBASE_SERVICE_ACCOUNT_JSON="$(cat path/ke/service-account.json)"
  supabase functions deploy send-push --no-verify-jwt
  ```
  `--no-verify-jwt` karena yang memanggil adalah Database Webhook internal, bukan client dengan JWT user.
- Database Webhook (Dashboard → Integrations → Database Webhooks), 3 buah, semuanya arahkan ke Edge Function `send-push` (schema `public`, bukan `realtime`):

  | Name | Table | Events |
      |---|---|---|
  | `notify_on_notification` | `notifications` | Insert |
  | `notify_on_message` | `messages` | Insert |
  | `notify_on_app_version` | `app_versions` | Insert |

**Kontrak payload push** (field `data` FCM, dibaca `NotificationHelper` + `MainActivity` untuk deep link):

| Key | Isi |
|---|---|
| `type` | `like` \| `comment` \| `follow` \| `mention` \| `dm` \| `app_update` |
| `title` / `body` | Teks notifikasi |
| `post_id` / `comment_id` | ID terkait (kosong kalau tidak relevan) |
| `sender_id` / `sender_username` | Pemicu notifikasi / pengirim DM |
| `download_url` | Hanya untuk `type=app_update` — link APK, dibuka browser saat notifikasi di-tap |

**Debugging cepat:** cek tab **Invocations** (bukan Logs) di Edge Function dulu — kosong berarti webhook belum aktif/salah tabel; status 500 biasanya `permission denied` karena `service_role` butuh `grant` eksplisit ke tabel terkait (sudah termasuk di skrip bagian 1); response 200 dengan `"no device tokens"` berarti user itu belum register token (logout-login ulang).

---

## 🔄 7. CEK UPDATE APLIKASI (IN-APP UPDATE POPUP)

Karena app didistribusikan lewat sideload (bukan Play Store), tidak ada auto-update diam-diam —
sebagai gantinya, app cek tabel `app_versions` tiap kali dibuka dan menampilkan dialog kalau ada
versi lebih baru. User tetap yang menyelesaikan instalasi APK secara manual lewat browser.

**Alur:** app dibuka → `AppUpdateViewModel` panggil `GET rest/v1/app_versions?platform=eq.android&order=version_code.desc&limit=1`
→ dibandingkan dengan `BuildConfig.VERSION_CODE` → kalau lebih baru, tampilkan `UpdateDialog` di atas
layar Home/Login (dipasang di level `AppNavGraph`, bukan di satu screen tertentu) → tombol "Update"
membuka `download_url` lewat browser (`Intent.ACTION_VIEW`), tombol "Nanti" menyimpan version_code
yang di-dismiss ke `EncryptedPreferencesManager` supaya tidak nongol lagi untuk versi yang sama.

Kalau `min_supported_version_code` diisi dan lebih besar dari versi app saat ini, dialog jadi **wajib**
(tidak ada tombol "Nanti", tidak bisa ditutup dengan tap di luar dialog).

**Cara merilis versi baru:**
1. Upload APK ke hosting pilihan kamu (GitHub Releases, Google Drive, MediaFire, dll — bebas, yang
   penting linknya bisa dibuka langsung lewat browser).
2. Insert 1 baris baru ke `app_versions` lewat Supabase Dashboard/SQL Editor, contoh:
   ```sql
   insert into public.app_versions (platform, version_code, version_name, release_notes, download_url)
   values ('android', 2, '1.1.0', 'Perbaikan bug & fitur baru', 'https://contoh.com/link-apk-kamu');
   ```
3. Insert ini otomatis memicu webhook `notify_on_app_version` → semua device yang punya token
   terdaftar di `device_tokens` menerima push notification broadcast soal update baru (lihat bagian 5).

**Sisi Android:** `AppUpdateRepository`/`AppUpdateRepositoryImpl` (bandingkan versi & simpan status
dismiss), `AppUpdateViewModel` (dipanggil dari `AppNavGraph`), `UpdateDialog.kt` (composable dialog-nya).

---

## 🔗 6. LINK PREVIEW (OG PREVIEW) SAAT POSTING LINK

Saat user mengetik link, app menampilkan card judul/deskripsi/gambar dari meta tag Open Graph tujuan (mirip preview link WhatsApp).

**Alur:** user ketik link → debounce ~600ms → `SupabaseApiService.fetchLinkPreview` panggil Edge Function `link-preview` → Edge Function fetch HTML server-side, parse OG tags, cache ke tabel `link_previews` → card muncul di form Buat Post (real-time) dan di feed (dari cache). Gambar preview tidak disimpan ke Storage — Android load `image_url` langsung dari situs asal pakai Coil.

**Sisi Supabase (perlu disiapkan manual):**
- Tabel `link_previews` — sudah ada di skrip bagian 1 (termasuk grant `select` eksplisit ke `service_role`, wajib karena UPSERT butuh SELECT juga untuk cek row yang sudah ada).
- Deploy Edge Function `supabase/functions/link-preview/index.ts` **dengan** Verify JWT aktif (beda dari `send-push`) — yang memanggilnya client dengan JWT user asli, bukan webhook internal:
  ```bash
  supabase functions deploy link-preview
  ```

**Keamanan di Edge Function:** cache-first (URL yang sudah di-fetch dalam 3 hari tidak di-fetch ulang), guard SSRF dasar (menolak `localhost`/IP private/metadata endpoint cloud, termasuk DNS rebinding), timeout 6 detik + baca maks ~300KB pertama, dan cache cuma ditulis oleh `service_role` (client cuma bisa baca).

**Debugging cepat:** cek tab Invocations Edge Function; 401 berarti JWT verify gagal (pastikan tidak pakai `--no-verify-jwt`); `fetch_failed: true` berarti situs tujuan memang tidak punya meta OG; card muncul di form tapi hilang di feed biasanya karena `service_role` belum punya `grant select` di `link_previews`.