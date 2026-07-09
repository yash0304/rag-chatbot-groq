"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Profile, Skill } from "@/lib/types";
import { useToast } from "@/components/Toast";

const TREE_META: Record<string, { label: string; icon: string }> = {
  scholar: { label: "Scholar", icon: "📚" },
  explorer: { label: "Explorer", icon: "🧭" },
  strategist: { label: "Strategist", icon: "♟️" },
  forger: { label: "Forger", icon: "🔥" },
};

export default function SkillsPage() {
  const [skills, setSkills] = useState<Skill[]>([]);
  const [profile, setProfile] = useState<Profile | null>(null);
  const toast = useToast();

  const load = useCallback(() => {
    api.get<Skill[]>("/gamification/skills").then(setSkills).catch(() => {});
    api.get<Profile>("/gamification/profile").then(setProfile).catch(() => {});
  }, []);
  useEffect(load, [load]);

  async function unlock(code: string) {
    try {
      await api.post(`/gamification/skills/${code}/unlock`);
      toast("Skill unlocked!", "levelup");
      load();
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Cannot unlock", "error");
    }
  }

  const trees = Object.keys(TREE_META);

  return (
    <div className="flex flex-col gap-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl font-bold text-parchment">Skill Trees</h1>
          <p className="text-sm text-slate-500">Skill points are earned by leveling up.</p>
        </div>
        {profile && (
          <span className="rounded-lg bg-rune/20 px-3 py-2 text-sm font-semibold text-rune">
            ✨ {profile.skill_points} points
          </span>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        {trees.map((tree) => (
          <section key={tree} className="card">
            <h2 className="mb-4 font-semibold text-parchment">
              {TREE_META[tree].icon} {TREE_META[tree].label}
            </h2>
            <div className="flex flex-col gap-3">
              {skills
                .filter((s) => s.tree === tree)
                .map((s) => (
                  <div
                    key={s.code}
                    className={`rounded-lg border p-3 ${
                      s.owned
                        ? "border-rune/60 bg-rune/10"
                        : s.available
                          ? "border-slate-600"
                          : "border-slate-800 opacity-50"
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div>
                        <span className="text-sm font-semibold text-parchment">
                          Tier {s.tier} — {s.name}
                        </span>
                        <p className="text-xs text-slate-400">{s.description}</p>
                      </div>
                      {s.owned ? (
                        <span className="text-xs text-rune">owned</span>
                      ) : (
                        <button
                          onClick={() => unlock(s.code)}
                          className="btn-ghost text-xs"
                          disabled={!s.available}
                        >
                          {s.cost} pt{s.cost > 1 ? "s" : ""}
                        </button>
                      )}
                    </div>
                  </div>
                ))}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}
