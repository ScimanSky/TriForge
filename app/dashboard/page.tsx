"use client";

import { Activity, Bike, Coins, Flame, Waves } from "lucide-react";
import { AppShell } from "@/components/app-shell";
import { EmptyState } from "@/components/empty-state";
import { useTriForgeData } from "@/lib/hooks/use-triforge-data";
import { formatDistance, getLevelFromXp } from "@/lib/scoring";

export default function DashboardPage() {
  const { profile, activities, xpTotal, creditBalance, streak, loading, supabaseReady } = useTriForgeData();
  const level = getLevelFromXp(xpTotal);
  const totals = {
    swim: activities.filter((item) => item.discipline === "swim").reduce((sum, item) => sum + item.distance_meters, 0),
    bike: activities.filter((item) => item.discipline === "bike").reduce((sum, item) => sum + item.distance_meters, 0),
    run: activities.filter((item) => item.discipline === "run").reduce((sum, item) => sum + item.distance_meters, 0)
  };

  return (
    <AppShell>
      <div className="space-y-6">
        {!supabaseReady ? <EmptyState title="Connect Supabase" body="Add your Supabase URL and anon key to .env.local to enable auth, activity inserts, XP, missions, and Forge Credits." /> : null}
        <section className="metric-card rounded-2xl p-6">
          <div className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
            <div>
              <p className="text-sm uppercase tracking-[0.28em] text-white/40">Athlete command deck</p>
              <h1 className="mt-2 font-display text-4xl text-white">{profile?.display_name ?? "TriForge Athlete"}</h1>
              <p className="mt-3 text-white/55">Off-chain XP and simulated credits for triathlon consistency.</p>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <Stat label="Level" value={String(level)} icon={<Flame className="h-5 w-5 text-ember" />} />
              <Stat label="XP" value={xpTotal.toLocaleString()} icon={<Activity className="h-5 w-5 text-current" />} />
              <Stat label="Credits" value={creditBalance.toLocaleString()} icon={<Coins className="h-5 w-5 text-forge" />} />
            </div>
          </div>
        </section>

        <section className="grid gap-4 md:grid-cols-3">
          <DisciplineCard icon={<Waves />} label="Swim" value={formatDistance(totals.swim)} color="text-current" />
          <DisciplineCard icon={<Bike />} label="Bike" value={formatDistance(totals.bike)} color="text-charge" />
          <DisciplineCard icon={<Activity />} label="Run" value={formatDistance(totals.run)} color="text-forge" />
        </section>

        <section className="grid gap-4 lg:grid-cols-[1fr_0.8fr]">
          <div className="metric-card rounded-2xl p-5">
            <h2 className="font-display text-2xl">Recent sessions</h2>
            <div className="mt-4 space-y-3">
              {loading ? <p className="text-white/50">Loading...</p> : null}
              {!loading && activities.length === 0 ? <p className="text-white/50">No activity yet. Log the first session.</p> : null}
              {activities.slice(0, 5).map((activity) => (
                <div className="flex items-center justify-between rounded-xl bg-white/[0.04] p-4" key={activity.id}>
                  <div>
                    <p className="font-bold capitalize">{activity.discipline}</p>
                    <p className="text-sm text-white/45">{new Date(activity.performed_at).toLocaleDateString()}</p>
                  </div>
                  <p className="font-black">{formatDistance(activity.distance_meters)}</p>
                </div>
              ))}
            </div>
          </div>
          <div className="metric-card rounded-2xl p-5">
            <h2 className="font-display text-2xl">Streak engine</h2>
            <p className="mt-7 text-7xl font-black text-charge">{streak}</p>
            <p className="mt-3 text-white/55">active training days</p>
            <div className="mt-6 h-3 overflow-hidden rounded-full bg-white/10">
              <div className="h-full rounded-full bg-charge" style={{ width: `${Math.min(100, streak * 14)}%` }} />
            </div>
          </div>
        </section>
      </div>
    </AppShell>
  );
}

function Stat({ label, value, icon }: { label: string; value: string; icon: React.ReactNode }) {
  return (
    <div className="min-w-24 rounded-xl border border-white/10 bg-black/20 p-3">
      {icon}
      <p className="mt-2 text-2xl font-black">{value}</p>
      <p className="text-xs uppercase tracking-[0.2em] text-white/40">{label}</p>
    </div>
  );
}

function DisciplineCard({ label, value, icon, color }: { label: string; value: string; icon: React.ReactNode; color: string }) {
  return (
    <div className="metric-card rounded-2xl p-5">
      <div className={`mb-10 h-8 w-8 ${color}`}>{icon}</div>
      <p className="text-4xl font-black">{value}</p>
      <p className="mt-2 text-sm uppercase tracking-[0.28em] text-white/42">{label}</p>
    </div>
  );
}
