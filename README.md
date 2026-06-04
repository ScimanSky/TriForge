# TriForge

TriForge is a triathlon-first Crypto Move-to-Earn and GameFi MVP. The first release is off-chain: athletes log swim, bike, and run sessions, earn XP, complete missions, and receive simulated Forge Credits in a ledger.

## Stack

- Next.js App Router
- TypeScript
- Tailwind CSS
- Supabase Auth + Postgres
- Vercel Hobby deployment

## Local Setup

```bash
pnpm install
cp .env.example .env.local
pnpm dev
```

Create a Supabase project, paste the values into `.env.local`, then run `supabase/schema.sql` in the Supabase SQL editor.

## Checks

```bash
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

## Archive

The imported SwimForge code is preserved on branch `archive/swimforge-import` and tag `swimforge-import-before-reset`.
