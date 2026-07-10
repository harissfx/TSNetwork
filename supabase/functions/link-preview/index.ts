import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const supabase = createClient(supabaseUrl, serviceRoleKey);

const FETCH_TIMEOUT_MS = 6000;
const MAX_BYTES_TO_READ = 300_000;
const CACHE_TTL_MS = 3 * 24 * 60 * 60 * 1000;
const FAILURE_RETRY_MS = 24 * 60 * 60 * 1000;

interface LinkPreviewRow {
  url: string;
  title: string | null;
  description: string | null;
  image_url: string | null;
  site_name: string | null;
  fetch_failed: boolean;
  fetched_at: string;
}

function isPrivateOrLocalHostname(hostname: string): boolean {
  const h = hostname.toLowerCase();
  if (h === "localhost" || h.endsWith(".local") || h.endsWith(".internal")) return true;

  const ipv4 = h.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (ipv4) {
    const [a, b] = [parseInt(ipv4[1]), parseInt(ipv4[2])];
    if (a === 127) return true;
    if (a === 10) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    if (a === 192 && b === 168) return true;
    if (a === 169 && b === 254) return true;
    if (a === 0) return true;
    return false;
  }

  if (h === "::1" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")) return true;

  return false;
}

async function isSafeUrl(url: URL): Promise<boolean> {
  if (url.protocol !== "http:" && url.protocol !== "https:") return false;
  if (isPrivateOrLocalHostname(url.hostname)) return false;
  try {
    const records = await Deno.resolveDns(url.hostname, "A");
    for (const ip of records) {
      if (isPrivateOrLocalHostname(ip)) return false;
    }
  } catch {
  }

  return true;
}

function decodeHtmlEntities(text: string): string {
  return text
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#0?39;/g, "'")
    .replace(/&#x27;/g, "'")
    .trim();
}

function parseMetaTags(html: string): Record<string, string> {
  const result: Record<string, string> = {};
  const metaTagRegex = /<meta\s+[^>]*>/gi;
  const tags = html.match(metaTagRegex) ?? [];

  for (const tag of tags) {
    const propMatch = tag.match(/(?:property|name)\s*=\s*["']([^"']+)["']/i);
    const contentMatch = tag.match(/content\s*=\s*["']([^"']*)["']/i);
    if (propMatch && contentMatch) {
      const key = propMatch[1].toLowerCase();
      if (!(key in result)) result[key] = decodeHtmlEntities(contentMatch[1]);
    }
  }
  return result;
}

function parseTitleTag(html: string): string | null {
  const match = html.match(/<title[^>]*>([^<]*)<\/title>/i);
  return match ? decodeHtmlEntities(match[1]) : null;
}

async function readHtmlCapped(response: Response, maxBytes: number): Promise<string> {
  const reader = response.body?.getReader();
  if (!reader) return await response.text();

  const chunks: Uint8Array[] = [];
  let received = 0;
  while (received < maxBytes) {
    const { done, value } = await reader.read();
    if (done) break;
    if (value) {
      chunks.push(value);
      received += value.length;
    }
  }
  try {
    await reader.cancel();
  } catch {
  }
  const merged = new Uint8Array(received);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.length;
  }
  return new TextDecoder("utf-8", { fatal: false }).decode(merged);
}

async function fetchPreview(targetUrl: URL): Promise<Omit<LinkPreviewRow, "url" | "fetched_at">> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);

  try {
    const response = await fetch(targetUrl.toString(), {
      signal: controller.signal,
      redirect: "follow",
      headers: {
        "User-Agent":
          "Mozilla/5.0 (compatible; TSNetworkLinkPreview/1.0; +https://tsnetwork.app)",
        Accept: "text/html,application/xhtml+xml",
      },
    });

    const contentType = response.headers.get("content-type") ?? "";
    if (!response.ok || !contentType.includes("text/html")) {
      return { title: null, description: null, image_url: null, site_name: null, fetch_failed: true };
    }

    const html = await readHtmlCapped(response, MAX_BYTES_TO_READ);
    const meta = parseMetaTags(html);
    const finalUrl = new URL(response.url || targetUrl.toString());

    const title = meta["og:title"] || parseTitleTag(html) || null;
    const description = meta["og:description"] || meta["description"] || null;
    const rawImage = meta["og:image"] || meta["twitter:image"] || null;
    const siteName = meta["og:site_name"] || finalUrl.hostname;

    let imageUrl: string | null = null;
    if (rawImage) {
      try {
        imageUrl = new URL(rawImage, finalUrl).toString();
      } catch {
        imageUrl = null;
      }
    }

    if (!title && !description && !imageUrl) {
      return { title: null, description: null, image_url: null, site_name: siteName, fetch_failed: true };
    }

    return { title, description, image_url: imageUrl, site_name: siteName, fetch_failed: false };
  } catch {
    return { title: null, description: null, image_url: null, site_name: null, fetch_failed: true };
  } finally {
    clearTimeout(timeout);
  }
}

Deno.serve(async (req) => {
  try {
    if (req.method !== "POST") {
      return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405 });
    }

    const body = await req.json().catch(() => null);
    const rawUrl = body?.url;
    if (typeof rawUrl !== "string" || rawUrl.length === 0 || rawUrl.length > 2000) {
      return new Response(JSON.stringify({ error: "Invalid url" }), { status: 400 });
    }

    let parsedUrl: URL;
    try {
      parsedUrl = new URL(rawUrl);
    } catch {
      return new Response(JSON.stringify({ error: "Invalid url" }), { status: 400 });
    }

    const normalizedUrl = parsedUrl.toString();

    const { data: cached } = await supabase
      .from("link_previews")
      .select("*")
      .eq("url", normalizedUrl)
      .maybeSingle<LinkPreviewRow>();

    if (cached) {
      const age = Date.now() - new Date(cached.fetched_at).getTime();
      const ttl = cached.fetch_failed ? FAILURE_RETRY_MS : CACHE_TTL_MS;
      if (age < ttl) {
        return new Response(JSON.stringify(cached), { status: 200 });
      }
    }

    if (!(await isSafeUrl(parsedUrl))) {
      return new Response(JSON.stringify({ error: "URL not allowed" }), { status: 400 });
    }

    const preview = await fetchPreview(parsedUrl);
    const row: LinkPreviewRow = {
      url: normalizedUrl,
      ...preview,
      fetched_at: new Date().toISOString(),
    };

    await supabase.from("link_previews").upsert(row, { onConflict: "url" });

    return new Response(JSON.stringify(row), { status: 200 });
  } catch (e) {
    console.error(e);
    return new Response(JSON.stringify({ error: `${e}` }), { status: 500 });
  }
});