"use client";

import { useState } from "react";
import { AppShell } from "@/components/app-shell";
import { EmptyState } from "@/components/empty-state";
import { getSupabaseBrowser, isSupabaseConfigured } from "@/lib/supabase/browser";
import { useTriForgeData } from "@/lib/hooks/use-triforge-data";
import { Discipline, disciplines, formatDistance } from "@/lib/scoring";

export default function ActivitiesPage() {
  const { activities, refresh } = useTriForgeData();
  const [discipline, setDiscipline] = useState<Discipline>("run");
  const [distanceKm, setDistanceKm] = useState("5");
  const [durationMinutes, setDurationMinutes] = useState("30");
  const [notes, setNotes] = useState("");
  const [message, setMessage] = useState("");
  const configured = isSupabaseConfigured();

  async function submitActivity(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage("");
    if (!configured) {
      setMessage("Supabase is not configured yet.");
      return;
    }

    const supabase = getSupabaseBrowser();
    const { data: auth } = await supabase.auth.getUser();
    if (!auth.user) {
      setMessage("Login first.");
      return;
    }

    const distanceMeters = Math.round(Number(distanceKm) * 1000);
    const durationSeconds = Math.round(Number(durationMinutes) * 60);
    const { error } = await supabase.from("activities").insert({
      user_id: auth.user.id,
      discipline,
      distance_meters: distanceMeters,
      duration_seconds: durationSeconds,
      notes: notes.trim() || null
    });

    if (error) {
      setMessage(error.message);
      return;
    }
    setNotes("");
    setMessage("Activity logged. XP, missions, and Forge Credits were evaluated.");
    await refresh();
  }

  return (
    <AppShell>
      <div className="grid gap-6 lg:grid-cols-[0.8fr_1.2fr]">
        <section className="metric-card rounded-2xl p-6">
          <h1 className="font-display text-3xl">Log activity</h1>
          <p className="mt-2 text-white/55">Manual M2E input for the off-chain MVP.</p>
          {!configured ? <EmptyState title="Supabase required" body="Set env variables and run the schema before logging activities." /> : null}
          <form className="mt-6 space-y-4" onSubmit={submitActivity}>
            <label className="block">
              <span className="text-sm text-white/55">Discipline</span>
              <select className="mt-2 w-full rounded-xl border border-white/10 bg-black/30 p-3 text-white" value={discipline} onChange={(event) => setDiscipline(event.target.value as Discipline)}>
                {disciplines.map((item) => (
                  <option key={item} value={item}>{item}</option>
                ))}
              </select>
            </label>
            <label className="block">
              <span className="text-sm text-white/55">Distance km</span>
              <input className="mt-2 w-full rounded-xl border border-white/10 bg-black/30 p-3 text-white" min="0.1" step="0.1" value={distanceKm} onChange={(event) => setDistanceKm(event.target.value)} />
            </label>
            <label className="block">
              <span className="text-sm text-white/55">Duration minutes</span>
              <input className="mt-2 w-full rounded-xl border border-white/10 bg-black/30 p-3 text-white" min="1" step="1" value={durationMinutes} onChange={(event) => setDurationMinutes(event.target.value)} />
            </label>
            <label className="block">
              <span className="text-sm text-white/55">Notes</span>
              <textarea className="mt-2 min-h-28 w-full rounded-xl border border-white/10 bg-black/30 p-3 text-white" value={notes} onChange={(event) => setNotes(event.target.value)} />
            </label>
            <button className="w-full rounded-xl bg-charge px-5 py-3 font-black text-graphite" type="submit">
              Forge activity
            </button>
            {message ? <p className="text-sm text-current">{message}</p> : null}
          </form>
        </section>

        <section className="metric-card rounded-2xl p-6">
          <h2 className="font-display text-3xl">Activity ledger</h2>
          <div className="mt-5 space-y-3">
            {activities.length === 0 ? <p className="text-white/50">No activities yet.</p> : null}
            {activities.map((activity) => (
              <div className="grid gap-3 rounded-xl bg-white/[0.04] p-4 md:grid-cols-4" key={activity.id}>
                <p className="font-bold capitalize">{activity.discipline}</p>
                <p>{formatDistance(activity.distance_meters)}</p>
                <p>{Math.round(activity.duration_seconds / 60)} min</p>
                <p className="text-white/45">{new Date(activity.performed_at).toLocaleDateString()}</p>
              </div>
            ))}
          </div>
        </section>
      </div>
    </AppShell>
  );
}
