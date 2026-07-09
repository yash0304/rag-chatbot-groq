"use client";

import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { WeeklyReview } from "@/lib/types";
import { useToast } from "@/components/Toast";

export default function ReviewPage() {
  const [reviews, setReviews] = useState<WeeklyReview[]>([]);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  useEffect(() => {
    api.get<WeeklyReview[]>("/reviews").then(setReviews).catch(() => {});
  }, []);

  async function generate() {
    setBusy(true);
    try {
      const review = await api.post<WeeklyReview>("/reviews/weekly");
      setReviews((prev) => [review, ...prev.filter((r) => r.id !== review.id)]);
      toast("The Narrator has written this week's chronicle.");
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Review failed", "error");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="font-display text-3xl font-bold text-parchment">Weekly Review</h1>
          <p className="text-sm text-slate-500">
            An honest chronicle of the week, told by the Narrator — plus one suggestion for the next.
          </p>
        </div>
        <button onClick={generate} className="btn" disabled={busy}>
          {busy ? "Writing…" : "🕯️ Chronicle this week"}
        </button>
      </div>

      {reviews.length === 0 && (
        <p className="text-sm text-slate-500">No chronicles yet. Generate your first weekly review.</p>
      )}

      {reviews.map((r) => (
        <article key={r.id} className="card">
          <h2 className="mb-3 text-sm font-semibold text-rune">Week of {r.week_start}</h2>
          <p className="mb-4 whitespace-pre-wrap text-sm leading-relaxed text-slate-300">{r.narrative}</p>
          <div className="mb-4 flex flex-wrap gap-4 text-xs text-slate-500">
            <span>⚡ {String(r.stats.xp_earned ?? 0)} XP</span>
            <span>⚔️ {String(r.stats.quests_completed ?? 0)} quests</span>
            <span>🔥 {String(r.stats.habit_checkins ?? 0)} check-ins</span>
            <span>📜 {String(r.stats.documents_processed ?? 0)} documents</span>
          </div>
          {r.suggestions.length > 0 && (
            <div className="rounded-lg bg-abyss p-3 text-sm text-slate-400">
              <span className="font-semibold text-parchment">Next week: </span>
              {r.suggestions.join(" ")}
            </div>
          )}
        </article>
      ))}
    </div>
  );
}
