"use client";

import { AppShell } from "@/components/app-shell";
import { useTriForgeData } from "@/lib/hooks/use-triforge-data";

export default function MissionsPage() {
  const { missions, completions } = useTriForgeData();
  const completed = new Set(completions.map((item) => item.mission_id));

  return (
    <AppShell>
      <section className="metric-card rounded-2xl p-6">
        <p className="text-sm uppercase tracking-[0.28em] text-white/40">M2E mission board</p>
        <h1 className="mt-2 font-display text-4xl">Season Zero Missions</h1>
        <div className="mt-6 grid gap-4 md:grid-cols-3">
          {missions.map((mission) => (
            <div className="rounded-2xl border border-white/10 bg-black/24 p-5" key={mission.id}>
              <div className="mb-10 flex items-center justify-between">
                <span className="rounded-full bg-white/10 px-3 py-1 text-xs uppercase tracking-[0.22em] text-white/50">{mission.kind}</span>
                <span className={completed.has(mission.id) ? "text-charge" : "text-white/35"}>{completed.has(mission.id) ? "complete" : "open"}</span>
              </div>
              <h2 className="font-display text-2xl">{mission.title}</h2>
              <p className="mt-3 min-h-16 text-white/55">{mission.description}</p>
              <p className="mt-6 text-sm text-white/45">Reward</p>
              <p className="text-2xl font-black text-forge">{mission.credit_reward} credits</p>
            </div>
          ))}
        </div>
      </section>
    </AppShell>
  );
}
