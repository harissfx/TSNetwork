# OpenText: Aplikasi Media Sosial Khusus Teks Sumber Terbuka (Open-Source)

Platform media sosial berbasis teks yang ringan, aman, dan mengutamakan privasi yang dibangun untuk sistem operasi Android. OpenText menghindari beban basis data multimedia (gambar/video) dengan memprioritaskan kecepatan, kemudahan penggunaan, dan isolasi status lokal.

---

## 🛠️ TEKNOLOGI YANG DIGUNAKAN (TECH STACK)

- **Frontend:** Android Studio dengan Jetpack Compose (Kotlin), Material 3, Navigation Compose, DataStore, dan Retrofit.
- **Backend:** Supabase PostgreSQL Database, Supabase Auth (berbasis JWT), dan Supabase Realtime untuk pertukaran pesan instan (DM).
- **Keamanan & Pencegahan Spam:** Upstash Redis rate-limiting (maksimal 10 postingan/jam per pengguna), puzzle hCaptcha saat registrasi, dan klien OkHttp dengan penegakan HTTPS.

---

## 💾 1. SKEMA DATABASE & KEBIJAKAN SUPABASE (SQL LENGKAP)

> [!WARNING]
> **Kalau proyek Supabase kamu SUDAH PERNAH menjalankan skrip lengkap ini sebelumnya, JANGAN jalankan ulang seluruh skrip di bawah** — bagian atasnya berisi `drop table ... cascade` yang akan menghapus semua data yang sudah ada.
> Kalau kamu cuma perlu menyusulkan update-update terbaru (badge notifikasi, kustomisasi story, badge verifikasi, upload foto profil, privasi following, akun privat beneran berfungsi) ke proyek yang sudah jalan, jalankan file `migration_consolidated.sql` saja — isinya aman (tidak menghapus data) dan bisa dijalankan berkali-kali.
> Untuk proyek yang **baru dibuat dari nol**, semua fitur di atas sudah otomatis termasuk di dalam skrip lengkap di bawah, jadi tidak perlu langkah tambahan apa pun.

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


-- INDEKS OPTIMALISASI KINERJA (PERFORMANCE INDEXES)
create index idx_posts_created_at on public.posts(created_at desc);
create index idx_messages_conversation_id on public.messages(conversation_id);
create index idx_stories_expires_at on public.stories(expires_at);
create index idx_likes_post_id on public.likes(post_id);
create index idx_comments_post_id on public.comments(post_id);
create index idx_comment_likes_comment_id on public.comment_likes(comment_id);
create index idx_notifications_recipient_id on public.notifications(recipient_id);


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

-- 3. Memperbarui Kolom updated_at pada Percakapan secara otomatis saat pesan baru dikirim
create or replace function public.handle_message_inserted()
returns trigger as $$
begin
    update public.conversations
    set updated_at = timezone('utc'::text, now())
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

-- 8. HAK AKSES (GRANTS)
-- Memberikan hak akses tabel ke role API (anon dan authenticated)
grant select on all tables in schema public to anon, authenticated;
grant insert, update, delete on all tables in schema public to authenticated;
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

Klien Retrofit Android berkomunikasi dengan API Supabase menggunakan endpoint REST standar berikut:

- **Endpoint Otentikasi (Auth):**
  - `POST /auth/v1/signup` (Membuat kredensial pengguna baru di layanan Supabase Auth GoTrue)
  - `POST /auth/v1/token?grant_type=password` (Melakukan login dan mengembalikan token JWT)
- **Endpoint Database (CRUD Postgrest):**
  - `GET /rest/v1/posts` (Mengambil daftar postingan terbaru)
  - `POST /rest/v1/posts` (Membuat postingan baru hingga maksimal 3000 karakter)
  - `GET /rest/v1/profiles?id=eq.{id}` (Mengambil rincian profil pengguna)
  - `PATCH /rest/v1/profiles?id=eq.{id}` (Memperbarui biografi dan nama tampilan pengguna)
  - `GET /rest/v1/messages` (Mengambil riwayat percakapan direct message)
  - `POST /rest/v1/messages` (Mengirim gelembung pesan chat baru)

---

## 📂 3. STRUKTUR FOLDER PROYEK

OpenText menerapkan arsitektur **Clean Architecture + MVVM** dengan struktur folder berikut:

```text
com.example/
│
├── data/
│   ├── api/                      # SupabaseApiService.kt, SupabaseClient.kt
│   ├── local/                    # EncryptedPreferencesManager.kt (penyimpanan JWT terenkripsi)
│   ├── model/                    # Dtos.kt (Object transfer data request & response API)
│   └── repository/               # RepositoryImpls.kt (Implementasi Auth, Posts, Stories, DMs)
│
├── domain/
│   ├── model/                    # Models.kt (Entitas User, Post, Story, Comment, Message)
│   └── repository/               # Interfaces.kt (Interface deklarasi kontrak repositori)
│
├── presentation/
│   ├── components/               # LinkTextComponent.kt, UserAvatarComponent.kt
│   ├── navigation/               # Routes.kt, AppNavGraph.kt (Sistem NavHost Jetpack)
│   └── screens/                  # SplashScreen, LoginScreen, RegisterScreen, HomeScreen, CreatePostScreen,
│                                 # PostDetailScreen, StoryScreen, CreateStoryScreen, ProfileScreen,
│                                 # EditProfileScreen, DMListScreen, DMChatScreen, NotificationScreen,
│                                 # SearchScreen, SettingsScreen
│
└── di/
    └── ServiceLocator.kt         # Container Dependency Injection (DI) otomatis
```

---

## 🛡️ 4. FITUR KEAMANAN & ANTI-SPAM

1. **Upstash Redis Rate-Limiting:** Mencegah spam dengan batasan maksimal 10 postingan per jam per pengguna, serta pendaftaran akun dibatasi maksimal 5 kali per IP per hari.
2. **Interactive Captcha:** Pengguna wajib menyelesaikan puzzle captcha buah-buahan saat pendaftaran untuk memblokir registrasi bot.
3. **Transport Security:** Klien OkHttp menonaktifkan lalu lintas teks biasa (`usesCleartextTraffic=false`) untuk mewajibkan enkripsi HTTPS di semua jaringan.
4. **Encrypted Storage:** Token akses JWT disimpan secara lokal menggunakan `EncryptedSharedPreferences` bawaan Android untuk mencegah kebocoran kredensial.