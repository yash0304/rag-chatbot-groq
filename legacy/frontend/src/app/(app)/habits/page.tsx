"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Habit } from "@/lib/types";
import { useToast } from "@/components/Toast";

interface CheckinResponse {
  habit: Habit;
  xp_awarded: number;
  multiplier: number;
  level_up: boolean;
  achievements_unlocked: { name: string; icon: string }[];
}

export default function HabitsPage() {
  const [habits, setHabits] = useState<Habit[]>([]);
  const [title, setTitle] = useState("");
  const [cadence, setCadence] = useState("daily");
  const toast = useToast();

  const load = useCallback(() => {
    api.get<Habit[]>("/habits").then(setHabits).catch(() => {});
  }, []);
  useEffect(load, [load]);

  async function create(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim()) return;
    await api.post("/habits", { title, cadence });
    setTitle("");
    toast("Daily mission forged.");
    load();
  }

  async function checkin(id: string) {
    try {
      const res = await api.post<CheckinResponse>(`/habits/${id}/checkin`);
      toast(`Mission complete! +${res.xp_awarded} XP (×${res.multiplier.toFixed(2)} streak bonus)`);
      for (const a of res.achievements_unlocked) toast(`${a.icon} Achievement: ${a.name}`, "levelup");
      load();
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Check-in failed", "error");
    }
  }

  async function remove(id: string) {
    await api.del(`/habits/${id}`);
    load();
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="font-display text-3xl font-bold text-parchment">Daily Missions</h1>
        <p className="text-sm text-slate-500">
          Streaks multiply XP: +5% per day kept, up to ×2.5. Miss a day and the flame resets.
        </p>
      </div>

      <form onSubmit={create} className="card flex flex-wrap items-end gap-3">
        <div className="min-w-48 flex-1">
          <label className="mb-1 block text-xs text-slate-500">New mission</label>
          <input
            className="input"
            placeholder="e.g. Read 20 pages"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </div>
        <div>
          <label className="mb-1 block text-xs text-slate-500">Cadence</label>
          <select className="input" value={cadence} onChange={(e) => setCadence(e.target.value)}>
            <option value="daily">daily</option>
            <option value="weekdays">weekdays</option>
            <option value="weekly">weekly</option>
          </select>
        </div>
        <button className="btn">Forge</button>
      </form>

      <div className="grid gap-4 sm:grid-cols-2">
        {habits.map((h) => (
          <div key={h.id} className="card">
            <div className="mb-2 flex items-start justify-between">
              <h3 className="font-semibold text-parchment">{h.title}</h3>
              <button onClick={() => remove(h.id)} className="text-xs text-red-500 hover:underline">
                remove
              </button>
            </div>
            <div className="mb-4 flex gap-4 text-sm text-slate-400">
              <span>🔥 streak {h.streak}</span>
              <span>🏔️ best {h.best_streak}</span>
              <span className="text-slate-600">{h.cadence}</span>
            </div>
            {h.checked_in_today ? (
              <span className="text-sm text-emerald-400">✓ Completed today</span>
            ) : (
              <button onClick={() => checkin(h.id)} className="btn text-xs">
                Complete today&apos;s mission
              </button>
            )}
          </div>
        ))}
        {habits.length === 0 && (
          <p className="text-sm text-slate-500">No missions yet. Small daily deeds build legends.</p>
        )}
      </div>
    </div>
  );
}
