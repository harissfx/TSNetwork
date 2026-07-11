import React, { useState } from "react";
import { motion } from "motion/react";
import { 
  Zap, 
  Shield, 
  Code, 
  Heart, 
  FileText, 
  Clock, 
  MessageSquare, 
  Users, 
  Send, 
  Bell, 
  Link as LinkIcon, 
  Languages, 
  Sparkles, 
  Download, 
  Github, 
  ExternalLink, 
  BookOpen, 
  Menu, 
  X, 
  ArrowRight, 
  ChevronRight, 
  Copy, 
  Check, 
  Terminal, 
  Smartphone,
  Info
} from "lucide-react";

import { VALUE_PROPOSITIONS, APP_FEATURES, DOC_SECTIONS } from "./data";
import { PhoneMockup } from "./components/PhoneMockup";
import { ReleaseSection } from "./components/ReleaseSection";

export default function App() {
  // Navigation & View Mode state
  const [viewMode, setViewMode] = useState<"landing" | "docs">("landing");
  const [activeDocSection, setActiveDocSection] = useState("arsitektur");
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  
  // Repo configurations
  const gitUsername = "harissfx";
  const gitRepo = "OpenText";

  // Copy code utility state
  const [copiedSection, setCopiedSection] = useState<string | null>(null);

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedSection(id);
    setTimeout(() => {
      setCopiedSection(null);
    }, 2000);
  };

  const handleNavClick = (sectionId: string) => {
    setViewMode("landing");
    setMobileMenuOpen(false);
    setTimeout(() => {
      const element = document.getElementById(sectionId);
      if (element) {
        element.scrollIntoView({ behavior: "smooth" });
      }
    }, 100);
  };

  const handleOpenDocs = (sectionId: string) => {
    setViewMode("docs");
    setActiveDocSection(sectionId);
    setMobileMenuOpen(false);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 font-sans selection:bg-brand selection:text-white">
      
      {/* GLOBAL HEADER & NAVIGATION */}
      <nav className="sticky top-0 z-50 bg-zinc-950/80 backdrop-blur-md border-b border-zinc-900/80">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            
            {/* Logo area */}
            <div 
              onClick={() => { setViewMode("landing"); window.scrollTo({ top: 0, behavior: "smooth" }); }}
              className="flex items-center gap-2.5 cursor-pointer group"
              id="nav-logo"
            >
              <div className="w-8 h-8 rounded-lg bg-brand flex items-center justify-center text-white font-black text-sm tracking-tighter shadow-[0_0_12px_rgba(255,87,34,0.35)] group-hover:scale-105 transition-transform">
                OT
              </div>
              <div className="flex flex-col">
                <span className="text-white font-extrabold text-base tracking-tight leading-none">OpenText</span>
                <span className="text-[10px] text-zinc-500 font-mono tracking-wider">Android App</span>
              </div>
            </div>

            {/* Desktop Navigation Links */}
            <div className="hidden md:flex items-center gap-6">
              {viewMode === "landing" ? (
                <>
                  <button onClick={() => handleNavClick("kenapa")} className="text-sm text-zinc-400 hover:text-white font-medium transition-colors cursor-pointer">Tentang</button>
                  <button onClick={() => handleNavClick("fitur")} className="text-sm text-zinc-400 hover:text-white font-medium transition-colors cursor-pointer">Fitur</button>
                  <button onClick={() => handleNavClick("install")} className="text-sm text-zinc-400 hover:text-white font-medium transition-colors cursor-pointer">Cara Install</button>
                  <button onClick={() => handleNavClick("rilis")} className="text-sm text-zinc-400 hover:text-white font-medium transition-colors cursor-pointer">Rilis</button>
                </>
              ) : (
                <button onClick={() => setViewMode("landing")} className="text-sm text-zinc-400 hover:text-white font-medium transition-colors cursor-pointer flex items-center gap-1">
                  ← Kembali ke Landing Page
                </button>
              )}
              
              <div className="h-4 w-[1px] bg-zinc-800" />
              
              <button
                onClick={() => setViewMode(viewMode === "landing" ? "docs" : "landing")}
                className={`text-xs font-bold px-4 py-2 rounded-lg transition-all flex items-center gap-1.5 cursor-pointer border ${
                  viewMode === "docs" 
                    ? "bg-brand border-brand text-white shadow-[0_0_15px_rgba(255,87,34,0.25)]" 
                    : "bg-zinc-900 border-zinc-800 hover:border-zinc-700 text-zinc-300"
                }`}
              >
                <BookOpen className="w-3.5 h-3.5" />
                {viewMode === "docs" ? "Buka Landing Page" : "Dokumentasi Teknik"}
              </button>

              <a
                href={`https://github.com/${gitUsername}/${gitRepo}`}
                target="_blank"
                rel="noopener noreferrer"
                className="p-2 text-zinc-400 hover:text-white transition-colors cursor-pointer"
                title="Kode Sumber di GitHub"
              >
                <Github className="w-5 h-5" />
              </a>
            </div>

            {/* Mobile menu button */}
            <div className="flex md:hidden items-center gap-3">
              <button
                onClick={() => setViewMode(viewMode === "landing" ? "docs" : "landing")}
                className="text-[11px] font-bold px-2.5 py-1.5 rounded-lg bg-zinc-900 border border-zinc-800 text-zinc-300 flex items-center gap-1"
              >
                <BookOpen className="w-3 h-3" />
                {viewMode === "docs" ? "Landing" : "Docs"}
              </button>
              
              <button
                onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                className="p-2 rounded-lg bg-zinc-900 text-zinc-400 hover:text-white hover:bg-zinc-800 transition-colors cursor-pointer"
                id="mobile-menu-btn"
              >
                {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
              </button>
            </div>

          </div>
        </div>

        {/* Mobile Navigation Panel */}
        {mobileMenuOpen && (
          <div className="md:hidden bg-zinc-950 border-b border-zinc-900 px-4 pt-2 pb-4 space-y-2">
            {viewMode === "landing" ? (
              <>
                <button onClick={() => handleNavClick("kenapa")} className="block w-full text-left px-3 py-2 rounded-md text-sm font-medium text-zinc-400 hover:text-white hover:bg-zinc-900">Tentang</button>
                <button onClick={() => handleNavClick("fitur")} className="block w-full text-left px-3 py-2 rounded-md text-sm font-medium text-zinc-400 hover:text-white hover:bg-zinc-900">Fitur</button>
                <button onClick={() => handleNavClick("install")} className="block w-full text-left px-3 py-2 rounded-md text-sm font-medium text-zinc-400 hover:text-white hover:bg-zinc-900">Cara Install</button>
                <button onClick={() => handleNavClick("rilis")} className="block w-full text-left px-3 py-2 rounded-md text-sm font-medium text-zinc-400 hover:text-white hover:bg-zinc-900">Rilis</button>
              </>
            ) : (
              <button onClick={() => { setViewMode("landing"); setMobileMenuOpen(false); }} className="block w-full text-left px-3 py-2 rounded-md text-sm font-medium text-zinc-400 hover:text-white hover:bg-zinc-900">
                ← Kembali ke Landing Page
              </button>
            )}
            
            <div className="border-t border-zinc-900 pt-2" />
            
            <button
              onClick={() => { setViewMode(viewMode === "landing" ? "docs" : "landing"); setMobileMenuOpen(false); }}
              className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-md text-sm font-bold bg-brand text-white shadow"
            >
              <BookOpen className="w-4 h-4" />
              {viewMode === "docs" ? "Tampilkan Landing Page" : "Dokumentasi Teknik"}
            </button>

            <a
              href={`https://github.com/${gitUsername}/${gitRepo}`}
              target="_blank"
              rel="noopener noreferrer"
              className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-md text-sm font-bold bg-zinc-900 text-zinc-300 border border-zinc-800"
            >
              <Github className="w-4 h-4" />
              Jelajahi Repo GitHub
            </a>
          </div>
        )}
      </nav>

      {/* VIEW: LANDING PAGE */}
      {viewMode === "landing" && (
        <div className="space-y-24 pb-20">
          
          {/* SECTION 1: HERO */}
          <header className="relative pt-12 md:pt-20 lg:pt-28 overflow-hidden">
            {/* Background absolute graphic decorations */}
            <div className="absolute top-1/4 left-1/4 w-[500px] h-[500px] bg-brand/5 rounded-full blur-3xl -translate-x-1/2 -translate-y-1/2 pointer-events-none" />
            <div className="absolute top-1/3 right-1/4 w-[350px] h-[350px] bg-[#E64A19]/5 rounded-full blur-3xl translate-x-1/2 pointer-events-none" />

            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center relative z-10 space-y-8">
              
              {/* Badge */}
              <div className="inline-flex items-center gap-2 px-3  py-1.5 rounded-full bg-zinc-900 border border-zinc-800 text-xs text-zinc-400 select-none mx-auto shadow-inner">
                <span className="flex h-2 w-2 rounded-full bg-brand animate-pulse" />
                <span>Android App • Sideload APK</span>
              </div>

              {/* Title & Tagline */}
              <div className="space-y-4 max-w-3xl mx-auto">
                <h1 className="text-4xl sm:text-5xl md:text-6xl font-black tracking-tight text-white leading-none">
                  Sosial Media Bebas Bloat. <br />
                  <span className="text-brand">Hanya Teks.</span> Murni Pikiran.
                </h1>
                <p className="text-base sm:text-lg md:text-xl text-zinc-400 font-medium leading-relaxed max-w-2xl mx-auto">
                  OpenText adalah platform media sosial Android mandiri yang ringan, aman, dan memprioritaskan privasi Anda. Tanpa gambar yang membuang kuota, tanpa pelacak iklan.
                </p>
              </div>

              {/* CTA Buttons */}
              <div className="flex flex-col sm:flex-row items-center justify-center gap-3 max-w-md mx-auto pt-2">
                <a
                  href={`https://github.com/${gitUsername}/${gitRepo}/releases/latest/download/opentext.apk`}
                  className="w-full sm:w-auto flex items-center justify-center gap-2.5 px-6 py-3.5 rounded-xl bg-brand hover:bg-brand-hover text-white text-sm font-extrabold shadow-[0_4px_20px_rgba(255,87,34,0.3)] hover:shadow-[0_4px_28px_rgba(255,87,34,0.5)] transition-all cursor-pointer"
                  id="btn-download-hero"
                >
                  <Download className="w-4 h-4" />
                  Unduh APK Langsung
                </a>

                <a
                  href={`https://github.com/${gitUsername}/${gitRepo}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="w-full sm:w-auto flex items-center justify-center gap-2.5 px-6 py-3.5 rounded-xl bg-zinc-900 hover:bg-zinc-800 text-zinc-200 text-sm font-bold border border-zinc-800 hover:border-zinc-700 transition-all cursor-pointer"
                  id="btn-github-hero"
                >
                  <Github className="w-4 h-4" />
                  Lihat di GitHub
                  <ExternalLink className="w-3.5 h-3.5 text-zinc-500" />
                </a>
              </div>

              {/* Sub-label */}
              <p className="text-xs text-zinc-500 font-mono">
                Didistribusikan gratis • Berukuran ringan (~12MB) • Mendukung Android 8.0+
              </p>

            </div>
          </header>

          {/* SECTION 2: KENAPA OPENTEXT */}
          <section id="kenapa" className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 scroll-mt-20">
            <div className="text-center space-y-3 mb-12">
              <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white">Kenapa Memilih OpenText?</h2>
              <p className="text-zinc-400 text-sm sm:text-base max-w-xl mx-auto">Kami mengembalikan esensi bersosial media: tempat bertukar ide dan pikiran dengan tenang tanpa kebisingan visual.</p>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
              {VALUE_PROPOSITIONS.map((val, idx) => (
                <div key={idx} className="bg-zinc-900/30 border border-zinc-900 hover:border-zinc-800 p-5 rounded-2xl space-y-3 transition-all hover:translate-y-[-2px]">
                  <div className="w-10 h-10 rounded-xl bg-brand/10 border border-brand/20 flex items-center justify-center text-brand">
                    {val.iconName === "Zap" && <Zap className="w-5 h-5" />}
                    {val.iconName === "Shield" && <Shield className="w-5 h-5" />}
                    {val.iconName === "Code" && <Code className="w-5 h-5" />}
                    {val.iconName === "Heart" && <Heart className="w-5 h-5" />}
                  </div>
                  <h3 className="text-white font-bold text-base">{val.title}</h3>
                  <p className="text-zinc-400 text-xs leading-relaxed">{val.description}</p>
                </div>
              ))}
            </div>
          </section>

          {/* SECTION 3: FITUR UTAMA & MOCKUP INTERAKTIF */}
          <section id="fitur" className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 scroll-mt-20 space-y-12">
            <div className="text-center space-y-3">
              <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white">Eksplorasi Fitur Aplikasi</h2>
              <p className="text-zinc-400 text-sm sm:text-base max-w-xl mx-auto">Interaksikan mockup ponsel di bawah untuk melihat bagaimana fitur berjalan secara live di perangkat Android.</p>
            </div>

            {/* Interactive Phone Mockup */}
            <PhoneMockup />

            {/* Feature Bento Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 pt-8">
              {APP_FEATURES.map((feat) => (
                <div key={feat.id} className="bg-zinc-900/25 border border-zinc-900 p-5 rounded-xl flex gap-4 hover:border-zinc-800/80 transition-colors">
                  <div className="w-9 h-9 rounded-lg bg-zinc-900 border border-zinc-800 flex items-center justify-center text-brand shrink-0">
                    {feat.iconName === "FileText" && <FileText className="w-4 h-4" />}
                    {feat.iconName === "Clock" && <Clock className="w-4 h-4" />}
                    {feat.iconName === "MessageSquareCode" && <MessageSquare className="w-4 h-4" />}
                    {feat.iconName === "HeartHandshake" && <Heart className="w-4 h-4" />}
                    {feat.iconName === "Users" && <Users className="w-4 h-4" />}
                    {feat.iconName === "Send" && <Send className="w-4 h-4" />}
                    {feat.iconName === "Bell" && <Bell className="w-4 h-4" />}
                    {feat.iconName === "Link" && <LinkIcon className="w-4 h-4" />}
                    {feat.iconName === "Languages" && <Languages className="w-4 h-4" />}
                    {feat.iconName === "Sparkles" && <Sparkles className="w-4 h-4" />}
                  </div>
                  <div className="space-y-1.5">
                    <h4 className="text-white font-bold text-sm">{feat.title}</h4>
                    <p className="text-zinc-400 text-xs leading-relaxed">{feat.description}</p>
                  </div>
                </div>
              ))}
            </div>
          </section>

          {/* SECTION 4: CARA INSTALL (SIDELOAD TUTORIAL) */}
          <section id="install" className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 scroll-mt-20">
            <div className="bg-zinc-900/20 border border-zinc-900 rounded-3xl p-6 sm:p-10 space-y-8">
              
              <div className="text-center space-y-2">
                <h2 className="text-2xl font-extrabold tracking-tight text-white">Cara Memasang Aplikasi (Sideload)</h2>
                <p className="text-zinc-400 text-xs sm:text-sm max-w-lg mx-auto">
                  Karena OpenText didistribusikan secara mandiri di luar Google Play Store demi menjaga otonomi, berikut langkah mudah memasang APK di ponsel Anda.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
                
                {/* Step 1 */}
                <div className="space-y-2.5 text-center md:text-left">
                  <div className="w-8 h-8 rounded-full bg-brand/10 border border-brand/20 flex items-center justify-center text-brand font-mono font-bold text-sm mx-auto md:mx-0">
                    1
                  </div>
                  <h4 className="text-white font-bold text-xs uppercase tracking-wider">Unduh APK</h4>
                  <p className="text-zinc-400 text-xs leading-relaxed">
                    Klik tombol download di atas untuk menyimpan berkas <code className="px-1 py-0.5 rounded bg-zinc-800 text-zinc-300 font-mono text-[10px]">opentext.apk</code> di folder download Anda.
                  </p>
                </div>

                {/* Step 2 */}
                <div className="space-y-2.5 text-center md:text-left">
                  <div className="w-8 h-8 rounded-full bg-brand/10 border border-brand/20 flex items-center justify-center text-brand font-mono font-bold text-sm mx-auto md:mx-0">
                    2
                  </div>
                  <h4 className="text-white font-bold text-xs uppercase tracking-wider">Buka Berkas</h4>
                  <p className="text-zinc-400 text-xs leading-relaxed">
                    Ketuk berkas APK dari bilah notifikasi setelah selesai terunduh, atau buka menggunakan File Manager internal ponsel.
                  </p>
                </div>

                {/* Step 3 */}
                <div className="space-y-2.5 text-center md:text-left">
                  <div className="w-8 h-8 rounded-full bg-brand/10 border border-brand/20 flex items-center justify-center text-brand font-mono font-bold text-sm mx-auto md:mx-0">
                    3
                  </div>
                  <h4 className="text-white font-bold text-xs uppercase tracking-wider">Izinkan Sideload</h4>
                  <p className="text-zinc-400 text-xs leading-relaxed">
                    Jika muncul peringatan keamanan, klik <b className="text-white">Settings/Pengaturan</b> pada pop-up lalu aktifkan opsi <b className="text-brand">"Izinkan dari sumber tak dikenal"</b>.
                  </p>
                </div>

                {/* Step 4 */}
                <div className="space-y-2.5 text-center md:text-left">
                  <div className="w-8 h-8 rounded-full bg-brand/10 border border-brand/20 flex items-center justify-center text-brand font-mono font-bold text-sm mx-auto md:mx-0">
                    4
                  </div>
                  <h4 className="text-white font-bold text-xs uppercase tracking-wider">Selesai Pasang</h4>
                  <p className="text-zinc-400 text-xs leading-relaxed">
                    Kembali ke layar instalasi dan klik <b className="text-white">Instal</b>. Aplikasi siap dibuka dan langsung terhubung dengan aman.
                  </p>
                </div>

              </div>

              {/* Warning/Safety Notice */}
              <div className="p-4 bg-zinc-950 rounded-2xl border border-zinc-800 text-xs text-zinc-400 leading-relaxed flex items-start gap-3">
                <Info className="w-4 h-4 text-brand mt-0.5 shrink-0" />
                <div className="space-y-1">
                  <p className="text-white font-semibold">Mengapa muncul peringatan "Unknown App" dari Google Play Protect?</p>
                  <p>
                    Android memunculkan peringatan proteksi standar untuk semua aplikasi yang dipasang manual di luar Play Store. Kami menjamin berkas APK OpenText 100% aman karena di-compile dan ditandatangani langsung dari kode sumber publik kami yang transparan di GitHub. Anda dapat memverifikasi kode sumbernya kapan saja.
                  </p>
                </div>
              </div>

            </div>
          </section>

          {/* SECTION 5: LIVE RELEASES */}
          <section id="rilis" className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 scroll-mt-20 space-y-6">
            <div className="text-center space-y-2">
              <h2 className="text-2xl font-extrabold tracking-tight text-white">Log Rilis Terbaru Aplikasi</h2>
              <p className="text-zinc-400 text-xs sm:text-sm max-w-lg mx-auto">
                Terkoneksi langsung dengan GitHub API untuk mengambil versi terkini secara real-time.
              </p>
            </div>
            <ReleaseSection defaultUsername={gitUsername} defaultRepo={gitRepo} />
          </section>

          {/* SECTION 6: TECH STACK BADGES */}
          <section className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 text-center space-y-8">
            <div className="space-y-2">
              <h3 className="text-xl font-bold text-white">Didukung Arsitektur Terbuka & Handal</h3>
              <p className="text-zinc-400 text-xs max-w-md mx-auto">Kami mengedepankan keamanan jangka panjang dan transparansi menyeluruh.</p>
            </div>

            <div className="flex flex-wrap items-center justify-center gap-4">
              <div className="px-4 py-2 rounded-xl bg-zinc-900 border border-zinc-800 text-zinc-300 text-xs font-semibold flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-violet-500" />
                Kotlin SDK
              </div>
              <div className="px-4 py-2 rounded-xl bg-zinc-900 border border-zinc-800 text-zinc-300 text-xs font-semibold flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-teal-400" />
                Jetpack Compose
              </div>
              <div className="px-4 py-2 rounded-xl bg-zinc-900 border border-zinc-800 text-zinc-300 text-xs font-semibold flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500" />
                Supabase Backend
              </div>
              <div className="px-4 py-2 rounded-xl bg-zinc-900 border border-zinc-800 text-zinc-300 text-xs font-semibold flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-amber-500" />
                Firebase Cloud Messaging
              </div>
            </div>

            <div className="pt-2">
              <button
                onClick={() => handleOpenDocs("arsitektur")}
                className="inline-flex items-center gap-1.5 text-xs font-bold text-brand hover:text-brand-light underline cursor-pointer"
              >
                Lihat Spesifikasi & Skema Database Selengkapnya di Dokumentasi Teknik
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </section>

        </div>
      )}

      {/* VIEW: TECHNICAL DOCUMENTATION PANEL */}
      {viewMode === "docs" && (
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
          <div className="flex flex-col lg:flex-row gap-8">
            
            {/* Documentation Sidebar Menu */}
            <aside className="w-full lg:w-64 shrink-0 space-y-6">
              
              <div className="p-4 bg-zinc-900/40 rounded-xl border border-zinc-900/80">
                <div className="text-xs font-bold text-zinc-500 uppercase tracking-wider font-mono mb-3">
                  Navigasi Dokumen
                </div>
                <div className="flex flex-col gap-1">
                  {DOC_SECTIONS.map(doc => (
                    <button
                      key={doc.id}
                      onClick={() => setActiveDocSection(doc.id)}
                      className={`w-full text-left px-3 py-2 rounded-lg text-xs font-medium transition-all flex items-center justify-between cursor-pointer ${
                        activeDocSection === doc.id
                          ? "bg-brand/10 border border-brand/30 text-brand font-semibold"
                          : "text-zinc-400 hover:text-white hover:bg-zinc-900 border border-transparent"
                      }`}
                    >
                      <span>{doc.title.split(". ")[1] || doc.title}</span>
                      <ChevronRight className="w-3 h-3 opacity-60" />
                    </button>
                  ))}
                </div>
              </div>

              {/* Back button */}
              <button
                onClick={() => setViewMode("landing")}
                className="w-full py-2.5 rounded-xl bg-zinc-900 hover:bg-zinc-800 text-zinc-300 text-xs font-bold border border-zinc-800 transition-colors flex items-center justify-center gap-2 cursor-pointer"
              >
                ← Kembali ke Landing Page
              </button>

            </aside>

            {/* Documentation Content Area */}
            <main className="flex-1 bg-zinc-900/15 border border-zinc-900/80 rounded-2xl p-6 sm:p-8 space-y-6 min-w-0">
              
              {/* Find active section data */}
              {DOC_SECTIONS.filter(doc => doc.id === activeDocSection).map(doc => (
                <div key={doc.id} className="space-y-6">
                  
                  {/* Title & Description */}
                  <div className="space-y-3">
                    <h2 className="text-xl sm:text-2xl font-black text-white tracking-tight border-b border-zinc-900 pb-3">
                      {doc.title}
                    </h2>
                    <p className="text-zinc-400 text-sm leading-relaxed whitespace-pre-line">
                      {doc.description}
                    </p>
                  </div>

                  {/* Optional Code Snippet Frame */}
                  {doc.codeSnippet && (
                    <div className="space-y-2">
                      <div className="flex items-center justify-between px-4 py-2 bg-zinc-950 border-t border-x border-zinc-900 rounded-t-xl text-[10px] text-zinc-400 font-mono">
                        <div className="flex items-center gap-1.5 uppercase font-bold text-brand">
                          <Terminal className="w-3.5 h-3.5" />
                          <span>{doc.language} block</span>
                        </div>
                        <button
                          onClick={() => copyToClipboard(doc.codeSnippet || "", doc.id)}
                          className="flex items-center gap-1 hover:text-white transition-colors cursor-pointer"
                        >
                          {copiedSection === doc.id ? (
                            <>
                              <Check className="w-3 h-3 text-emerald-400" />
                              <span className="text-emerald-400 font-bold">Tersalin!</span>
                            </>
                          ) : (
                            <>
                              <Copy className="w-3 h-3" />
                              <span>Salin Kode</span>
                            </>
                          )}
                        </button>
                      </div>

                      <pre className="p-4 bg-zinc-950 border border-zinc-900 rounded-b-xl overflow-x-auto font-mono text-[11px] sm:text-xs text-zinc-300 leading-relaxed no-scrollbar select-all">
                        <code>{doc.codeSnippet}</code>
                      </pre>
                    </div>
                  )}

                  {/* Interactive Quick Help Footer for developers */}
                  <div className="p-4 bg-zinc-900/50 rounded-xl border border-zinc-800 space-y-2">
                    <div className="text-[10px] text-brand uppercase font-mono font-bold tracking-wider">Saran Implementasi Supabase</div>
                    <p className="text-xs text-zinc-400 leading-relaxed">
                      Sifat text-only dari OpenText memangkas latensi pengiriman data hingga ekstrem. Pastikan Anda mengaktifkan **Row Level Security (RLS)** pada tabel <code className="px-1 py-0.5 rounded bg-zinc-950 text-white font-mono">profiles</code> dan <code className="px-1 py-0.5 rounded bg-zinc-950 text-white font-mono">posts</code>, serta menguji kebijakan (policies) otentikasi di dasbor Supabase Anda sebelum memublikasikan APK.
                    </p>
                  </div>

                </div>
              ))}

              {/* Floating footer link to change settings */}
              <div className="border-t border-zinc-900 pt-6 flex flex-col sm:flex-row items-center justify-between gap-4">
                <span className="text-xs text-zinc-500 font-mono">
                  Dokumentasi Teknis Terbuka • Lisensi MIT
                </span>
                <button
                  onClick={() => {
                    setViewMode("landing");
                    setTimeout(() => {
                      const element = document.getElementById("rilis");
                      if (element) element.scrollIntoView({ behavior: "smooth" });
                    }, 100);
                  }}
                  className="text-xs font-extrabold text-brand hover:text-brand-light flex items-center gap-1 cursor-pointer"
                >
                  Periksa Rilis Aplikasi Terbaru
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
              </div>

            </main>

          </div>
        </div>
      )}

      {/* FOOTER */}
      <footer className="bg-zinc-950 border-t border-zinc-900/80 py-12">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8">
          
          <div className="flex flex-col md:flex-row items-center justify-between gap-6 border-b border-zinc-900 pb-8">
            
            {/* Branding */}
            <div className="text-center md:text-left space-y-1.5">
              <div className="flex items-center justify-center md:justify-start gap-2">
                <div className="w-6 h-6 rounded bg-brand flex items-center justify-center text-white font-black text-xs">
                  OT
                </div>
                <span className="text-white font-bold text-sm tracking-tight">OpenText Project</span>
              </div>
              <p className="text-zinc-500 text-xs">Aplikasi Android jejaring sosial berbasis teks murni, open-source dan bebas iklan.</p>
            </div>

            {/* Quick Links */}
            <div className="flex flex-wrap justify-center gap-6 text-xs font-semibold text-zinc-400">
              <button onClick={() => handleNavClick("kenapa")} className="hover:text-white transition-colors cursor-pointer">Tentang</button>
              <button onClick={() => handleNavClick("fitur")} className="hover:text-white transition-colors cursor-pointer">Fitur</button>
              <button onClick={() => handleNavClick("install")} className="hover:text-white transition-colors cursor-pointer">Cara Install</button>
              <button onClick={() => setViewMode("docs")} className="hover:text-white transition-colors cursor-pointer text-brand">Dokumentasi Teknik</button>
              <a href={`https://github.com/${gitUsername}/${gitRepo}`} target="_blank" rel="noopener noreferrer" className="hover:text-white transition-colors cursor-pointer flex items-center gap-1">
                GitHub Repo
                <ExternalLink className="w-3 h-3 text-zinc-600" />
              </a>
            </div>

          </div>

          <div className="flex flex-col sm:flex-row items-center justify-between gap-4 text-xs font-mono text-zinc-600">
            <p>
              © {new Date().getFullYear()} OpenText. Lisensi MIT. Hak cipta dilindungi.
            </p>
            <div className="flex items-center gap-2">
              <span>Dibangun untuk Android Sideloading</span>
              <span>•</span>
              <span className="text-brand">Otonom & Terdesentralisasi</span>
            </div>
          </div>

        </div>
      </footer>

    </div>
  );
}
