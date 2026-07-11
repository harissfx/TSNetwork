import React, { useState, useEffect } from "react";
import { GitHubRelease } from "../types";
import { MarkdownRenderer } from "./MarkdownRenderer";
import { 
  Download, 
  Github, 
  Calendar, 
  Tag, 
  AlertCircle, 
  RefreshCw,
  CheckCircle2,
  Info,
  ExternalLink
} from "lucide-react";

interface ReleaseSectionProps {
  defaultUsername?: string;
  defaultRepo?: string;
}

export function ReleaseSection({ 
  defaultUsername = "harissfx", 
  defaultRepo = "OpenText" 
}: ReleaseSectionProps) {
  
  const username = defaultUsername;
  const repo = defaultRepo;
  
  const [release, setRelease] = useState<GitHubRelease | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isUsingFallback, setIsUsingFallback] = useState(false);

  const fallbackRelease: GitHubRelease = {
    tag_name: "v1.2.0",
    name: "OpenText v1.2.0 - Stabil & Hemat Baterai",
    published_at: new Date().toISOString(),
    html_url: `https://github.com/${username}/${repo}/releases/latest`,
    body: `### Pembaruan Baru di OpenText v1.2.0

Selamat datang di rilis stabil terbaru! Kami fokus meningkatkan kehandalan pesan langsung dan mengoptimalkan penggunaan RAM aplikasi Android.

#### 🚀 Fitur Baru
- **Kustomisasi Cerita Teks**: Ditambahkan 4 warna gradien latar belakang baru dan kustomisasi font (Sans, Mono, Serif) pada menu Stories.
- **Preview Link Lebih Cerdas**: Pemuatan metadata visual yang lebih cepat untuk URL situs eksternal.
- **Penerjemahan Bahasa**: Dukungan penuh bahasa Indonesia, Inggris, dan Spanyol.

#### 🛠️ Perbaikan & Optimasi
- Mengurangi konsumsi memori background service hingga 30%.
- Memperbaiki bug pada status pesan terkirim (double checkmark) di Supabase Realtime.
- Memperbaiki pemotongan karakter saat postingan teks mendekati limit 3000 karakter.

> **Catatan Keamanan**: File APK yang diunduh di bawah ini ditandatangani dengan kunci rilis resmi dan siap dipasang di perangkat Android Anda.`
  };

  const fetchReleaseData = async (targetUser: string, targetRepo: string) => {
    setLoading(true);
    setErrorMsg(null);
    setIsUsingFallback(false);

    try {
      const response = await fetch(`https://api.github.com/repos/${targetUser}/${targetRepo}/releases/latest`);
      
      if (!response.ok) {
        if (response.status === 404) {
          throw new Error("Repositori atau rilis terbaru tidak ditemukan (404).");
        } else if (response.status === 403) {
          throw new Error("Akses dibatasi (403) - Kemungkinan besar limit API GitHub Anda sedang penuh.");
        } else {
          throw new Error(`Gagal memuat rilis (${response.status}).`);
        }
      }

      const data = await response.json();
      setRelease({
        tag_name: data.tag_name,
        name: data.name || data.tag_name,
        published_at: data.published_at,
        body: data.body,
        html_url: data.html_url
      });
    } catch (err: any) {
      console.warn("GitHub Fetch failed, using aesthetic fallback", err);
      setErrorMsg(err.message || "Gagal memuat rilis terbaru.");
      // Soft fallback so the UI never looks broken!
      setRelease(fallbackRelease);
      setIsUsingFallback(true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReleaseData(username, repo);
  }, []);

  const formatDate = (dateStr: string) => {
    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString("id-ID", {
        year: "numeric",
        month: "long",
        day: "numeric"
      });
    } catch {
      return dateStr;
    }
  };

  return (
    <div className="w-full">
      {/* Main Release Log Display */}
      <div className="bg-zinc-950 border border-zinc-800/80 rounded-2xl p-6 sm:p-8 space-y-6 relative overflow-hidden">
        
        {/* Background glow decorator */}
        <div className="absolute -top-24 -right-24 w-48 h-48 bg-brand/10 rounded-full blur-3xl pointer-events-none" />

        {loading ? (
          <div className="py-12 flex flex-col items-center justify-center gap-3">
            <RefreshCw className="w-8 h-8 text-brand animate-spin" />
            <p className="text-zinc-400 text-xs font-mono">Mengontak API GitHub REST...</p>
          </div>
        ) : (
          <>
            {/* Header info */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-zinc-900 pb-5">
              <div className="space-y-1.5">
                <div className="flex items-center gap-2">
                  <span className="flex items-center gap-1 text-xs px-2.5 py-0.5 rounded-full bg-brand/10 border border-brand/20 text-brand font-bold font-mono">
                    <Tag className="w-3 h-3" />
                    {release?.tag_name || "v1.2.0"}
                  </span>
                  {isUsingFallback ? (
                    <span className="text-[10px] px-2 py-0.5 rounded-full bg-zinc-800 text-zinc-400 font-medium flex items-center gap-1">
                      <Info className="w-3 h-3" />
                      Data Simulasi
                    </span>
                  ) : (
                    <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-950/50 border border-emerald-900/50 text-emerald-400 font-semibold flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" />
                      Terverifikasi Live
                    </span>
                  )}
                </div>
                <h3 className="text-white text-lg sm:text-xl font-bold tracking-tight">
                  {release?.name || "OpenText Rilis Perdana"}
                </h3>
              </div>

              <div className="flex items-center gap-2 text-zinc-400 text-xs font-mono">
                <Calendar className="w-4 h-4 text-zinc-500" />
                <span>{release ? formatDate(release.published_at) : "10 Juli 2026"}</span>
              </div>
            </div>

            {/* Release notes wrapper */}
            <div className="bg-zinc-900/20 rounded-xl p-5 border border-zinc-900/60 max-h-[350px] overflow-y-auto no-scrollbar">
              <MarkdownRenderer content={release?.body || ""} />
            </div>

            {/* Error alerts / instructions if in fallback */}
            {isUsingFallback && (
              <div className="p-4 bg-zinc-900/50 border border-zinc-800 rounded-xl flex items-start gap-3">
                <AlertCircle className="w-4 h-4 text-zinc-400 mt-0.5 shrink-0" />
                <div className="text-xs text-zinc-400 leading-relaxed">
                  {errorMsg ? (
                    <p className="mb-1"><span className="text-zinc-300 font-semibold">Gagal live-fetch:</span> {errorMsg}</p>
                  ) : (
                    <p className="mb-1">Halaman menampilkan data demo karena Anda belum mengubah repositori default.</p>
                  )}
                  <p>
                    Saat Anda melakukan <code className="px-1 py-0.5 bg-zinc-950 text-brand rounded text-[10px] font-mono">npm run build</code>, pastikan variabel username dan repository di code-level sudah disesuaikan dengan milik Anda di GitHub agar pemuatan data berjalan live secara otomatis.
                  </p>
                </div>
              </div>
            )}

            {/* Action Download Buttons inside release log */}
            <div className="flex flex-col sm:flex-row gap-3 pt-2">
              <a
                href={`https://github.com/${username}/${repo}/releases/latest/download/opentext.apk`}
                className="flex-1 flex items-center justify-center gap-2.5 px-5 py-3 rounded-xl bg-brand hover:bg-brand-hover text-white text-xs font-extrabold shadow-[0_4px_14px_rgba(76,111,255,0.2)] hover:shadow-[0_4px_20px_rgba(76,111,255,0.35)] transition-all cursor-pointer"
              >
                <Download className="w-4 h-4" />
                Unduh APK Versi Terbaru ({release?.tag_name || "v1.2.0"})
              </a>

              <a
                href={`https://github.com/${username}/${repo}`}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center justify-center gap-2.5 px-5 py-3 rounded-xl bg-zinc-900 hover:bg-zinc-800 text-zinc-300 text-xs font-bold border border-zinc-800 hover:border-zinc-700 transition-all cursor-pointer"
              >
                <Github className="w-4 h-4" />
                Jelajahi Repo GitHub
                <ExternalLink className="w-3.5 h-3.5 text-zinc-500" />
              </a>
            </div>
          </>
        )}
      </div>
    </div>
  );
}