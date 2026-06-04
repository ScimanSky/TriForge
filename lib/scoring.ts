export const disciplines = ["swim", "bike", "run"] as const;

export type Discipline = (typeof disciplines)[number];

export function getXpForActivity(distanceMeters: number, durationSeconds: number): number {
  const distanceXp = Math.round(distanceMeters / 100);
  const durationXp = Math.round(durationSeconds / 120);
  return Math.max(10, distanceXp + durationXp);
}

export function getCreditsForXp(xp: number): number {
  return Math.max(1, Math.floor(xp / 10));
}

export function getLevelFromXp(xp: number): number {
  return Math.floor(Math.sqrt(Math.max(0, xp) / 120)) + 1;
}

export function formatDistance(distanceMeters: number): string {
  if (distanceMeters >= 1000) {
    return `${(distanceMeters / 1000).toFixed(distanceMeters >= 10000 ? 0 : 1)} km`;
  }
  return `${distanceMeters} m`;
}
