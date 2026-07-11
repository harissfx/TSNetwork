import React, { useState } from "react";
import { 
  Heart, 
  MessageSquare, 
  Send, 
  User, 
  ShieldCheck, 
  ExternalLink,
  RefreshCw,
  Sparkles,
  ChevronLeft,
  Smile,
  MoreVertical,
  Lock,
  CheckCheck,
  Link,
  Home,
  Search,
  CirclePlus,
  Bell
} from "lucide-react";

export function PhoneMockup() {
  const [activeTab, setActiveTab] = useState<"feed" | "story" | "chat" | "profile">("feed");

  // Chat State
  const [chatMessages, setChatMessages] = useState([
    { id: 1, sender: "other", text: "Halo! Sudah coba OpenText di Android?", time: "10:30" },
    { id: 2, sender: "me", text: "Sudah! Ringan sekali aplikasinya, responsif.", time: "10:32" },
    { id: 3, sender: "other", text: "Iya, hemat kuota karena tidak ada loading gambar & video berat.", time: "10:33" }
  ]);
  const [inputText, setInputText] = useState("");

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim()) return;

    const newMsg = {
      id: Date.now(),
      sender: "me",
      text: inputText,
      time: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    };
    setChatMessages([...chatMessages, newMsg]);
    setInputText("");

    // Auto response after a second to simulate Realtime
    setTimeout(() => {
      setChatMessages(prev => [
        ...prev,
        {
          id: Date.now() + 1,
          sender: "other",
          text: "Keren kan! Didukung Supabase Realtime pula, chat langsung masuk.",
          time: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
        }
      ]);
    }, 1200);
  };

  // Story State
  const storyBackgrounds = [
    { bg: "bg-gradient-to-tr from-[#4C6FFF] to-[#39E8F9]", text: "text-white", font: "font-sans" },
    { bg: "bg-zinc-900 border border-brand/50", text: "text-brand", font: "font-mono" },
    { bg: "bg-emerald-950", text: "text-emerald-300", font: "font-serif" },
    { bg: "bg-violet-950", text: "text-violet-200", font: "font-sans" }
  ];
  const [selectedStoryBg, setSelectedStoryBg] = useState(0);
  const [storyText, setStoryText] = useState("OpenText: Ruang berbagi pikiran murni lewat tulisan. Tanpa clutter media, tanpa algoritma manipulatif! 🚀✨");

  // Post State (Feed)
  const [posts, setPosts] = useState([
    {
      id: 1,
      author: "budi_setiawan",
      avatarColor: "bg-teal-600",
      content: "Baru saja membaca artikel bagus tentang kebangkitan kembali internet berbasis teks. It is clean, low cost, and hyper-focused. Cek link ini: https://github.com/harissfx/OpenText",
      time: "2j lalu",
      likes: 12,
      comments: 3,
      hasLiked: false,
      linkPreview: {
        title: "OpenText Android App Source Code",
        description: "Privacy-first text-only social network built on Kotlin, Jetpack Compose and Supabase.",
        domain: "github.com"
      }
    },
    {
      id: 2,
      author: "anisa_putri",
      avatarColor: "bg-[#EC4899]",
      content: "Mengapa media sosial modern terasa begitu melelahkan? Terlalu banyak video auto-play, gambar komparatif, dan algoritma yang sengaja memancing emosi negatif. Back to basics is the best way.",
      time: "4j lalu",
      likes: 45,
      comments: 11,
      hasLiked: true,
      linkPreview: null
    }
  ]);

  const toggleLike = (id: number) => {
    setPosts(prev => prev.map(post => {
      if (post.id === id) {
        return {
          ...post,
          likes: post.hasLiked ? post.likes - 1 : post.likes + 1,
          hasLiked: !post.hasLiked
        };
      }
      return post;
    }));
  };

  return (
    <div className="flex flex-col lg:flex-row items-center gap-8 xl:gap-12 w-full max-w-5xl mx-auto py-4">
      {/* Phone Screen Left Selector Panels */}
      <div className="flex lg:flex-col flex-wrap justify-center gap-3 w-full lg:w-48 shrink-0">
        <button
          onClick={() => setActiveTab("feed")}
          className={`flex items-center gap-3 px-4 py-3 rounded-xl border text-sm font-medium transition-all cursor-pointer ${
            activeTab === "feed"
              ? "bg-brand/10 border-brand text-brand shadow-[0_0_12px_rgba(76,111,255,0.15)]"
              : "bg-zinc-900 border-zinc-800 text-zinc-400 hover:text-white hover:border-zinc-700"
          }`}
        >
          <span className="flex h-2 w-2 rounded-full bg-brand" />
          Home Feed
        </button>

        <button
          onClick={() => setActiveTab("story")}
          className={`flex items-center gap-3 px-4 py-3 rounded-xl border text-sm font-medium transition-all cursor-pointer ${
            activeTab === "story"
              ? "bg-brand/10 border-brand text-brand shadow-[0_0_12px_rgba(76,111,255,0.15)]"
              : "bg-zinc-900 border-zinc-800 text-zinc-400 hover:text-white hover:border-zinc-700"
          }`}
        >
          <span className="flex h-2 w-2 rounded-full bg-pink-500" />
          24h Stories
        </button>

        <button
          onClick={() => setActiveTab("chat")}
          className={`flex items-center gap-3 px-4 py-3 rounded-xl border text-sm font-medium transition-all cursor-pointer ${
            activeTab === "chat"
              ? "bg-brand/10 border-brand text-brand shadow-[0_0_12px_rgba(76,111,255,0.15)]"
              : "bg-zinc-900 border-zinc-800 text-zinc-400 hover:text-white hover:border-zinc-700"
          }`}
        >
          <span className="flex h-2 w-2 rounded-full bg-emerald-500" />
          Realtime DM
        </button>

        <button
          onClick={() => setActiveTab("profile")}
          className={`flex items-center gap-3 px-4 py-3 rounded-xl border text-sm font-medium transition-all cursor-pointer ${
            activeTab === "profile"
              ? "bg-brand/10 border-brand text-brand shadow-[0_0_12px_rgba(76,111,255,0.15)]"
              : "bg-zinc-900 border-zinc-800 text-zinc-400 hover:text-white hover:border-zinc-700"
          }`}
        >
          <span className="flex h-2 w-2 rounded-full bg-indigo-500" />
          Profile & Updates
        </button>
      </div>

      {/* Main Interactive Phone Display */}
      <div className="relative mx-auto w-full max-w-[310px] h-[610px] bg-black rounded-[42px] border-8 border-zinc-800 shadow-2xl overflow-hidden flex flex-col shrink-0 ring-4 ring-zinc-900">

        {/* Android Notch / Speaker / Camera */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 h-6 w-36 bg-zinc-800 rounded-b-2xl z-50 flex items-center justify-center">
          <div className="w-12 h-1 bg-black rounded-full" />
        </div>

        {/* Simulated Android Status Bar */}
        <div className="h-7 pt-1 px-6 flex justify-between items-center text-[10px] font-mono text-zinc-400 bg-zinc-950 shrink-0 select-none">
          <span>10:45</span>
          <div className="flex items-center gap-1.5">
            <span>Supabase Active</span>
            <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
          </div>
        </div>

        {/* Dynamic Mobile App Header */}
        <div className="h-12 border-b border-zinc-900 bg-zinc-950 px-4 flex items-center justify-between shrink-0">
          <span className="flex items-center gap-1.5">
            <img src={`${import.meta.env.BASE_URL}app-icon.png`} alt="OpenText" className="w-5 h-5 rounded-md" />
            <span className="text-sm font-extrabold text-white tracking-tight">OpenText</span>
          </span>
          <div className="flex gap-2">
            <span className="text-[9px] px-1.5 py-0.5 rounded bg-zinc-900 text-zinc-400 font-mono">
              v1.2.0
            </span>
          </div>
        </div>

        {/* Dynamic Mobile Screen Body */}
        <div className="flex-1 bg-zinc-950 overflow-y-auto no-scrollbar flex flex-col">

          {/* SCREEN: HOME FEED */}
          {activeTab === "feed" && (
            <div className="p-3 space-y-3 flex-1 flex flex-col justify-start">
              {/* Stories horizontal slider thumbnail */}
              <div className="flex gap-2 pb-2 border-b border-zinc-900 overflow-x-auto no-scrollbar">
                <div className="flex flex-col items-center gap-1 shrink-0">
                  <div className="w-9 h-9 rounded-full ring-2 ring-pink-500 p-0.5">
                    <div className="w-full h-full rounded-full bg-zinc-800 flex items-center justify-center text-xs text-white font-bold">
                      Me
                    </div>
                  </div>
                  <span className="text-[8px] text-zinc-500">Cerita</span>
                </div>
                <div className="flex flex-col items-center gap-1 shrink-0">
                  <div className="w-9 h-9 rounded-full ring-2 ring-zinc-800 p-0.5">
                    <div className="w-full h-full rounded-full bg-teal-600 flex items-center justify-center text-xs text-white font-bold">
                      B
                    </div>
                  </div>
                  <span className="text-[8px] text-zinc-500">budi_s</span>
                </div>
                <div className="flex flex-col items-center gap-1 shrink-0">
                  <div className="w-9 h-9 rounded-full ring-2 ring-zinc-800 p-0.5">
                    <div className="w-full h-full rounded-full bg-brand flex items-center justify-center text-xs text-white font-bold">
                      A
                    </div>
                  </div>
                  <span className="text-[8px] text-zinc-500">anisa_p</span>
                </div>
              </div>

              {/* Feed posts list */}
              {posts.map(post => (
                <div key={post.id} className="p-3 bg-zinc-900/50 rounded-xl border border-zinc-900/80 space-y-2">
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-2">
                      <div className={`w-6 h-6 rounded-full ${post.avatarColor} flex items-center justify-center text-[10px] text-white font-black`}>
                        {post.author[0].toUpperCase()}
                      </div>
                      <span className="text-xs font-semibold text-zinc-200">@{post.author}</span>
                    </div>
                    <span className="text-[9px] text-zinc-500 font-mono">{post.time}</span>
                  </div>

                  <p className="text-zinc-300 text-[11px] leading-relaxed break-words whitespace-pre-line">
                    {post.content}
                  </p>

                  {/* Link Preview Mockup */}
                  {post.linkPreview && (
                    <div className="border border-zinc-800 bg-zinc-950/60 rounded-lg p-2 flex flex-col gap-1">
                      <div className="flex items-center gap-1 text-[9px] text-brand font-semibold uppercase tracking-wider font-mono">
                        <Link className="w-2.5 h-2.5" />
                        {post.linkPreview.domain}
                      </div>
                      <div className="text-[10px] font-bold text-white leading-snug">{post.linkPreview.title}</div>
                      <div className="text-[9px] text-zinc-400 line-clamp-2 leading-normal">{post.linkPreview.description}</div>
                    </div>
                  )}

                  <div className="flex gap-4 pt-1 text-[10px] text-zinc-500 font-mono select-none">
                    <button
                      onClick={() => toggleLike(post.id)}
                      className={`flex items-center gap-1.5 hover:text-red-500 transition-colors cursor-pointer ${post.hasLiked ? "text-red-500" : ""}`}
                    >
                      <Heart className="w-3.5 h-3.5" fill={post.hasLiked ? "currentColor" : "none"} />
                      <span>{post.likes}</span>
                    </button>
                    <div className="flex items-center gap-1.5 hover:text-brand transition-colors">
                      <MessageSquare className="w-3.5 h-3.5" />
                      <span>{post.comments}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* SCREEN: STORIES */}
          {activeTab === "story" && (
            <div className="p-3 flex-1 flex flex-col justify-between">
              <div className="text-center text-[10px] text-zinc-500 pb-1.5 font-mono">
                Atur Latar & Teks Cerita Anda
              </div>

              {/* Dynamic Live Story Frame */}
              <div className={`flex-1 rounded-2xl flex flex-col items-center justify-center p-6 text-center shadow-inner transition-all duration-300 ${storyBackgrounds[selectedStoryBg].bg}`}>
                <p className={`text-sm leading-relaxed font-semibold transition-all ${storyBackgrounds[selectedStoryBg].text} ${storyBackgrounds[selectedStoryBg].font}`}>
                  {storyText || "Tuliskan pikiran instan Anda di sini..."}
                </p>
                <div className="absolute bottom-16 text-[8px] text-zinc-400/80 font-mono uppercase tracking-widest mt-4">
                  OpenText Stories
                </div>
              </div>

              {/* Interactive Controls inside app view */}
              <div className="bg-zinc-900/60 p-2.5 rounded-xl border border-zinc-900 mt-2.5 space-y-2">
                <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar">
                  <span className="text-[9px] text-zinc-400 shrink-0 font-mono">Warna:</span>
                  {storyBackgrounds.map((bg, idx) => (
                    <button
                      key={idx}
                      onClick={() => setSelectedStoryBg(idx)}
                      className={`w-4 h-4 rounded-full border shrink-0 transition-transform ${bg.bg} ${
                        selectedStoryBg === idx ? "scale-125 border-white" : "border-transparent"
                      }`}
                    />
                  ))}
                </div>
                <input
                  type="text"
                  value={storyText}
                  onChange={(e) => setStoryText(e.target.value.slice(0, 100))}
                  placeholder="Ketik cerita..."
                  className="w-full text-[10px] px-2 py-1 rounded bg-zinc-950 border border-zinc-800 text-white focus:outline-none focus:border-brand"
                />
              </div>
            </div>
          )}

          {/* SCREEN: CHAT */}
          {activeTab === "chat" && (
            <div className="flex-1 flex flex-col justify-between h-full bg-zinc-950">
              {/* Active User Header */}
              <div className="px-3 py-1.5 bg-zinc-900/60 border-b border-zinc-900 flex items-center justify-between shrink-0">
                <div className="flex items-center gap-2">
                  <div className="w-5 h-5 rounded-full bg-teal-600 flex items-center justify-center text-[9px] text-white font-bold">
                    B
                  </div>
                  <div className="flex flex-col">
                    <span className="text-[10px] font-bold text-zinc-200">budi_setiawan</span>
                    <span className="text-[7px] text-emerald-500 font-mono">Online</span>
                  </div>
                </div>
                <MoreVertical className="w-3.5 h-3.5 text-zinc-500" />
              </div>

              {/* Chats Scroller */}
              <div className="flex-1 p-3 space-y-2.5 overflow-y-auto no-scrollbar flex flex-col">
                {chatMessages.map(msg => (
                  <div
                    key={msg.id}
                    className={`flex flex-col max-w-[85%] ${
                      msg.sender === "me" ? "self-end items-end" : "self-start items-start"
                    }`}
                  >
                    <div
                      className={`px-3 py-1.5 rounded-xl text-[10px] leading-relaxed break-all ${
                        msg.sender === "me"
                          ? "bg-brand text-white rounded-tr-none"
                          : "bg-zinc-900 text-zinc-300 rounded-tl-none"
                      }`}
                    >
                      {msg.text}
                    </div>
                    <div className="flex items-center gap-1 mt-0.5 px-1">
                      <span className="text-[7px] text-zinc-600 font-mono">{msg.time}</span>
                      {msg.sender === "me" && (
                        <CheckCheck className="w-3 h-3 text-brand" />
                      )}
                    </div>
                  </div>
                ))}
              </div>

              {/* Interactive Send Form */}
              <form onSubmit={handleSendMessage} className="p-2 border-t border-zinc-900 bg-zinc-950 shrink-0 flex gap-1.5">
                <input
                  type="text"
                  value={inputText}
                  onChange={(e) => setInputText(e.target.value)}
                  placeholder="Ketik pesan langsung..."
                  className="flex-1 text-[10px] px-2.5 py-1.5 rounded-lg bg-zinc-900 border border-zinc-800 text-white focus:outline-none focus:border-brand placeholder-zinc-500"
                />
                <button
                  type="submit"
                  className="p-1.5 bg-brand hover:bg-brand-hover rounded-lg text-white transition-colors cursor-pointer"
                >
                  <Send className="w-3.5 h-3.5" />
                </button>
              </form>
            </div>
          )}

          {/* SCREEN: PROFILE & UPDATES */}
          {activeTab === "profile" && (
            <div className="p-3 space-y-3 flex-1 flex flex-col justify-start">
              {/* Profile Card */}
              <div className="p-3 bg-zinc-900/40 rounded-xl border border-zinc-900 flex flex-col items-center text-center space-y-1.5">
                <div className="relative">
                  <div className="w-12 h-12 rounded-full bg-brand flex items-center justify-center text-white font-extrabold text-lg shadow-[0_0_12px_rgba(76,111,255,0.3)]">
                    O
                  </div>
                  <div className="absolute -bottom-1 -right-1 bg-zinc-950 p-0.5 rounded-full border border-zinc-800">
                    <Lock className="w-3 h-3 text-brand" />
                  </div>
                </div>
                <div>
                  <div className="text-xs font-bold text-white flex items-center justify-center gap-1">
                    Oka Mahendra
                    <ShieldCheck className="w-3.5 h-3.5 text-brand" />
                  </div>
                  <span className="text-[9px] text-zinc-500 font-mono">@oka_mahendra</span>
                </div>
                <p className="text-[10px] text-zinc-400 max-w-[180px] leading-relaxed">
                  Menyukai minimalisme, kopi hitam, dan open source. 🌿
                </p>
                <div className="flex gap-4 pt-1 border-t border-zinc-800/80 w-full justify-around text-center select-none font-mono text-[9px] text-zinc-400">
                  <div>
                    <div className="font-bold text-white">128</div>
                    <span>Post</span>
                  </div>
                  <div>
                    <div className="font-bold text-white">1.2K</div>
                    <span>Follower</span>
                  </div>
                </div>
              </div>

              {/* Simulated In-App Update Alert (App Updater module demonstration) */}
              <div className="p-3 bg-brand/5 rounded-xl border border-brand/20 space-y-2 relative overflow-hidden">
                <div className="absolute top-1 right-1">
                  <RefreshCw className="w-6 h-6 text-brand/20 animate-spin" style={{ animationDuration: '6s' }} />
                </div>
                <div className="flex items-center gap-1.5">
                  <Sparkles className="w-3.5 h-3.5 text-brand" />
                  <span className="text-[10px] font-extrabold text-zinc-200">Pembaruan Tersedia!</span>
                </div>
                <p className="text-[9px] text-zinc-400 leading-relaxed">
                  Versi terbaru <b className="text-white">v1.2.0</b> telah rilis. Kami menambahkan optimasi load chat dan kustomisasi cerita teks.
                </p>
                <button
                  onClick={() => alert("Mengunduh APK pembaruan OpenText...")}
                  className="w-full text-center text-[9px] py-1 bg-brand hover:bg-brand-hover text-white font-bold rounded-md transition-colors cursor-pointer"
                >
                  Instal Sekarang (12 MB)
                </button>
              </div>
            </div>
          )}

        </div>

        {/* App Bottom Navigation Bar -- persis struktur BottomNavigationBar.kt di app asli */}
        <div className="h-14 bg-zinc-900/90 border-t border-zinc-900 flex items-center justify-around shrink-0 px-1">
          <button
            onClick={() => setActiveTab("feed")}
            className={`flex flex-col items-center gap-0.5 px-2 py-1 rounded-lg transition-colors cursor-pointer ${
              activeTab === "feed" ? "text-brand-light" : "text-zinc-500"
            }`}
          >
            <Home className="w-4 h-4" fill={activeTab === "feed" ? "currentColor" : "none"} />
            <span className="text-[7px] font-medium">Beranda</span>
          </button>
          <button className="flex flex-col items-center gap-0.5 px-2 py-1 rounded-lg text-zinc-500 cursor-pointer">
            <Search className="w-4 h-4" />
            <span className="text-[7px] font-medium">Cari</span>
          </button>
          <button className="flex flex-col items-center gap-0.5 px-2 py-1 rounded-lg text-zinc-500 cursor-pointer">
            <CirclePlus className="w-4 h-4" />
            <span className="text-[7px] font-medium">Post</span>
          </button>
          <button className="relative flex flex-col items-center gap-0.5 px-2 py-1 rounded-lg text-zinc-500 cursor-pointer">
            <span className="relative">
              <Bell className="w-4 h-4" />
              <span className="absolute -top-1 -right-1.5 min-w-[10px] h-[10px] px-0.5 rounded-full bg-red-500 text-white text-[6px] font-bold flex items-center justify-center leading-none">2</span>
            </span>
            <span className="text-[7px] font-medium">Notifikasi</span>
          </button>
          <button
            onClick={() => setActiveTab("profile")}
            className={`flex flex-col items-center gap-0.5 px-2 py-1 rounded-lg transition-colors cursor-pointer ${
              activeTab === "profile" ? "text-brand-light" : "text-zinc-500"
            }`}
          >
            <User className="w-4 h-4" fill={activeTab === "profile" ? "currentColor" : "none"} />
            <span className="text-[7px] font-medium">Profil</span>
          </button>
        </div>

        {/* Android Home Navigation bar */}
        <div className="h-9 bg-zinc-950 flex items-center justify-center shrink-0 border-t border-zinc-950">
          <div className="w-24 h-1 bg-zinc-700 rounded-full cursor-pointer hover:bg-zinc-500" />
        </div>

      </div>

      {/* Screen Interactive Details panel */}
      <div className="flex-1 bg-zinc-900/30 p-5 rounded-2xl border border-zinc-800/80 max-w-md">
        <h4 className="text-white font-semibold text-lg mb-2 flex items-center gap-2">
          {activeTab === "feed" && "🏠 Home Feed & Link Preview"}
          {activeTab === "story" && "🌸 Cerita Teks 24 Jam"}
          {activeTab === "chat" && "💬 Chat Instan Real-time"}
          {activeTab === "profile" && "🛡️ Profil Privat & Auto Update"}
        </h4>
        <p className="text-zinc-400 text-xs leading-relaxed mb-4">
          {activeTab === "feed" && "Menyediakan timeline tanpa kebisingan gambar atau video. URL yang dibagikan otomatis dirender sebagai cuplikan kaya metadata visual murni (link preview) yang hemat kuota, sehingga tampilan tetap elegan dan kaya informasi."}
          {activeTab === "story" && "Ingin berbagi cerita spontan? Fitur cerita terhapus otomatis setelah 24 jam. Anda dapat memilih warna latar yang dinamis dan menyesuaikan format font. Coba ketik teks atau ubah warna pada simulator ponsel di samping!"}
          {activeTab === "chat" && "Fitur kirim-terima pesan langsung dengan kecepatan real-time memanfaatkan koneksi WebSocket Supabase Realtime Channels. Coba kirim pesan pada form simulator di samping, dan saksikan respon bot mensimulasikan koneksi aktif!"}
          {activeTab === "profile" && "Memiliki kendali penuh privasi akun dengan model persetujuan follow. Juga dilengkapi dengan modul in-app update checker yang mendeteksi rilis versi APK terbaru dari server untuk kelancaran sideloading."}
        </p>

        <div className="p-3 bg-zinc-950 rounded-lg border border-zinc-800 space-y-1.5">
          <div className="text-[10px] text-brand uppercase font-mono font-bold tracking-wider">Implementasi Kode Android</div>
          <div className="text-xs font-semibold text-zinc-300">
            {activeTab === "feed" && "Jetpack Compose + Coil Preview"}
            {activeTab === "story" && "Compose Canvas State Customizer"}
            {activeTab === "chat" && "Supabase Realtime Postgres Callback"}
            {activeTab === "profile" && "Supabase app_versions Auto-Checker"}
          </div>
          <div className="text-[11px] text-zinc-500">
            {activeTab === "feed" && "Parsing URL menggunakan regex client-side, menarik metadata OpenGraph via serverless Supabase Edge Functions untuk menjamin privasi IP client."}
            {activeTab === "story" && "Menggunakan font-sans (Inter), font-mono (JetBrains Mono) dan font-serif secara dinamis di level Kotlin, mempertahankan rendering yang ringan."}
            {activeTab === "chat" && "Saluran real-time dipasang secara asinkron dalam Kotlin Coroutines Flow, menangkap trigger postgres INSERT di tabel messages secara instan."}
            {activeTab === "profile" && "Saat aplikasi dibuka, query membandingkan versionCode lokal dengan records versi global. Jika versi global lebih tinggi, launcher memunculkan modal download."}
          </div>
        </div>
      </div>
    </div>
  );
}