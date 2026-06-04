import Link from "next/link";
import { Activity, Bike, Coins, Flame, ShieldCheck, Waves } from "lucide-react";
import { BrandMark } from "@/components/brand-mark";

const tracks = [
  { label: "Swim", icon: Waves, value: "1.9 km", tone: "text-current" },
  { label: "Bike", icon: Bike, value: "74 km", tone: "text-charge" },
  { label: "Run", icon: Activity, value: "18 km", tone: "text-forge" }
];

export default function LandingPage() {
  return (
    <main className="min-h-screen grid-circuit">
      <nav className="mx-auto flex max-w-7xl items-center justify-between px-5 py-6">
        <Link className="flex items-center gap-3" href="/">
          <BrandMark />
          <span className="font-display text-xl tracking-wide">TriForge</span>
        </Link>
        <div className="flex items-center gap-3">
          <Link className="rounded-full border border-white/10 px-4 py-2 text-sm text-white/75 hover:text-white" href="/login">
            Login
          </Link>
          <Link className="rounded-full bg-charge px-4 py-2 text-sm font-bold text-graphite shadow-glow" href="/register">
            Start
          </Link>
        </div>
      </nav>

      <section className="mx-auto grid max-w-7xl gap-10 px-5 pb-16 pt-10 lg:grid-cols-[1.05fr_0.95fr] lg:items-center lg:pt-20">
        <div>
          <p className="mb-5 inline-flex rounded-full border border-current/25 bg-current/10 px-4 py-2 text-sm text-current">
            Off-chain M2E prototype for triathlon athletes
          </p>
          <h1 className="max-w-4xl font-display text-5xl leading-[0.95] tracking-tight text-white sm:text-7xl">
            Train the three disciplines. Forge the fourth economy.
          </h1>
          <p className="mt-7 max-w-2xl text-lg leading-8 text-white/68">
            TriForge turns swim, bike, and run sessions into XP, streaks, missions, and simulated Forge Credits.
            No wallet custody, no token promises, just a playable training economy ready for the next layer.
          </p>
          <div className="mt-9 flex flex-wrap gap-4">
            <Link className="rounded-full bg-forge px-6 py-3 font-bold text-graphite shadow-forge" href="/register">
              Create athlete profile
            </Link>
            <Link className="rounded-full border border-white/15 px-6 py-3 font-bold text-white/80 hover:border-current hover:text-current" href="/dashboard">
              View dashboard
            </Link>
          </div>
          <div className="mt-10 grid max-w-xl grid-cols-3 gap-3">
            {tracks.map((track) => (
              <div className="metric-card rounded-xl p-4" key={track.label}>
                <track.icon className={`mb-5 h-6 w-6 ${track.tone}`} />
                <p className="text-2xl font-black">{track.value}</p>
                <p className="text-sm uppercase tracking-[0.25em] text-white/42">{track.label}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="scanline metric-card relative min-h-[560px] overflow-hidden rounded-2xl p-6">
          <div className="absolute -right-20 top-12 h-64 w-64 rounded-full border border-current/20" />
          <div className="absolute bottom-8 left-8 h-44 w-44 rounded-full border border-forge/25" />
          <div className="relative z-10">
            <div className="mb-8 flex items-center justify-between">
              <div>
                <p className="text-sm uppercase tracking-[0.3em] text-white/42">Forge Ledger</p>
                <p className="font-display text-3xl">Season Zero</p>
              </div>
              <ShieldCheck className="h-9 w-9 text-charge" />
            </div>
            <div className="rounded-2xl border border-white/10 bg-black/28 p-5">
              <div className="flex items-center justify-between border-b border-white/10 pb-4">
                <span className="text-white/55">Level</span>
                <span className="font-display text-4xl text-charge">08</span>
              </div>
              <div className="mt-5 h-3 overflow-hidden rounded-full bg-white/10">
                <div className="h-full w-[68%] rounded-full bg-gradient-to-r from-current via-charge to-forge" />
              </div>
              <div className="mt-5 grid grid-cols-2 gap-3">
                <div className="rounded-xl bg-white/[0.05] p-4">
                  <Flame className="mb-4 h-6 w-6 text-ember" />
                  <p className="text-3xl font-black">5</p>
                  <p className="text-sm text-white/46">day streak</p>
                </div>
                <div className="rounded-xl bg-white/[0.05] p-4">
                  <Coins className="mb-4 h-6 w-6 text-forge" />
                  <p className="text-3xl font-black">420</p>
                  <p className="text-sm text-white/46">Forge Credits</p>
                </div>
              </div>
            </div>
            <div className="mt-5 space-y-3">
              {["Complete one session", "Train all three disciplines", "Hold a weekly streak"].map((item, index) => (
                <div className="flex items-center justify-between rounded-xl border border-white/10 bg-white/[0.04] p-4" key={item}>
                  <span>{item}</span>
                  <span className={index === 0 ? "text-charge" : "text-white/35"}>{index === 0 ? "claimed" : "queued"}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
