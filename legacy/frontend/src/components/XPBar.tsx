"use client";

export default function XPBar({
  level,
  pct,
  xp,
  toNext,
}: {
  level: number;
  pct: number;
  xp: number;
  toNext: number;
}) {
  return (
    <div>
      <div className="mb-1 flex items-baseline justify-between text-xs">
        <span className="font-semibold text-rune">Level {level}</span>
        <span className="text-slate-500">
          {xp.toLocaleString()} XP · {toNext.toLocaleString()} to next level
        </span>
      </div>
      <div className="h-2.5 overflow-hidden rounded-full bg-slate-800">
        <div
          className="h-full rounded-full bg-gradient-to-r from-amber-600 to-rune transition-all"
          style={{ width: `${Math.min(pct, 100)}%` }}
        />
      </div>
    </div>
  );
}
