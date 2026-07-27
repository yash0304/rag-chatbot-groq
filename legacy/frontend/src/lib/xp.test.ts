import { describe, expect, it } from "vitest";
import { levelForXp, levelProgress, xpRequiredForLevel } from "./xp";

describe("level curve (must mirror backend)", () => {
  it("anchors", () => {
    expect(xpRequiredForLevel(1)).toBe(0);
    expect(xpRequiredForLevel(2)).toBe(100);
    expect(levelForXp(0)).toBe(1);
    expect(levelForXp(99)).toBe(1);
    expect(levelForXp(100)).toBe(2);
    expect(levelForXp(303)).toBe(3);
  });

  it("is monotonic", () => {
    let prev = -1;
    for (let level = 1; level < 40; level++) {
      const needed = xpRequiredForLevel(level);
      expect(needed).toBeGreaterThan(prev);
      prev = needed;
    }
  });

  it("progress stays within 0-100", () => {
    for (const xp of [0, 50, 100, 250, 1000, 12345]) {
      const { pct } = levelProgress(xp);
      expect(pct).toBeGreaterThanOrEqual(0);
      expect(pct).toBeLessThanOrEqual(100);
    }
  });
});
