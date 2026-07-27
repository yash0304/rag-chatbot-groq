"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Goal } from "@/lib/types";
import { useToast } from "@/components/Toast";

interface MilestoneResponse {
  goal: Goal;
  xp_awarded: number;
  goal_completed: boolean;
  achievements_unlocked: { name: string; icon: string }[];
}

export default function GoalsPage() {
  const [goals, setGoals] = useState<Goal[]>([]);
  const [title, setTitle] = useState("");
  const [milestones, setMilestones] = useState("");
  const toast = useToast();

  const load = useCallback(() => {
    api.get<Goal[]>("/goals").then(setGoals).catch(() => {});
  }, []);
  useEffect(load, [load]);

  async function create(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim()) return;
    const ms = milestones
      .split("\n")
      .map((m) => m.trim())
      .filter(Boolean);
    await api.post("/goals", { title, milestones: ms });
    setTitle("");
    setMilestones("");
    toast("A new story arc begins.");
    load();
  }

  async function completeMilestone(goalId: string, milestoneId: string) {
    const res = await api.post<MilestoneResponse>(
      `/goals/${goalId}/milestones/${milestoneId}/complete`
    );
    toast(`Chapter complete! +${res.xp_awarded} XP`);
    if (res.goal_completed) toast("📖 Story arc complete!", "levelup");
    for (const a of res.achievements_unlocked) toast(`${a.icon} Achievement: ${a.name}`, "levelup");
    load();
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="font-display text-3xl font-bold text-parchment">Story Arcs</h1>
        <p className="text-sm text-slate-500">
          Long-term goals as narrative arcs — each milestone is a chapter.
        </p>
      </div>

      <form onSubmit={create} className="card flex flex-col gap-3">
        <input
          className="input"
          placeholder="Goal — e.g. Learn Spanish"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
        <textarea
          className="input min-h-24"
          placeholder={"Milestones, one per line:\nFinish beginner course\nHold a 10-minute conversation"}
          value={milestones}
          onChange={(e) => setMilestones(e.target.value)}
        />
        <button className="btn self-start">Begin the arc</button>
      </form>

      <div className="flex flex-col gap-4">
        {goals.map((g) => {
          const doneCount = g.milestones.filter((m) => m.completed).length;
          return (
            <div key={g.id} className="card">
              <div className="mb-1 flex items-center justify-between">
                <h3 className="font-semibold text-parchment">{g.title}</h3>
                <span
                  className={`text-xs ${g.status === "completed" ? "text-emerald-400" : "text-slate-500"}`}
                >
                  {g.status === "completed" ? "✓ arc complete" : `${doneCount}/${g.milestones.length} chapters`}
                </span>
              </div>
              {g.arc_theme && <p className="mb-3 text-sm italic text-rune">“{g.arc_theme}”</p>}
              <ol className="flex flex-col gap-2">
                {g.milestones.map((m) => (
                  <li key={m.id} className="flex items-center gap-3 text-sm">
                    {m.completed ? (
                      <span className="text-emerald-400">✓</span>
                    ) : (
                      <button
                        onClick={() => completeMilestone(g.id, m.id)}
                        className="h-4 w-4 rounded-full border border-slate-600 hover:border-rune"
                        aria-label={`Complete ${m.title}`}
                      />
                    )}
                    <span className={m.completed ? "text-slate-500 line-through" : "text-slate-300"}>
                      Chapter {m.seq + 1}: {m.title}
                    </span>
                  </li>
                ))}
              </ol>
            </div>
          );
        })}
        {goals.length === 0 && (
          <p className="text-sm text-slate-500">No arcs yet. Every legend starts with a first chapter.</p>
        )}
      </div>
    </div>
  );
}
