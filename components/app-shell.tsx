"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Activity, Gauge, ListChecks, LogOut, Wallet } from "lucide-react";
import { BrandMark } from "@/components/brand-mark";
import { getSupabaseBrowser, isSupabaseConfigured } from "@/lib/supabase/browser";

const nav = [
  { href: "/dashboard", label: "Dashboard", icon: Gauge },
  { href: "/activities", label: "Activities", icon: Activity },
  { href: "/missions", label: "Missions", icon: ListChecks },
  { href: "/wallet", label: "Wallet", icon: Wallet }
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();

  async function logout() {
    if (isSupabaseConfigured()) {
      await getSupabaseBrowser().auth.signOut();
    }
    router.push("/");
  }

  return (
    <main className="min-h-screen grid-circuit">
      <aside className="fixed inset-x-0 top-0 z-20 border-b border-white/10 bg-graphite/90 backdrop-blur md:bottom-0 md:left-0 md:right-auto md:w-72 md:border-b-0 md:border-r">
        <div className="flex h-full items-center justify-between gap-4 px-5 py-4 md:flex-col md:items-stretch md:justify-start md:py-6">
          <Link className="flex items-center gap-3" href="/dashboard">
            <BrandMark />
            <span className="font-display text-xl">TriForge</span>
          </Link>
          <nav className="flex gap-2 overflow-x-auto md:mt-10 md:flex-col">
            {nav.map((item) => {
              const active = pathname === item.href;
              return (
                <Link
                  className={`flex min-w-fit items-center gap-3 rounded-xl px-4 py-3 text-sm font-bold ${active ? "bg-charge text-graphite" : "text-white/62 hover:bg-white/8 hover:text-white"}`}
                  href={item.href}
                  key={item.href}
                >
                  <item.icon className="h-5 w-5" />
                  {item.label}
                </Link>
              );
            })}
          </nav>
          <button className="hidden items-center gap-3 rounded-xl px-4 py-3 text-left text-sm font-bold text-white/55 hover:bg-white/8 md:mt-auto md:flex" onClick={logout} type="button">
            <LogOut className="h-5 w-5" />
            Logout
          </button>
        </div>
      </aside>
      <section className="px-5 pb-10 pt-28 md:ml-72 md:px-8 md:pt-8">
        <div className="mx-auto max-w-6xl">{children}</div>
      </section>
    </main>
  );
}
