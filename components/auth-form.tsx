"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { getSupabaseBrowser, isSupabaseConfigured } from "@/lib/supabase/browser";

export function AuthForm({ mode }: { mode: "login" | "register" }) {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [status, setStatus] = useState("");
  const configured = isSupabaseConfigured();

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setStatus("");

    if (!configured) {
      setStatus("Supabase env variables are missing. Add them to .env.local first.");
      return;
    }

    const supabase = getSupabaseBrowser();
    if (mode === "login") {
      const { error } = await supabase.auth.signInWithPassword({ email, password });
      if (error) {
        setStatus(error.message);
        return;
      }
      router.push("/dashboard");
      return;
    }

    const { data, error } = await supabase.auth.signUp({
      email,
      password,
      options: {
        data: {
          display_name: displayName || email.split("@")[0]
        }
      }
    });
    if (error) {
      setStatus(error.message);
      return;
    }

    if (data.user) {
      await supabase.from("profiles").upsert({
        id: data.user.id,
        display_name: displayName || email.split("@")[0]
      });
    }
    router.push("/dashboard");
  }

  return (
    <form className="mt-6 space-y-4" onSubmit={submit}>
      {mode === "register" ? (
        <label className="block">
          <span className="text-sm text-white/55">Athlete name</span>
          <input className="mt-2 w-full rounded-xl border border-white/10 bg-black/30 p-3 text-white" value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
        </label>
      ) : null}
      <label className="block">
        <span className="text-sm text-white/55">Email</span>
        <input className="mt-2 w-full rounded-xl border border-white/10 bg-black/30 p-3 text-white" type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
      </label>
      <label className="block">
        <span className="text-sm text-white/55">Password</span>
        <input className="mt-2 w-full rounded-xl border border-white/10 bg-black/30 p-3 text-white" minLength={6} type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
      </label>
      <button className="w-full rounded-xl bg-forge px-5 py-3 font-black text-graphite" type="submit">
        {mode === "login" ? "Enter TriForge" : "Create account"}
      </button>
      {status ? <p className="text-sm text-current">{status}</p> : null}
    </form>
  );
}
