"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Summary } from "@/lib/types";
import StatTile from "@/components/StatTile";

interface DayXP {
  date: string;
  xp: number;
}

export default function AnalyticsPage() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [series, setSeries] = useState<DayXP[]>([]);
  const [heatmap, setHeatmap] = useState<{ date: string; count: number }[]>([]);

  useEffect(() => {
    api.get<Summary>("/analytics/summary").then(setSummary).catch(() => {});
    api.get<DayXP[]>("/analytics/xp-daily?days=30").then(setSeries).catch(() => {});
    api
      .get<{ date: string; count: number }[]>("/analytics/activity-heatmap?weeks=12")
      .then(setHeatmap)
      .catch(() => {});
  }, []);

  const maxXp = Math.max(...series.map((d) => d.xp), 1);
  const heatByDate = new Map(heatmap.map((h) => [h.date, h.count]));
  const days: string[] = [];
  for (let i = 83; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    days.push(d.toISOString().slice(0, 10));
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="font-display text-3xl font-bold text-parchment">Chronicles</h1>
        <p className="text-sm text-slate-500">The measurable record of your campaign.</p>
      </div>

      {summary && (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatTile label="Total XP" value={summary.xp_total.toLocaleString()} icon="⚡" />
          <StatTile label="Quests done" value={summary.quests_completed} icon="⚔️" />
          <StatTile label="Documents" value={summary.documents} icon="📜" />
          <StatTile label="Active habits" value={summary.habits_active} icon="🔥" />
        </div>
      )}

      <section className="card">
        <h2 className="mb-4 text-sm font-semibold text-parchment">XP — last 30 days</h2>
        <div className="flex h-40 items-end gap-1">
          {series.map((d) => (
            <div
              key={d.date}
              title={`${d.date}: ${d.xp} XP`}
              className="flex-1 rounded-t bg-gradient-to-t from-amber-700 to-rune"
              style={{ height: `${Math.max((d.xp / maxXp) * 100, 2)}%` }}
            />
          ))}
        </div>
      </section>

      <section className="card">
        <h2 className="mb-4 text-sm font-semibold text-parchment">Activity — last 12 weeks</h2>
        <div className="grid grid-flow-col grid-rows-7 gap-1">
          {days.map((date) => {
            const count = heatByDate.get(date) || 0;
            const intensity =
              count === 0 ? "bg-slate-800" : count < 3 ? "bg-amber-900" : count < 6 ? "bg-amber-600" : "bg-rune";
            return <div key={date} title={`${date}: ${count} actions`} className={`h-4 w-4 rounded-sm ${intensity}`} />;
          })}
        </div>
      </section>
    </div>
  );
}
