"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { User } from "@/lib/types";
import { useToast } from "@/components/Toast";

interface Entry {
  hero_name: string;
  level: number;
  xp: number;
}

export default function LeaderboardPage() {
  const [entries, setEntries] = useState<Entry[]>([]);
  const [user, setUser] = useState<User | null>(null);
  const [heroName, setHeroName] = useState("");
  const toast = useToast();

  const load = useCallback(() => {
    api.get<Entry[]>("/leaderboard").then(setEntries).catch(() => {});
    api.get<User>("/users/me").then((u) => {
      setUser(u);
      setHeroName(u.hero_name || "");
    });
  }, []);
  useEffect(load, [load]);

  async function optIn(e: React.FormEvent) {
    e.preventDefault();
    await api.patch("/users/me", { hero_name: heroName, leaderboard_opt_in: true });
    toast("You now stand among the heroes.");
    load();
  }

  async function optOut() {
    await api.patch("/users/me", { leaderboard_opt_in: false });
    toast("You have withdrawn from the rankings.");
    load();
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="font-display text-3xl font-bold text-parchment">Hall of Heroes</h1>
        <p className="text-sm text-slate-500">
          Strictly opt-in. Only your chosen hero alias, level, and XP are ever shown.
        </p>
      </div>

      {user && !user.leaderboard_opt_in ? (
        <form onSubmit={optIn} className="card flex flex-wrap items-end gap-3">
          <div className="min-w-48 flex-1">
            <label className="mb-1 block text-xs text-slate-500">Hero alias (shown publicly)</label>
            <input
              className="input"
              placeholder="e.g. Thalor of the Quiet Vale"
              value={heroName}
              onChange={(e) => setHeroName(e.target.value)}
              required
              maxLength={100}
            />
          </div>
          <button className="btn">Join the rankings</button>
        </form>
      ) : (
        user && (
          <button onClick={optOut} className="btn-ghost self-start text-xs">
            Withdraw from rankings
          </button>
        )
      )}

      <div className="card">
        {entries.length === 0 ? (
          <p className="text-sm text-slate-500">The hall stands empty — be the first to enter.</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
                <th className="pb-3">#</th>
                <th className="pb-3">Hero</th>
                <th className="pb-3 text-right">Level</th>
                <th className="pb-3 text-right">XP</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((e, i) => (
                <tr key={e.hero_name + i} className="border-t border-slate-800">
                  <td className="py-2 text-slate-500">{i + 1}</td>
                  <td className="py-2 text-parchment">
                    {i === 0 && "👑 "}
                    {e.hero_name}
                  </td>
                  <td className="py-2 text-right text-rune">{e.level}</td>
                  <td className="py-2 text-right text-slate-400">{e.xp.toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
