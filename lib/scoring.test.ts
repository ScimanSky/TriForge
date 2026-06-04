import { describe, expect, it } from "vitest";
import { formatDistance, getCreditsForXp, getLevelFromXp, getXpForActivity } from "./scoring";

describe("TriForge scoring", () => {
  it("awards a minimum amount of XP", () => {
    expect(getXpForActivity(100, 120)).toBeGreaterThanOrEqual(10);
  });

  it("converts XP to simulated credits", () => {
    expect(getCreditsForXp(10)).toBe(1);
    expect(getCreditsForXp(95)).toBe(9);
  });

  it("levels up from accumulated XP", () => {
    expect(getLevelFromXp(0)).toBe(1);
    expect(getLevelFromXp(480)).toBe(3);
  });

  it("formats distance for the UI", () => {
    expect(formatDistance(750)).toBe("750 m");
    expect(formatDistance(5200)).toBe("5.2 km");
  });
});
