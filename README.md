# OpenText: Aplikasi Media Sosial Khusus Teks

Platform media sosial berbasis teks yang ringan, aman, dan mengutamakan privasi yang dibangun untuk sistem operasi Android. OpenText menghindari beban basis data multimedia (gambar/video) dengan memprioritaskan kecepatan, kemudahan penggunaan, dan isolasi status lokal.

---

## 🛠️ TEKNOLOGI YANG DIGUNAKAN (TECH STACK)

- **Frontend:** Android Studio dengan Jetpack Compose (Kotlin), Material 3, Navigation Compose, Retrofit + Moshi (konversi JSON), Coil (loading gambar), dan `SharedPreferences`/`EncryptedSharedPreferences` untuk penyimpanan lokal (tema, bahasa, token JWT).
- **Backend:** Supabase PostgreSQL Database, Supabase Auth (berbasis JWT), Supabase Realtime untuk pertukaran pesan instan (DM), dan Supabase Storage (bucket `avatars` untuk foto profil).
- **Push Notification:** Firebase Cloud Messaging (FCM) + 1 Supabase Edge Function (`send-push`), dipicu lewat Database Webhook (lihat bagian 5).
- **Keamanan:** RLS (Row Level Security) di semua tabel Supabase, trigger yang mencegah pengguna biasa mengubah status verifikasi (`is_verified`) miliknya sendiri, token JWT disimpan via `EncryptedSharedPreferences`, dan klien OkHttp dengan `usesCleartextTraffic="false"` (memaksa semua trafik lewat HTTPS).

---

## ⚙️ 0. KONFIGURASI PROYEK (WAJIB SEBELUM BUILD)

Kredensial Supabase **tidak di-hardcode** di kode Android -- diambil dari `BuildConfig.SUPABASE_URL`
dan `BuildConfig.SUPABASE_ANON_KEY` (lihat `SupabaseClient.kt`), yang digenerate lewat
[Secrets Gradle Plugin](https://github.com/google/secrets-gradle-plugin) dari sebuah file `.env`
di root proyek (setara `app/build.gradle.kts` -> blok `secrets { propertiesFileName = ".env" }`).
File ini **tidak disertakan di repo** (rahasia per-developer), jadi buat sendiri:

1. Buat file `.env` di root proyek (sejajar dengan `settings.gradle.kts`), isinya:
   ```properties
   SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
   SUPABASE_ANON_KEY=isi-anon-public-key-dari-dashboard-supabase
   ```
   (Dashboard Supabase -> **Project Settings -> API** untuk mendapatkan kedua nilai ini.)
2. Sinkronkan proyek dengan Gradle (Android Studio akan otomatis membaca `.env` lewat plugin secrets).
3. Untuk push notification, `app/google-services.json` (kredensial Firebase) sudah termasuk di repo ini, jadi tidak perlu setup ulang kecuali kamu memakai project Firebase sendiri.

> `local.properties` hanya berisi `sdk.dir` (lokasi Android SDK di komputer kamu) -- bukan tempat kredensial Supabase.

---

## 💾 1. SKEMA DATABASE & KEBIJAKAN SUPABASE (SQL LENGKAP)

> [!WARNING]
> **Kalau proyek Supabase kamu SUDAH PERNAH menjalankan skrip lengkap ini sebelumnya, JANGAN jalankan ulang seluruh skrip di bawah** — bagian atasnya berisi `drop table ... cascade` yang akan menghapus semua data yang sudah ada.
> Repo ini **tidak menyertakan file migrasi terpisah** (tidak ada folder `supabase/migrations`) — skrip SQL di bawah adalah satu-satunya sumber skema database, didesain untuk setup dari nol di proyek Supabase yang masih kosong.
> Kalau proyek Supabase kamu sudah jalan dan cuma perlu menyusulkan tabel/kolom yang belum ada (mis. `device_tokens`, `hidden_for_user1`/`hidden_for_user2`, `is_verified`, `hide_following_list`, kolom kustomisasi story), salin manual bagian `create table`/`alter table` yang relevan dari skrip di bawah, satu per satu, alih-alih menjalankan seluruh blok.
> Untuk proyek yang **baru dibuat dari nol**, cukup jalankan seluruh skrip di bawah satu kali — semua fitur (device tokens, kustomisasi story, badge verifikasi, upload foto profil, privasi following, akun privat, hapus chat/pesan) sudah otomatis termasuk.

> [!IMPORTANT]
> **CARA MENJALANKAN KODE DI SUPABASE:**
> 1. Masuk ke dashboard [Supabase](https://supabase.com/).
> 2. Pilih proyek Anda, lalu klik menu **SQL Editor** di panel sebelah kiri.
> 3. Klik tombol **New Query** (atau tanda plus `+`) untuk membuka tab editor baru.
> 4. Salin (copy) **seluruh isi blok kode SQL di bawah ini**, tempelkan (paste) ke editor tersebut.
> 5. Klik tombol **Run** di bagian kanan bawah.

```sql
-- Mengaktifkan ekstensi UUID generator
create extension if not exists "uuid-ossp";

-- Bersihkan tabel lama jika ada (agar skrip ini bisa dijalankan ulang tanpa error)
drop trigger if exists on_auth_user_created on auth.users;
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


-- INDEKS OPTIMALISASI KINERJA (PERFORMANCE INDEXES)
create index idx_posts_created_at on public.posts(created_at desc);
create index idx_messages_conversation_id on public.messages(conversation_id);
create index idx_stories_expires_at on public.stories(expires_at);
create index idx_likes_post_id on public.likes(post_id);
create index idx_comments_post_id on public.comments(post_id);
create index idx_comment_likes_comment_id on public.comment_likes(comment_id);
create index idx_notifications_recipient_id on public.notifications(recipient_id);
create index idx_device_tokens_user_id on public.device_tokens(user_id);


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
│                                  # NotificationHelper, PushNotificationManager, TimeUtils
│
└── di/
    └── ServiceLocator.kt         # Container Dependency Injection (DI) manual/tanpa Hilt
```

---

## 🛡️ 4. FITUR KEAMANAN

1. **Row Level Security (RLS):** Semua tabel Supabase mengaktifkan RLS dengan policy per-operasi (select/insert/update/delete), termasuk aturan visibilitas yang menghormati akun privat (`is_private`) dan privasi daftar following (`hide_following_list`) — lihat bagian 1 di atas.
2. **Proteksi Badge Verifikasi:** Trigger `prevent_self_verification` mencegah pengguna mengubah `is_verified` miliknya sendiri lewat request API biasa; kolom ini hanya bisa diubah manual lewat SQL Editor/Table Editor Supabase.
3. **Transport Security:** Klien OkHttp + `AndroidManifest` menonaktifkan lalu lintas teks biasa (`usesCleartextTraffic="false"`) untuk mewajibkan enkripsi HTTPS di semua request jaringan.
4. **Encrypted Storage:** Token akses JWT & refresh token disimpan secara lokal menggunakan `EncryptedSharedPreferences` (AES-256) lewat `EncryptedPreferencesManager`, dengan fallback otomatis ke `SharedPreferences` biasa kalau enkripsi gagal diinisialisasi di device tertentu.
5. **Storage Access Control:** Bucket `avatars` bersifat public-read, tapi upload/update/delete foto profil dibatasi lewat policy Supabase Storage supaya pengguna hanya bisa mengubah foto miliknya sendiri (folder `{user_id}/`).

---

## 🔔 5. PUSH NOTIFICATION (FCM) -- NOTIFIKASI SAAT APLIKASI TERTUTUP

> ✅ **Status: sudah terpasang & terkonfirmasi jalan** (like/comment/follow/DM memicu
> notifikasi di system tray walau aplikasi tertutup total).

Aplikasi bisa menampilkan notifikasi sistem (like, komentar, follow, DM baru) walaupun
aplikasi sedang tertutup/di-kill, memakai **Firebase Cloud Messaging (FCM)**.

### Yang sudah otomatis jalan di sisi Android (client)
- `app/google-services.json` -- kredensial project Firebase, sudah terpasang.
- `com.textsocial.app.push.FcmService` -- menerima push & menampilkannya lewat `NotificationHelper`.
- `PushNotificationManager` -- mendaftarkan/menghapus FCM token device ke tabel `device_tokens`, dipanggil otomatis setelah login/register berhasil, saat app dibuka dalam keadaan sudah login, dan saat logout.
- `MainActivity` -- minta izin notifikasi (Android 13+) dan membuka layar yang relevan saat notifikasi di-tap (deep link ke post/profil/DM terkait).

### Yang HARUS kamu siapkan sendiri di sisi Supabase (backend)
Bagian client hanya bisa *menerima & menampilkan* push. Yang *mengirim* push (begitu ada
row baru di tabel `notifications`/`messages`) ada di Supabase, lewat sebuah Edge Function.

**1. Pastikan tabel `device_tokens` sudah ada**
Tabel ini sudah termasuk di skrip SQL lengkap pada bagian 1. Kalau proyek Supabase kamu
sudah jalan lebih dulu sebelum fitur push ini ditambahkan, salin manual bagian
`create table public.device_tokens` beserta index, trigger `on_device_token_updated`,
policy RLS, dan `grant`-nya (lihat bagian 1) lalu jalankan sekali di SQL Editor -- tidak
menyentuh tabel lain yang sudah berisi data.

**2. Siapkan service account Firebase**
- Firebase Console -> ⚙️ Project Settings -> **Service Accounts** -> **Generate new private key**
- Ini men-download file JSON rahasia (JANGAN dimasukkan ke repo/APK, ini beda dari `google-services.json`)

**3. Deploy Edge Function `send-push`**
Kodenya ada di `supabase/functions/send-push/index.ts`. Dari terminal, di root proyek:
```bash
supabase login
supabase link --project-ref <project-id-kamu>   # lihat di project_info.project_id pada google-services.json
supabase secrets set FIREBASE_SERVICE_ACCOUNT_JSON="$(cat path/ke/service-account.json)"
supabase functions deploy send-push --no-verify-jwt
```
`--no-verify-jwt` dipakai karena yang memanggil function ini adalah Database Webhook
internal Supabase, bukan client dengan JWT user.

> Kalau deploy lewat **Dashboard -> Edge Functions -> Deploy a new function -> Via Editor**
> (tanpa CLI), Supabase kadang otomatis kasih nama slug sendiri ke function-nya (misal
> `dynamic-handler`) alih-alih `send-push`. Itu tidak masalah -- namanya bebas, yang penting
> pas bikin Database Webhook di langkah 4, Edge Function yang dipilih di dropdown itu yang
> benar (cek dulu di tab **Overview** function-nya buat lihat nama & URL aslinya).

**4. Buat Database Webhook**
Menunya ada di Dashboard Supabase -> **Integrations** -> **Database Webhooks** (bukan di
bawah menu "Database" langsung -- di beberapa versi dashboard menunya disembunyikan di situ).
Bisa juga langsung lewat URL `https://supabase.com/dashboard/project/<project-ref>/integrations/webhooks/webhooks`.
Klik **Create a new hook**, buat 2 buah:
| Name | Table | Events | Type | URL |
|---|---|---|---|---|
| `notify_on_notification` | `notifications` | Insert | Supabase Edge Functions | `send-push` |
| `notify_on_message` | `messages` | Insert | Supabase Edge Functions | `send-push` |

> ⚠️ Pas milih tabel di form pembuatan webhook, pastikan yang dipilih tabel `messages` di
> **schema `public`** (tabel asli aplikasi ini). Supabase juga punya tabel-tabel lain bernama
> mirip di **schema `realtime`** (dipakai fitur Realtime bawaan Supabase, bukan bagian dari
> aplikasi ini) -- kalau salah pilih ke situ, webhook-nya nggak akan pernah kepicu oleh DM
> yang beneran dikirim lewat aplikasi.

Setelah ini aktif, tiap ada like/komentar/follow baru (insert ke `notifications`) atau
pesan DM baru (insert ke `messages`), Supabase otomatis memanggil `send-push`, yang lalu
mengambil `device_tokens` milik penerima dan mengirim push lewat FCM.

### Kontrak payload push (dipakai bareng oleh Edge Function & client Android)
Field `data` yang dikirim ke device lewat FCM, dan dibaca oleh `NotificationHelper` +
`MainActivity` untuk deep link:

| Key | Isi |
|---|---|
| `type` | `like` \| `comment` \| `follow` \| `mention` \| `dm` |
| `title` / `body` | Teks notifikasi |
| `post_id` | ID postingan terkait (kosong kalau tidak relevan) |
| `comment_id` | ID komentar terkait (kosong kalau tidak relevan) |
| `sender_id` | ID pengguna yang memicu notifikasi/pengirim DM |
| `sender_username` | Username pengirim (dipakai buat judul layar DM) |

### Uji coba
Karena butuh 2 pihak (pengirim aktivitas + penerima notifikasi), paling gampang dites
dengan **1 device aja** lewat SQL Editor -- gak perlu 2 HP:

1. Tutup total app di HP (swipe dari recent apps, bukan cuma minimize).
2. Cari `user_id` sendiri: `select id from profiles where username = 'username_kamu';`
3. Jalankan insert manual (boleh pakai `user_id` yang sama buat `recipient_id` dan `sender_id`, notif ke diri sendiri gapapa buat tes):
   ```sql
   insert into notifications (recipient_id, sender_id, type)
   values ('<user_id>', '<user_id>', 'follow');
   ```
4. Notifikasi harus muncul di system tray dalam beberapa detik. Tap notifikasinya harus langsung membuka profil terkait.

Kalau mau tes DM juga:
```sql
insert into messages (conversation_id, sender_id, content)
values ('<user_id>_<user_id>', '<user_id>', 'Tes pesan DM');
```

### Debugging kalau notifikasi tidak muncul
Urutan pengecekan paling efektif, dari yang paling gampang dicek:

1. **Cek tab "Invocations" di Edge Function** (Dashboard -> Edge Functions -> function-nya
   -> tab **Invocations**), BUKAN tab "Logs" -- funsgi ini cuma menulis ke Logs kalau ada
   `console.log`/`console.error`, jadi kalau tidak ada entry di Logs itu belum tentu berarti
   function tidak ke-invoke. Tab Invocations mencatat semua request HTTP yang masuk apa adanya.
    - Tidak ada entry sama sekali di sekitar waktu tes -> Database Webhook belum aktif/salah
      setting. Cek Dashboard -> Integrations -> Database Webhooks -> pastikan tabel dan Edge
      Function yang dipilih sudah benar (dan schema-nya `public`, bukan `realtime`).
    - Ada entry tapi statusnya 500 -> baca detail error di tab **Logs**, biasanya salah satu dari dua ini:
2. **Error: `permission denied for table device_tokens` / mengarah ke `GRANT ... TO service_role`.**
   Ini terjadi karena `service_role` (dipakai Edge Function) butuh GRANT eksplisit ke tabel
   `device_tokens`, terpisah dari RLS policy. Perbaikannya:
   ```sql
   grant select, insert, update, delete on public.device_tokens to service_role;
   ```
   (skrip SQL lengkap di bagian 1 sudah menyertakan baris `grant` ini untuk deploy baru.)
3. **Notifikasi muncul tapi judulnya "Seseorang" / isi pesan generik, bukan nama pengirim asli.**
   Sama akar masalahnya seperti poin 2, tapi di tabel `profiles`: `service_role` gagal baca
   nama pengirim, jadi Edge Function jatuh ke teks fallback. Perbaikannya:
   ```sql
   grant select on all tables in schema public to service_role;
   ```
4. **Response `"no device tokens for recipient"` (status 200, bukan error).**
   Berarti pipeline-nya jalan normal, tapi `device_tokens` kosong untuk user itu. Cek:
   ```sql
   select * from device_tokens where user_id = '<user_id>';
   ```
   Kalau kosong, logout-login ulang di HP (token FCM didaftarkan lewat `PushNotificationManager`
   yang dipanggil setelah login sukses / saat app dibuka dalam keadaan sudah login).
5. **Semua di atas sukses (log sampai `FCM send OK...`) tapi tetap tidak ada notifikasi di HP.**
   Kemungkinan di sisi Android: izin notifikasi belum di-allow (Settings HP -> Apps -> nama
   app -> Notifications), atau battery optimization vendor (Xiaomi/Oppo/Vivo, dll) yang
   agresif membunuh proses background sehingga push FCM tidak sampai ke `FcmService`.
6. **Tadinya sudah jalan, tapi tiba-tiba berhenti total (Invocations kosong lagi) setelah
   menjalankan ulang skrip SQL.** Kalau skrip yang dijalankan mengandung `drop table ...
   cascade` untuk `notifications`/`messages` (skrip SQL lengkap di bagian 1 memang begitu --
   didesain untuk setup dari nol, bukan untuk di-run ulang di project yang sudah jalan),
   Database Webhook yang nempel ke tabel itu **ikut putus** waktu tabelnya di-drop dan
   dibuat ulang, walau di dashboard webhook-nya masih kelihatan "ada". Perbaikannya: hapus
   webhook lama itu di **Integrations -> Database Webhooks**, lalu buat ulang dari awal
   (lihat langkah 4 di atas). Ke depannya, untuk sekadar menambah/mengubah sesuatu di
   project yang sudah jalan, jalankan hanya potongan `create table`/`alter table` yang
   relevan (bukan seluruh skrip di bagian 1), supaya `drop table ... cascade`-nya tidak
   ikut menghapus data dan memutus webhook yang sudah aktif.

## 🔗 6. LINK PREVIEW (OG PREVIEW) SAAT POSTING LINK

Saat user mengetik link di form Buat Post, atau saat feed menampilkan post yang mengandung
link, aplikasi menampilkan card kecil berisi judul/deskripsi/gambar dari halaman tujuan
(mirip preview link di WhatsApp). Data ini diambil dari meta tag Open Graph (`og:title`,
`og:description`, `og:image`) milik situs tujuan.

### Alur singkatnya
1. User mengetik/menempel link di form Buat Post.
2. Setelah user berhenti mengetik ~600ms (debounce), app memanggil Edge Function
   `link-preview` lewat `SupabaseApiService.fetchLinkPreview`.
3. Edge Function fetch HTML situs tujuan (server-side, bukan dari HP user), parse meta tag
   OG-nya, lalu **cache** hasilnya ke tabel `link_previews` (lihat bagian 1) supaya post lain
   yang berisi link sama tidak perlu fetch ulang.
4. Card preview muncul di form Buat Post (real-time) dan di feed Home (dibaca dari cache,
   bukan fetch baru) untuk post yang sudah tersimpan.

Catatan penting soal beban server: yang mahal adalah langkah fetch+parse HTML di langkah 2,
dan itu **hanya terjadi sekali per URL unik** (berkat cache di langkah 3) -- render feed di
langkah 4 cuma query biasa ke tabel Postgres, seringan query lain di app ini. Gambar preview
sendiri **tidak** disimpan ke Supabase Storage; Android cukup me-load `image_url` langsung
dari server situs tujuan pakai Coil (persis seperti me-load avatar dari URL eksternal),
jadi tidak menambah pemakaian bucket Storage sama sekali.

### Yang HARUS kamu siapkan sendiri di sisi Supabase (backend)

**1. Pastikan tabel `link_previews` sudah ada**
Sudah termasuk di skrip SQL lengkap pada bagian 1. Kalau proyek Supabase kamu sudah jalan
lebih dulu (paling umum: nambahin fitur ini ke project yang sudah live), jalankan SQL
mandiri ini sekali di SQL Editor -- sudah termasuk semua grant yang dibutuhkan, jadi tidak
bergantung ke urutan skrip section 1:

```sql
create table public.link_previews (
    url text primary key,
    title text,
    description text,
    image_url text,
    site_name text,
    fetch_failed boolean not null default false,
    fetched_at timestamp with time zone default timezone('utc'::text, now()) not null
);

alter table public.link_previews enable row level security;

create policy "Anyone can read link previews" on public.link_previews for select
    using (true);

grant select, insert, update on public.link_previews to service_role;
grant select on public.link_previews to anon, authenticated;
```

> **Kenapa perlu `grant select` eksplisit, bukan cuma insert/update?** Kalau kamu jalankan
> ini sebagai skrip tambahan setelah setup awal project (bukan dari skrip lengkap section 1
> dari nol), grant lama seperti `grant select on all tables in schema public to ...` **tidak**
> otomatis berlaku ke tabel baru ini -- perintah itu cuma berlaku ke tabel yang sudah ada
> pada saat dijalankan. Tanpa `select` eksplisit di atas, UPSERT dari Edge Function bakal
> gagal **diam-diam tanpa error yang jelas** (karena UPSERT butuh SELECT juga buat ngecek row
> yang sudah ada), dan cache-nya nggak pernah kesimpen walau Edge Function tetap balikin
> respons sukses ke app.

**2. Deploy Edge Function `link-preview`**
Kodenya ada di `supabase/functions/link-preview/index.ts`. Ada 2 cara:

*Cara A -- lewat Dashboard (tanpa terminal):* Dashboard -> **Edge Functions** -> **Deploy a
new function** -> pilih **Via Editor** -> nama function-nya `link-preview` -> hapus isi
default di editor, ganti dengan isi `index.ts` -> pastikan toggle **Verify JWT** aktif -> klik
**Deploy**. Kalau nanti ada update kode, tinggal buka lagi function `link-preview` di
Dashboard, edit isinya, dan deploy ulang -- tidak perlu bikin function baru.

*Cara B -- lewat CLI:*
```bash
supabase login
supabase link --project-ref <project-id-kamu>
supabase functions deploy link-preview
```

Beda dari `send-push`, function ini **JANGAN** dipakai `--no-verify-jwt` -- yang memanggilnya
adalah client Android dengan JWT user asli (lewat header `Authorization` yang sudah otomatis
disisipkan `SupabaseClient.kt`), bukan Database Webhook internal. Dengan verifikasi JWT aktif
(default), hanya pengguna yang sudah login di aplikasi yang bisa memicu fetch, mencegah orang
luar menyalahgunakan Edge Function ini buat fetch URL sembarangan.

### Keamanan yang sudah dijaga di Edge Function ini
- **Cache-first:** URL yang sudah pernah di-fetch (berhasil, dalam 3 hari terakhir) tidak
  di-fetch ulang -- cukup baca dari tabel `link_previews`.
- **Guard SSRF dasar:** menolak fetch ke `localhost`, IP loopback/private
  (`127.x`, `10.x`, `172.16-31.x`, `192.168.x`, termasuk `169.254.169.254` yang sering
  dipakai buat endpoint metadata cloud), dan mencoba resolve DNS hostname-nya juga supaya
  domain yang di-*rebind* ke IP internal tetap ketolak.
- **Batas waktu & ukuran:** fetch dibatasi timeout 6 detik dan hanya membaca ~300KB pertama
  dari response (cukup untuk `<head>`, tidak perlu download seluruh halaman).
- **Cache ditulis oleh `service_role`, bukan client:** client hanya bisa **membaca** tabel
  `link_previews` (lihat policy RLS di bagian 1) -- tidak bisa menyuntik judul/gambar palsu
  langsung ke database.

### Debugging kalau card preview tidak muncul
1. Cek tab **Invocations** di Dashboard -> Edge Functions -> `link-preview` -- ada request
   masuk atau tidak saat kamu mengetik link di form Buat Post.
2. Kalau responsnya 401 -- pastikan function di-deploy **tanpa** `--no-verify-jwt` (lihat
   langkah 2 di atas) dan HP dalam keadaan sudah login.
3. Kalau `fetch_failed: true` di response -- situs tujuannya kemungkinan tidak punya meta tag
   Open Graph sama sekali, memblokir bot/user-agent tidak dikenal, atau responsnya bukan
   `text/html` (mis. langsung file PDF/gambar). Ini bukan bug, memang situs tujuannya tidak
   menyediakan metadata untuk di-preview.
4. Kalau mau paksa refresh cache untuk 1 URL tertentu (misal buat testing), hapus baris-nya
   manual: `delete from link_previews where url = '<url-nya>';`
5. **Kartu preview muncul di form Buat Post tapi TIDAK muncul lagi di feed setelah di-post**
   -- ini bukan gagal fetch (soalnya di form aja udah kebukti berhasil), tapi cache-nya nggak
   pernah kesimpen ke tabel `link_previews`. Cek dengan `select * from link_previews;` -- kalau
   kosong padahal udah sering coba post link, hampir pasti karena `service_role` belum punya
   hak `select` di tabel ini (lihat kotak catatan di langkah 1) -- upsert dari Edge Function
   butuh SELECT juga buat ngecek row yang sudah ada, dan kalau nggak ada, gagalnya diam-diam
   tanpa error yang jelas balik ke app. Jalankan
   `grant select on public.link_previews to service_role, anon, authenticated;` buat
   ngebenerin ini.