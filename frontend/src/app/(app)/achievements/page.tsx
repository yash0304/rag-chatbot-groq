"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Achievement, Collectible } from "@/lib/types";
import { RARITY_COLORS } from "@/lib/xp";

export default function AchievementsPage() {
  const [achievements, setAchievements] = useState<Achievement[]>([]);
  const [collectibles, setCollectibles] = useState<Collectible[]>([]);

  useEffect(() => {
    api.get<Achievement[]>("/gamification/achievements").then(setAchievements).catch(() => {});
    api.get<Collectible[]>("/gamification/collectibles").then(setCollectibles).catch(() => {});
  }, []);

  const unlockedCount = achievements.filter((a) => a.unlocked_at).length;

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="font-display text-3xl font-bold text-parchment">Hall of Deeds</h1>
        <p className="text-sm text-slate-500">
          {unlockedCount}/{achievements.length} achievements earned through real work.
        </p>
      </div>

      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {achievements.map((a) => (
          <div key={a.code} className={`card ${a.unlocked_at ? "" : "opacity-40 grayscale"}`}>
            <div className="mb-1 text-2xl">{a.icon}</div>
            <h3 className="text-sm font-semibold text-parchment">{a.name}</h3>
            <p className="mb-2 text-xs text-slate-400">{a.description}</p>
            <div className="text-xs text-rune">
              +{a.xp_bonus} XP {a.unlocked_at && `· ${new Date(a.unlocked_at).toLocaleDateString()}`}
            </div>
          </div>
        ))}
      </section>

      <section>
        <h2 className="font-display mb-4 text-xl font-bold text-parchment">Collectibles</h2>
        {collectibles.length === 0 ? (
          <p className="text-sm text-slate-500">
            No relics yet — rare deeds are rewarded with rare things.
          </p>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            {collectibles.map((c) => (
              <div key={c.code} className="card">
                <div className="mb-1 flex items-center justify-between">
                  <h3 className={`text-sm font-semibold ${RARITY_COLORS[c.rarity] || ""}`}>{c.name}</h3>
                  <span className="tag-chip">{c.rarity}</span>
                </div>
                <p className="text-xs italic text-slate-400">“{c.lore}”</p>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
