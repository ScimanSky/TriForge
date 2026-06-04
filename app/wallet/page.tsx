"use client";

import { Coins } from "lucide-react";
import { AppShell } from "@/components/app-shell";
import { useTriForgeData } from "@/lib/hooks/use-triforge-data";

export default function WalletPage() {
  const { ledger, creditBalance } = useTriForgeData();

  return (
    <AppShell>
      <div className="grid gap-6 lg:grid-cols-[0.75fr_1.25fr]">
        <section className="metric-card rounded-2xl p-6">
          <Coins className="h-10 w-10 text-forge" />
          <p className="mt-10 text-sm uppercase tracking-[0.28em] text-white/40">Simulated balance</p>
          <h1 className="mt-2 text-6xl font-black text-forge">{creditBalance}</h1>
          <p className="mt-4 text-white/55">
            Forge Credits are not a token, security, or claim on value. They are a gameplay ledger for the MVP.
          </p>
        </section>
        <section className="metric-card rounded-2xl p-6">
          <h2 className="font-display text-3xl">Credit ledger</h2>
          <div className="mt-5 space-y-3">
            {ledger.length === 0 ? <p className="text-white/50">No credit events yet.</p> : null}
            {ledger.map((entry) => (
              <div className="grid gap-3 rounded-xl bg-white/[0.04] p-4 md:grid-cols-[1fr_auto_auto]" key={entry.id}>
                <p>{entry.reason}</p>
                <p className={entry.amount >= 0 ? "text-charge" : "text-ember"}>{entry.amount > 0 ? "+" : ""}{entry.amount}</p>
                <p className="text-white/45">{new Date(entry.created_at).toLocaleDateString()}</p>
              </div>
            ))}
          </div>
        </section>
      </div>
    </AppShell>
  );
}
