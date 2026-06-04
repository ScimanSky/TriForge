"use client";

import Link from "next/link";
import { AuthForm } from "@/components/auth-form";
import { BrandMark } from "@/components/brand-mark";

export default function LoginPage() {
  return (
    <main className="flex min-h-screen items-center justify-center px-5">
      <div className="metric-card w-full max-w-md rounded-2xl p-6">
        <Link className="mb-8 flex items-center gap-3" href="/">
          <BrandMark />
          <span className="font-display text-2xl">TriForge</span>
        </Link>
        <h1 className="font-display text-4xl">Login</h1>
        <AuthForm mode="login" />
        <p className="mt-6 text-sm text-white/55">
          New athlete? <Link className="text-charge" href="/register">Create an account</Link>
        </p>
      </div>
    </main>
  );
}
