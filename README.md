# OpenText: Aplikasi Media Sosial Khusus Teks

Platform media sosial berbasis teks yang ringan, aman, dan mengutamakan privasi untuk Android. Menghindari beban multimedia dengan memprioritaskan kecepatan dan kemudahan penggunaan.

---

## 🛠️ TEKNOLOGI

- **Frontend:** Android Studio, Jetpack Compose (Kotlin), Material 3, Navigation Compose, DataStore, Retrofit
- **Backend:** Supabase PostgreSQL, Supabase Auth (JWT), Supabase Realtime (DM)
- **Keamanan:** Upstash Redis rate-limiting, hCaptcha, EncryptedSharedPreferences, HTTPS-only

---

## 💾 SKEMA DATABASE SUPABASE

> [!WARNING]
> Skrip ini menghapus semua data yang sudah ada. Hanya jalankan untuk proyek baru.

**Cara menjalankan:** Supabase Dashboard → SQL Editor → New Query → Paste kode → Run

```sql
create extension if not exists "uuid-ossp";

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

create table public.posts (
    id uuid default uuid_generate_v4() primary key,
    user_id uuid references public.profiles(id) on delete cascade not null,
    content text not null check (char_length(content) <= 3000),
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    likes_count integer default 0 not null,
    comments_count integer default 0 not null
);

create table public.stories (
    id uuid default uuid_generate_v4() primary key,
    user_id uuid references public.profiles(id) on delete cascade not null,
    content text not null check (char_length(content) <= 280),
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    expires_at timestamp with time zone not null,
    background_color text not null default '#000000',
    text_color text not null default '#FFFFFF',
    font_family text not null default 'default'
);

create table public.story_views (
    id uuid default uuid_generate_v4() primary key,
    story_id uuid references public.stories(id) on delete cascade not null,
    viewer_username text not null,
    viewed_at timestamp with time zone default timezone('utc'::text, now()) not null,
    unique(story_id, viewer_username)
);

create table public.comments (
    id uuid default uuid_generate_v4() primary key,
    post_id uuid references public.posts(id) on delete cascade not null,
    user_id uuid references public.profiles(id) on delete cascade not null,
    parent_id uuid references public.comments(id) on delete cascade,
    content text not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

create table public.comment_likes (
    id uuid default uuid_generate_v4() primary key,
    comment_id uuid references public.comments(id) on delete cascade not null,
    user_id uuid references public.profiles(id) on delete cascade not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    unique(comment_id, user_id)
);

create table public.likes (
    id uuid default uuid_generate_v4() primary key,
    post_id uuid references public.posts(id) on delete cascade not null,
    user_id uuid references public.profiles(id) on delete cascade not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    unique(post_id, user_id)
);

create table public.follows (
    id uuid default uuid_generate_v4() primary key,
    follower_id uuid references public.profiles(id) on delete cascade not null,
    following_id uuid references public.profiles(id) on delete cascade not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    unique(follower_id, following_id)
);

create table public.conversations (
    id text primary key,
    user1_id uuid references public.profiles(id) on delete cascade not null,
    user2_id uuid references public.profiles(id) on delete cascade not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    updated_at timestamp with time zone default timezone('utc'::text, now()) not null,
    unique(user1_id, user2_id)
);

create table public.messages (
    id uuid default uuid_generate_v4() primary key,
    conversation_id text references public.conversations(id) on delete cascade not null,
    sender_id uuid references public.profiles(id) on delete cascade not null,
    content text not null,
    is_read boolean default false not null,
    is_deleted boolean default false not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

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

create table public.device_tokens (
    id uuid default uuid_generate_v4() primary key,
    user_id uuid references public.profiles(id) on delete cascade not null,
    fcm_token text not null unique,
    platform text not null default 'android',
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    updated_at timestamp with time zone default timezone('utc'::text, now()) not null
);

create index idx_posts_created_at on public.posts(created_at desc);
create index idx_messages_conversation_id on public.messages(conversation_id);
create index idx_stories_expires_at on public.stories(expires_at);
create index idx_likes_post_id on public.likes(post_id);
create index idx_comments_post_id on public.comments(post_id);
create index idx_comment_likes_comment_id on public.comment_likes(comment_id);
create index idx_notifications_recipient_id on public.notifications(recipient_id);
create index idx_device_tokens_user_id on public.device_tokens(user_id);

create or replace function public.handle_like_change()
returns trigger as $$
begin
    if (TG_OP = 'INSERT') then
        update public.posts set likes_count = likes_count + 1 where id = new.post_id;
    elsif (TG_OP = 'DELETE') then
        update public.posts set likes_count = greatest(0, likes_count - 1) where id = old.post_id;
    end if;
    return null;
end;
$$ language plpgsql security definer;

create trigger on_like_change after insert or delete on public.likes
for each row execute function public.handle_like_change();

create or replace function public.handle_comment_change()
returns trigger as $$
begin
    if (TG_OP = 'INSERT') then
        update public.posts set comments_count = comments_count + 1 where id = new.post_id;
    elsif (TG_OP = 'DELETE') then
        update public.posts set comments_count = greatest(0, comments_count - 1) where id = old.post_id;
    end if;
    return null;
end;
$$ language plpgsql security definer;

create trigger on_comment_change after insert or delete on public.comments
for each row execute function public.handle_comment_change();

create or replace function public.handle_message_inserted()
returns trigger as $$
begin
    update public.conversations set updated_at = timezone('utc'::text, now()) where id = new.conversation_id;
    return new;
end;
$$ language plpgsql security definer;

create trigger on_message_inserted after insert on public.messages
for each row execute function public.handle_message_inserted();

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
    ) on conflict (id) do nothing;
    return new;
end;
$$ language plpgsql security definer;

create trigger on_auth_user_created after insert on auth.users
for each row execute function public.handle_new_user();

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

create trigger on_new_like_notify after insert on public.likes
for each row execute function public.handle_new_like_notification();

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

create trigger on_new_comment_notify after insert on public.comments
for each row execute function public.handle_new_comment_notification();

create or replace function public.handle_new_follow_notification()
returns trigger as $$
begin
    insert into public.notifications (recipient_id, sender_id, type)
    values (new.following_id, new.follower_id, 'follow');
    return new;
end;
$$ language plpgsql security definer;

create trigger on_new_follow_notify after insert on public.follows
for each row execute function public.handle_new_follow_notification();

create or replace function public.handle_message_before_insert()
returns trigger as $$
declare
    uid1 uuid;
    uid2 uuid;
begin
    uid1 := split_part(new.conversation_id, '_', 1)::uuid;
    uid2 := split_part(new.conversation_id, '_', 2)::uuid;
    insert into public.conversations (id, user1_id, user2_id)
    values (new.conversation_id, uid1, uid2) on conflict (id) do nothing;
    return new;
end;
$$ language plpgsql security definer;

create trigger on_message_before_insert before insert on public.messages
for each row execute function public.handle_message_before_insert();

create or replace function public.handle_device_token_updated()
returns trigger as $$
begin
    new.updated_at = timezone('utc'::text, now());
    return new;
end;
$$ language plpgsql security definer;

create trigger on_device_token_updated before update on public.device_tokens
for each row execute function public.handle_device_token_updated();

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
create trigger enforce_verified_badge before update on public.profiles
for each row execute function public.prevent_self_verification();

insert into public.profiles (id, username, email, display_name, avatar_color, bio, is_private)
select 
    id,
    coalesce(raw_user_meta_data->>'username', split_part(email, '@', 1)),
    email,
    coalesce(raw_user_meta_data->>'display_name', split_part(email, '@', 1)),
    '#FF5722',
    'Welcome to my profile!',
    false
from auth.users on conflict (id) do nothing;

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

create policy "Allow public profile reading" on public.profiles for select using (true);
create policy "Allow owners to edit profile" on public.profiles for update using (auth.uid() = id);

create policy "Posts visible respecting private accounts" on public.posts for select
using (
    auth.uid() = user_id
    or exists (select 1 from public.profiles p where p.id = posts.user_id and coalesce(p.is_private, false) = false)
    or exists (select 1 from public.follows f where f.follower_id = auth.uid() and f.following_id = posts.user_id)
);
create policy "Allow authenticated creation of posts" on public.posts for insert with check (auth.role() = 'authenticated');
create policy "Allow owner deletion of posts" on public.posts for delete using (auth.uid() = user_id);

create policy "Allow public reading of stories" on public.stories for select using (true);
create policy "Allow authenticated creation of stories" on public.stories for insert with check (auth.role() = 'authenticated');
create policy "Allow owner deletion of stories" on public.stories for delete using (auth.uid() = user_id);

create policy "Allow public reading of story views" on public.story_views for select using (true);
create policy "Allow authenticated insertion of story views" on public.story_views for insert with check (auth.role() = 'authenticated');

create policy "Comments visible respecting private accounts" on public.comments for select
using (
    exists (
        select 1 from public.posts po where po.id = comments.post_id
        and (
            auth.uid() = po.user_id
            or exists (select 1 from public.profiles p where p.id = po.user_id and coalesce(p.is_private, false) = false)
            or exists (select 1 from public.follows f where f.follower_id = auth.uid() and f.following_id = po.user_id)
        )
    )
);
create policy "Allow authenticated creation of comments" on public.comments for insert with check (auth.role() = 'authenticated');
create policy "Allow owner deletion of comments" on public.comments for delete using (auth.uid() = user_id);

create policy "Allow public reading of comment likes" on public.comment_likes for select using (true);
create policy "Allow authenticated creation of comment likes" on public.comment_likes for insert with check (auth.uid() = user_id);
create policy "Allow owner deletion of comment likes" on public.comment_likes for delete using (auth.uid() = user_id);

create policy "Allow public reading of likes" on public.likes for select using (true);
create policy "Allow authenticated creation of likes" on public.likes for insert with check (auth.role() = 'authenticated');
create policy "Allow owner deletion of likes" on public.likes for delete using (auth.uid() = user_id);

create policy "Follows readable respecting following-list privacy" on public.follows for select
using (
    auth.uid() = follower_id
    or auth.uid() = following_id
    or exists (select 1 from public.profiles p where p.id = follows.follower_id and coalesce(p.hide_following_list, false) = false)
);
create policy "Allow authenticated creation of follows" on public.follows for insert with check (auth.role() = 'authenticated');
create policy "Allow owner deletion of follows" on public.follows for delete using (auth.uid() = follower_id);

create policy "Allow selective reading of conversations" on public.conversations for select
    using (auth.uid() = user1_id or auth.uid() = user2_id);
create policy "Allow authenticated creation of conversations" on public.conversations for insert
    with check (auth.uid() = user1_id or auth.uid() = user2_id);
create policy "Allow update of conversations" on public.conversations for update
    using (auth.uid() = user1_id or auth.uid() = user2_id);

create policy "Allow selective reading of messages" on public.messages for select
    using (exists (select 1 from public.conversations c where c.id = conversation_id and (auth.uid() = c.user1_id or auth.uid() = c.user2_id)));
create policy "Allow sending of messages" on public.messages for insert
    with check (auth.uid() = sender_id and exists (select 1 from public.conversations c where c.id = conversation_id and (auth.uid() = c.user1_id or auth.uid() = c.user2_id)));
create policy "Allow marking messages as read" on public.messages for update
    using (exists (select 1 from public.conversations c where c.id = conversation_id and (auth.uid() = c.user1_id or auth.uid() = c.user2_id)));

create policy "Allow selective reading of notifications" on public.notifications for select
    using (auth.uid() = recipient_id);
create policy "Allow insertion of notifications" on public.notifications for insert
    with check (auth.uid() = sender_id);
create policy "Allow deletion of own notifications" on public.notifications for delete
    using (auth.uid() = recipient_id);
create policy "Allow marking own notifications as read" on public.notifications for update
    using (auth.uid() = recipient_id) with check (auth.uid() = recipient_id);

create policy "Allow reading own device tokens" on public.device_tokens for select
    using (auth.uid() = user_id);
create policy "Allow registering own device tokens" on public.device_tokens for insert
    with check (auth.uid() = user_id);
create policy "Allow updating own device tokens" on public.device_tokens for update
    using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "Allow deleting own device tokens" on public.device_tokens for delete
    using (auth.uid() = user_id);

grant select on all tables in schema public to anon, authenticated;
grant insert, update, delete on all tables in schema public to authenticated;
grant select, insert, update, delete on public.device_tokens to service_role;
grant usage, select on all sequences in schema public to authenticated, anon;

insert into storage.buckets (id, name, public) values ('avatars', 'avatars', true) on conflict (id) do nothing;

drop policy if exists "Avatar public read" on storage.objects;
create policy "Avatar public read" on storage.objects for select using (bucket_id = 'avatars');

drop policy if exists "Users can upload their own avatar" on storage.objects;
create policy "Users can upload their own avatar" on storage.objects for insert to authenticated
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "Users can update their own avatar" on storage.objects;
create policy "Users can update their own avatar" on storage.objects for update to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "Users can delete their own avatar" on storage.objects;
create policy "Users can delete their own avatar" on storage.objects for delete to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
```

---

## 📡 ENDPOINT REST API

| Endpoint | Method | Keterangan |
|----------|--------|------------|
| `/auth/v1/signup` | POST | Registrasi pengguna baru |
| `/auth/v1/token?grant_type=password` | POST | Login |
| `/rest/v1/posts` | GET/POST | Ambil/buat postingan |
| `/rest/v1/profiles?id=eq.{id}` | GET/PATCH | Baca/edit profil |
| `/rest/v1/messages` | GET/POST | Riwayat/kirim pesan DM |

---

## 📂 STRUKTUR FOLDER

```text
com.example/
├── data/
│   ├── api/              # SupabaseApiService, SupabaseClient
│   ├── local/            # EncryptedPreferencesManager
│   ├── model/            # Dtos.kt
│   └── repository/       # RepositoryImpls.kt
├── domain/
│   ├── model/            # Models.kt
│   └── repository/       # Interfaces.kt
├── presentation/
│   ├── components/       # LinkTextComponent, UserAvatarComponent
│   ├── navigation/       # Routes, AppNavGraph
│   └── screens/          # Semua layar aplikasi
└── di/
    └── ServiceLocator.kt
```

---

## 🛡️ KEAMANAN

- **Rate-limiting:** Maks 10 post/jam, 5 registrasi/IP/hari (Upstash Redis)
- **Captcha:** Puzzle buah-buahan saat registrasi (hCaptcha)
- **HTTPS-only:** OkHttp `usesCleartextTraffic=false`
- **Encrypted storage:** JWT disimpan di `EncryptedSharedPreferences`

---

## 🔔 PUSH NOTIFICATION (FCM)

### Sudah otomatis (Android client)
- `google-services.json` terpasang
- `FcmService` menerima dan menampilkan push
- `PushNotificationManager` daftarkan/hapus token FCM ke `device_tokens`
- `MainActivity` minta izin notifikasi & handle deep link

### Harus disiapkan (Supabase backend)

**1. Service account Firebase**
- Firebase Console → ⚙️ Project Settings → Service Accounts → Generate new private key

**2. Deploy Edge Function**
```bash
supabase login
supabase link --project-ref <project-id>
supabase secrets set FIREBASE_SERVICE_ACCOUNT_JSON="$(cat path/ke/service-account.json)"
supabase functions deploy send-push --no-verify-jwt
```

**3. Buat Database Webhook**

Dashboard → Integrations → Database Webhooks → Create new hook:

| Name | Table | Events | Type | URL |
|------|-------|--------|------|-----|
| `notify_on_notification` | `notifications` | Insert | Edge Function | `send-push` |
| `notify_on_message` | `messages` | Insert | Edge Function | `send-push` |

> Pastikan tabel yang dipilih dari schema `public`, bukan `realtime`.

### Payload push

| Key | Isi |
|-----|-----|
| `type` | `like` \| `comment` \| `follow` \| `mention` \| `dm` |
| `title` / `body` | Teks notifikasi |
| `post_id` | ID postingan (kosong jika tidak relevan) |
| `comment_id` | ID komentar (kosong jika tidak relevan) |
| `sender_id` | ID pengguna pemicu |
| `sender_username` | Username pengirim |

### Uji coba

```sql
-- Notifikasi ke diri sendiri
insert into notifications (recipient_id, sender_id, type)
values ('<user_id>', '<user_id>', 'follow');

-- DM ke diri sendiri
insert into messages (conversation_id, sender_id, content)
values ('<user_id>_<user_id>', '<user_id>', 'Tes pesan');
```

### Debugging

1. **Tidak ada entry di tab Invocations** → Webhook salah setting atau tabel salah schema
2. **Error `permission denied for table device_tokens`** → Jalankan:
   ```sql
   grant select, insert, update, delete on public.device_tokens to service_role;
   ```
3. **Response `no device tokens for recipient`** → Logout-login ulang di HP
4. **Push OK tapi tidak muncul** → Cek izin notifikasi & battery optimization