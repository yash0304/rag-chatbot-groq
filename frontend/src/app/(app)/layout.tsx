"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, logout } from "@/lib/api";
import { isLoggedIn } from "@/lib/auth";
import type { Profile } from "@/lib/types";
import XPBar from "@/components/XPBar";
import { ToastProvider } from "@/components/Toast";

const NAV = [
  { href: "/dashboard", label: "Dashboard", icon: "🏰" },
  { href: "/documents", label: "Archives", icon: "📜" },
  { href: "/chat", label: "Narrator", icon: "🔮" },
  { href: "/map", label: "World Map", icon: "🗺️" },
  { href: "/quests", label: "Quests", icon: "⚔️" },
  { href: "/habits", label: "Daily Missions", icon: "🔥" },
  { href: "/goals", label: "Story Arcs", icon: "📖" },
  { href: "/skills", label: "Skills", icon: "✨" },
  { href: "/achievements", label: "Achievements", icon: "🏆" },
  { href: "/analytics", label: "Analytics", icon: "📊" },
  { href: "/review", label: "Weekly Review", icon: "🕯️" },
  { href: "/leaderboard", label: "Leaderboard", icon: "🏅" },
];

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    if (!isLoggedIn()) {
      router.replace("/login");
      return;
    }
    api.get<Profile>("/gamification/profile").then(setProfile).catch(() => {});
  }, [router, pathname]);

  async function onLogout() {
    await logout();
    router.replace("/login");
  }

  return (
    <ToastProvider>
      <div className="flex min-h-screen">
        <aside
          className={`fixed inset-y-0 left-0 z-40 w-64 transform border-r border-slate-800 bg-realm p-4 transition-transform lg:static lg:translate-x-0 ${
            menuOpen ? "translate-x-0" : "-translate-x-full"
          }`}
        >
          <div className="mb-6 flex items-center justify-between">
            <Link href="/dashboard" className="font-display text-lg font-bold text-rune">
              MindQuest
            </Link>
            <button className="text-slate-500 lg:hidden" onClick={() => setMenuOpen(false)}>
              ✕
            </button>
          </div>
          {profile && (
            <div className="mb-6">
              <XPBar
                level={profile.level}
                pct={profile.progress_pct}
                xp={profile.xp}
                toNext={profile.xp_for_next_level - profile.xp}
              />
            </div>
          )}
          <nav className="flex flex-col gap-1">
            {NAV.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setMenuOpen(false)}
                className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition ${
                  pathname.startsWith(item.href)
                    ? "bg-abyss font-semibold text-rune"
                    : "text-slate-400 hover:bg-abyss hover:text-slate-200"
                }`}
              >
                <span>{item.icon}</span>
                {item.label}
              </Link>
            ))}
          </nav>
          <button onClick={onLogout} className="btn-ghost mt-8 w-full justify-center text-xs">
            Leave the realm
          </button>
        </aside>

        <div className="flex-1">
          <header className="flex items-center gap-3 border-b border-slate-800 p-4 lg:hidden">
            <button className="text-slate-300" onClick={() => setMenuOpen(true)}>
              ☰
            </button>
            <span className="font-display font-bold text-rune">MindQuest</span>
          </header>
          <main className="mx-auto max-w-5xl p-6">{children}</main>
        </div>
      </div>
    </ToastProvider>
  );
}
