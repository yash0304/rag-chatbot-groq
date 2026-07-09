"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Habit, Quest, Summary, User } from "@/lib/types";
import StatTile from "@/components/StatTile";

export default function Dashboard() {
  const [user, setUser] = useState<User | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [quests, setQuests] = useState<Quest[]>([]);
  const [habits, setHabits] = useState<Habit[]>([]);

  useEffect(() => {
    api.get<User>("/users/me").then(setUser).catch(() => {});
    api.get<Summary>("/analytics/summary").then(setSummary).catch(() => {});
    api.get<Quest[]>("/quests?status_filter=active").then(setQuests).catch(() => {});
    api.get<Habit[]>("/habits").then(setHabits).catch(() => {});
  }, []);

  const pendingMissions = habits.filter((h) => !h.checked_in_today);

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="font-display text-3xl font-bold text-parchment">
          {user ? `Hail, ${user.hero_name || user.display_name}` : "Hail, hero"}
        </h1>
        <p className="text-sm text-slate-500">The realm remembers what you build today.</p>
      </div>

      {summary && (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatTile label="Level" value={summary.level} icon="⭐" />
          <StatTile label="XP this week" value={summary.xp_7d} icon="⚡" />
          <StatTile label="Tomes archived" value={summary.documents} icon="📜" />
          <StatTile label="Best active streak" value={summary.current_streak_max} icon="🔥" />
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="card">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-semibold text-parchment">⚔️ Active quests</h2>
            <Link href="/quests" className="text-xs text-rune hover:underline">
              Quest board →
            </Link>
          </div>
          {quests.length === 0 ? (
            <p className="text-sm text-slate-500">
              No active quests. Visit the quest board or ask the Narrator to draft some.
            </p>
          ) : (
            <ul className="flex flex-col gap-2">
              {quests.slice(0, 5).map((q) => (
                <li key={q.id} className="flex items-center justify-between text-sm">
                  <span className="text-slate-300">{q.title}</span>
                  <span className="text-xs text-rune">+{q.xp_reward} XP</span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="card">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-semibold text-parchment">🔥 Today&apos;s missions</h2>
            <Link href="/habits" className="text-xs text-rune hover:underline">
              All missions →
            </Link>
          </div>
          {habits.length === 0 ? (
            <p className="text-sm text-slate-500">No daily missions yet — forge a habit.</p>
          ) : pendingMissions.length === 0 ? (
            <p className="text-sm text-emerald-400">All missions complete. The campfires stay lit.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {pendingMissions.slice(0, 5).map((h) => (
                <li key={h.id} className="flex items-center justify-between text-sm">
                  <span className="text-slate-300">{h.title}</span>
                  <span className="text-xs text-slate-500">streak {h.streak}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section className="card">
        <h2 className="mb-2 font-semibold text-parchment">🔮 Consult the Narrator</h2>
        <p className="mb-4 text-sm text-slate-500">
          Ask anything about your archives — answers come with citations to your own documents.
        </p>
        <Link href="/chat" className="btn">
          Open a conversation
        </Link>
      </section>
    </div>
  );
}
