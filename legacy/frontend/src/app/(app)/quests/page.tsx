"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Quest } from "@/lib/types";
import { DIFFICULTY_XP } from "@/lib/xp";
import { useToast } from "@/components/Toast";

const DIFFICULTIES = ["trivial", "easy", "normal", "hard", "epic"] as const;

interface CompleteResponse {
  quest: Quest;
  xp_awarded: number;
  level_up: boolean;
  new_level: number;
  achievements_unlocked: { name: string; icon: string }[];
}

export default function QuestsPage() {
  const [quests, setQuests] = useState<Quest[]>([]);
  const [title, setTitle] = useState("");
  const [difficulty, setDifficulty] = useState<(typeof DIFFICULTIES)[number]>("normal");
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const load = useCallback(() => {
    api.get<Quest[]>("/quests").then(setQuests).catch(() => {});
  }, []);
  useEffect(load, [load]);

  async function createQuest(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim()) return;
    await api.post<Quest>("/quests", { title, difficulty });
    setTitle("");
    toast("Quest posted to the board.");
    load();
  }

  async function generate() {
    setBusy(true);
    try {
      const drafts = await api.post<Quest[]>("/quests/generate", { count: 3 });
      toast(`The Questmaster drafted ${drafts.length} quests — accept the ones you'll take.`);
      load();
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Generation failed", "error");
    } finally {
      setBusy(false);
    }
  }

  async function accept(id: string) {
    await api.post(`/quests/${id}/accept`);
    load();
  }

  async function complete(id: string) {
    const res = await api.post<CompleteResponse>(`/quests/${id}/complete`);
    toast(`Quest complete! +${res.xp_awarded} XP`);
    if (res.level_up) toast(`⭐ Level up! You are now level ${res.new_level}`, "levelup");
    for (const a of res.achievements_unlocked) toast(`${a.icon} Achievement: ${a.name}`, "levelup");
    load();
  }

  async function abandon(id: string) {
    await api.del(`/quests/${id}`);
    load();
  }

  const drafts = quests.filter((q) => q.status === "draft");
  const active = quests.filter((q) => q.status === "active");
  const done = quests.filter((q) => q.status === "completed");

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="font-display text-3xl font-bold text-parchment">Quest Board</h1>
          <p className="text-sm text-slate-500">Real tasks, difficulty-scaled XP.</p>
        </div>
        <button onClick={generate} className="btn-ghost" disabled={busy}>
          {busy ? "Consulting the Questmaster…" : "🔮 Generate quests"}
        </button>
      </div>

      <form onSubmit={createQuest} className="card flex flex-wrap items-end gap-3">
        <div className="min-w-48 flex-1">
          <label className="mb-1 block text-xs text-slate-500">New quest</label>
          <input
            className="input"
            placeholder="e.g. Draft the project proposal"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </div>
        <div>
          <label className="mb-1 block text-xs text-slate-500">Difficulty</label>
          <select
            className="input"
            value={difficulty}
            onChange={(e) => setDifficulty(e.target.value as (typeof DIFFICULTIES)[number])}
          >
            {DIFFICULTIES.map((d) => (
              <option key={d} value={d}>
                {d} (+{DIFFICULTY_XP[d]} XP)
              </option>
            ))}
          </select>
        </div>
        <button className="btn">Post quest</button>
      </form>

      {drafts.length > 0 && (
        <section>
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">
            Questmaster drafts
          </h2>
          <div className="grid gap-3 sm:grid-cols-2">
            {drafts.map((q) => (
              <div key={q.id} className="card border-dashed">
                <h3 className="mb-1 font-semibold text-parchment">{q.title}</h3>
                {q.description && <p className="mb-3 text-sm text-slate-400">{q.description}</p>}
                <div className="flex items-center justify-between">
                  <span className="text-xs text-rune">
                    {q.difficulty} · +{q.xp_reward} XP
                  </span>
                  <div className="flex gap-2">
                    <button onClick={() => accept(q.id)} className="btn text-xs">
                      Accept
                    </button>
                    <button onClick={() => abandon(q.id)} className="btn-ghost text-xs">
                      Decline
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      <section>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Active</h2>
        {active.length === 0 && <p className="text-sm text-slate-500">The board is clear.</p>}
        <div className="flex flex-col gap-2">
          {active.map((q) => (
            <div key={q.id} className="card flex items-center justify-between py-3">
              <div>
                <span className="text-slate-200">{q.title}</span>
                <span className="ml-3 text-xs text-rune">
                  {q.difficulty} · +{q.xp_reward} XP
                </span>
              </div>
              <div className="flex gap-2">
                <button onClick={() => complete(q.id)} className="btn text-xs">
                  ✓ Complete
                </button>
                <button onClick={() => abandon(q.id)} className="btn-ghost text-xs">
                  Abandon
                </button>
              </div>
            </div>
          ))}
        </div>
      </section>

      {done.length > 0 && (
        <section>
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">
            Completed ({done.length})
          </h2>
          <div className="flex flex-col gap-1">
            {done.slice(0, 10).map((q) => (
              <div key={q.id} className="flex justify-between text-sm text-slate-500">
                <span className="line-through">{q.title}</span>
                <span>+{q.xp_reward} XP</span>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
