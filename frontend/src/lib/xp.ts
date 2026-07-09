/** Mirror of the backend level curve (published in the PRD). */

export function xpRequiredForLevel(level: number): number {
  if (level <= 1) return 0;
  return Math.floor(100 * Math.pow(level - 1, 1.6));
}

export function levelForXp(xp: number): number {
  let level = 1;
  while (xpRequiredForLevel(level + 1) <= xp) level += 1;
  return level;
}

export function levelProgress(xp: number): { level: number; pct: number; toNext: number } {
  const level = levelForXp(xp);
  const floor = xpRequiredForLevel(level);
  const next = xpRequiredForLevel(level + 1);
  const span = Math.max(next - floor, 1);
  return { level, pct: Math.round((1000 * (xp - floor)) / span) / 10, toNext: next - xp };
}

export const DIFFICULTY_XP: Record<string, number> = {
  trivial: 10,
  easy: 25,
  normal: 50,
  hard: 100,
  epic: 250,
};

export const RARITY_COLORS: Record<string, string> = {
  common: "text-slate-300",
  rare: "text-sky-300",
  epic: "text-purple-300",
  legendary: "text-rune",
};
