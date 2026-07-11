# OpenText Showcase

Landing page & dokumentasi untuk **OpenText** — aplikasi media sosial Android berbasis teks, ringan, dan mengutamakan privasi. Dibangun dengan Vite + React + TypeScript + Tailwind CSS, di-deploy sebagai situs statis lewat GitHub Pages dari folder `docs/`.

Repo aplikasi Android-nya: https://github.com/harissfx/OpenText

---

## Jalankan secara lokal

Project ini ada di dalam folder `web/` pada repo utama `OpenText` (sejajar dengan `app/`).

**Prasyarat:** Node.js

1. Masuk ke folder ini:
   ```bash
   cd web
   ```
2. Install dependency:
   ```bash
   npm install
   ```
3. Jalankan dev server:
   ```bash
   npm run dev
   ```

## Build & deploy ke GitHub Pages

1. Build project. Karena `outDir` di `vite.config.ts` sudah diatur ke `../docs`, hasil build otomatis masuk ke `OpenText/docs/` (root repo, sejajar `app/` dan `web/`), bukan `web/dist/`:
   ```bash
   npm run build
   ```
2. Commit & push folder `docs/` yang baru ke branch `main` (folder `web/` juga ikut di-commit sebagai source-nya).
3. Di GitHub: **Settings → Pages → Build and deployment → Source: "Deploy from a branch"** → pilih branch `main`, folder `/docs` → Save.
4. Situs akan tersedia di `https://harissfx.github.io/OpenText/` (butuh beberapa menit setelah pertama kali diaktifkan).

## Tombol Download APK & Rilis Otomatis

Tombol download mengarah ke:
```
https://github.com/harissfx/OpenText/releases/latest/download/opentext.apk
```
Link ini otomatis narik APK dari GitHub Release terbaru — pastikan nama file APK yang di-upload ke tiap Release **selalu `opentext.apk`** (nama sama persis tiap rilis) supaya link ini terus valid tanpa perlu diubah manual.

Bagian "rilis terbaru" di halaman (release notes, tanggal, versi) diambil otomatis lewat GitHub REST API publik (`/repos/harissfx/OpenText/releases/latest`) saat halaman dimuat — tidak perlu server/backend tambahan.

## Struktur

```
├── src/
│   ├── App.tsx                    # Layout utama landing page + docs
│   ├── data.ts                    # Konten fitur, value proposition, dokumentasi teknis
│   ├── components/
│   │   ├── ReleaseSection.tsx     # Fetch & tampilkan rilis terbaru dari GitHub API
│   │   ├── PhoneMockup.tsx        # Mockup UI aplikasi Android
│   │   └── MarkdownRenderer.tsx   # Render release notes (markdown) dari GitHub
│   └── types.ts
├── vite.config.ts                 # base path & outDir diatur untuk GitHub Pages
└── docs/                          # Hasil build (di-generate, jangan diedit manual)
```
