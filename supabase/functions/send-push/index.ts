// supabase/functions/send-push/index.ts
//
// Supabase Edge Function that turns a new row in `notifications` or `messages`
// into an actual push notification via Firebase Cloud Messaging (HTTP v1 API).
//
// Trigger: a Supabase Database Webhook configured on INSERT for both
// `public.notifications` and `public.messages`, pointing at this function's URL.
// Supabase sends the inserted row as `{ type: "INSERT", table, record, ... }`.
//
// Required secrets (set with `supabase secrets set ...`, see README):
//   FIREBASE_SERVICE_ACCOUNT_JSON  - the full service account JSON, as one string
//   SUPABASE_URL                   - auto-provided by Supabase
//   SUPABASE_SERVICE_ROLE_KEY      - auto-provided by Supabase
//
// This function deliberately does NOT use the deprecated FCM "legacy" API
// (server key + `Authorization: key=...`), since Google shut that down. It signs
// its own short-lived OAuth2 access token from the service account (JWT Bearer
// flow) using the Web Crypto API, which is all that's available in Deno's
// Edge Function runtime.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

interface WebhookPayload {
  type: "INSERT";
  table: "notifications" | "messages";
  record: Record<string, unknown>;
}

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const serviceAccountJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON")!;

const supabase = createClient(supabaseUrl, serviceRoleKey);
const serviceAccount: ServiceAccount = JSON.parse(serviceAccountJson);

// ---- OAuth2 access token (JWT Bearer flow), cached in-memory per warm instance ----

let cachedToken: { value: string; expiresAt: number } | null = null;

function base64UrlEncode(bytes: ArrayBuffer | Uint8Array): string {
  const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let str = "";
  for (const b of arr) str += String.fromCharCode(b);
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const pemBody = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const binaryDer = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    binaryDer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
}

async function getAccessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt > now + 30) {
    return cachedToken.value;
  }

  const header = { alg: "RS256", typ: "JWT" };
  const claimSet = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };

  const encodedHeader = base64UrlEncode(new TextEncoder().encode(JSON.stringify(header)));
  const encodedClaimSet = base64UrlEncode(new TextEncoder().encode(JSON.stringify(claimSet)));
  const signingInput = `${encodedHeader}.${encodedClaimSet}`;

  const key = await importPrivateKey(serviceAccount.private_key);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput)
  );
  const jwt = `${signingInput}.${base64UrlEncode(signature)}`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });

  if (!response.ok) {
    throw new Error(`Failed to get access token: ${response.status} ${await response.text()}`);
  }

  const json = await response.json();
  cachedToken = { value: json.access_token, expiresAt: now + json.expires_in };
  return cachedToken.value;
}

// ---- Building the notification content from the inserted row ----

async function buildPushForNotificationRow(record: Record<string, unknown>) {
  const recipientId = record.recipient_id as string;
  const senderId = record.sender_id as string;
  const type = record.type as string; // 'like' | 'comment' | 'follow' | 'mention'

  const { data: sender } = await supabase
    .from("profiles")
    .select("username, display_name")
    .eq("id", senderId)
    .maybeSingle();

  const senderName = sender?.display_name || sender?.username || "Seseorang";

  const titleByType: Record<string, string> = {
    like: "Like baru",
    comment: "Komentar baru",
    follow: "Pengikut baru",
    mention: "Kamu disebut",
  };
  const bodyByType: Record<string, string> = {
    like: `${senderName} menyukai postinganmu`,
    comment: `${senderName} mengomentari postinganmu`,
    follow: `${senderName} mulai mengikutimu`,
    mention: `${senderName} menyebutmu`,
  };

  return {
    recipientId,
    title: titleByType[type] ?? "Aktivitas baru",
    body: bodyByType[type] ?? `${senderName} melakukan sesuatu`,
    data: {
      type,
      post_id: (record.post_id as string) ?? "",
      comment_id: (record.comment_id as string) ?? "",
      sender_id: senderId,
      sender_username: sender?.username ?? "",
    },
  };
}

async function buildPushForMessageRow(record: Record<string, unknown>) {
  const conversationId = record.conversation_id as string;
  const senderId = record.sender_id as string;
  const content = record.content as string;

  // conversation_id is formatted "user1Id_user2Id" (see README) -- the recipient
  // is whichever half of that pair isn't the sender.
  const [uid1, uid2] = conversationId.split("_");
  const recipientId = uid1 === senderId ? uid2 : uid1;

  const { data: sender } = await supabase
    .from("profiles")
    .select("username, display_name")
    .eq("id", senderId)
    .maybeSingle();

  const senderName = sender?.display_name || sender?.username || "Seseorang";

  return {
    recipientId,
    title: senderName,
    body: content.length > 120 ? content.slice(0, 117) + "..." : content,
    data: {
      type: "dm",
      sender_id: senderId,
      sender_username: sender?.username ?? "",
    },
  };
}

// ---- Sending ----

async function sendToToken(accessToken: string, token: string, title: string, body: string, data: Record<string, string>) {
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token,
          // Data-only message: no top-level "notification" block. This makes sure
          // FcmService.onMessageReceived always runs on the client (see NotificationHelper),
          // instead of the OS auto-displaying a notification we don't control/dedupe.
          data: { ...data, title, body },
          android: { priority: data.type === "dm" ? "high" : "normal" },
        },
      }),
    }
  );

  if (!response.ok) {
    const errorBody = await response.text();
    // UNREGISTERED / invalid-argument usually means the token is stale (app uninstalled,
    // token rotated) -- clean it up so we stop wasting sends on it.
    if (response.status === 404 || errorBody.includes("UNREGISTERED")) {
      await supabase.from("device_tokens").delete().eq("fcm_token", token);
    }
    console.error(`FCM send failed for token ${token}: ${response.status} ${errorBody}`);
  }
}

Deno.serve(async (req) => {
  try {
    const payload: WebhookPayload = await req.json();

    if (payload.type !== "INSERT") {
      return new Response("ignored", { status: 200 });
    }

    const push =
      payload.table === "notifications"
        ? await buildPushForNotificationRow(payload.record)
        : payload.table === "messages"
        ? await buildPushForMessageRow(payload.record)
        : null;

    if (!push) return new Response("ignored: unknown table", { status: 200 });

    const { data: tokens } = await supabase
      .from("device_tokens")
      .select("fcm_token")
      .eq("user_id", push.recipientId);

    if (!tokens || tokens.length === 0) {
      return new Response("no device tokens for recipient", { status: 200 });
    }

    const accessToken = await getAccessToken();
    await Promise.all(
      tokens.map((t) => sendToToken(accessToken, t.fcm_token, push.title, push.body, push.data))
    );

    return new Response("ok", { status: 200 });
  } catch (e) {
    console.error(e);
    return new Response(`error: ${e}`, { status: 500 });
  }
});
