create extension if not exists "pgcrypto";

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.activities (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  discipline text not null check (discipline in ('swim', 'bike', 'run')),
  distance_meters integer not null check (distance_meters > 0),
  duration_seconds integer not null check (duration_seconds > 0),
  performed_at timestamptz not null default now(),
  notes text,
  created_at timestamptz not null default now()
);

create table if not exists public.missions (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  title text not null,
  description text not null,
  kind text not null check (kind in ('single_activity', 'weekly_streak', 'discipline_balance')),
  xp_reward integer not null default 0,
  credit_reward integer not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.mission_completions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  mission_id uuid not null references public.missions(id) on delete cascade,
  completed_at timestamptz not null default now(),
  unique (user_id, mission_id)
);

create table if not exists public.xp_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  activity_id uuid references public.activities(id) on delete set null,
  mission_id uuid references public.missions(id) on delete set null,
  amount integer not null,
  reason text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.forge_credit_ledger (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  activity_id uuid references public.activities(id) on delete set null,
  mission_id uuid references public.missions(id) on delete set null,
  amount integer not null,
  reason text not null,
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.activities enable row level security;
alter table public.missions enable row level security;
alter table public.mission_completions enable row level security;
alter table public.xp_events enable row level security;
alter table public.forge_credit_ledger enable row level security;

create policy "profiles_select_own" on public.profiles for select using (auth.uid() = id);
create policy "profiles_insert_own" on public.profiles for insert with check (auth.uid() = id);
create policy "profiles_update_own" on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);

create policy "activities_select_own" on public.activities for select using (auth.uid() = user_id);
create policy "activities_insert_own" on public.activities for insert with check (auth.uid() = user_id);
create policy "activities_update_own" on public.activities for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "missions_select_active" on public.missions for select using (active = true);
create policy "mission_completions_select_own" on public.mission_completions for select using (auth.uid() = user_id);
create policy "xp_events_select_own" on public.xp_events for select using (auth.uid() = user_id);
create policy "forge_credit_ledger_select_own" on public.forge_credit_ledger for select using (auth.uid() = user_id);

create or replace function public.score_activity()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  xp integer;
  credits integer;
begin
  xp := greatest(10, round(new.distance_meters / 100.0)::integer + round(new.duration_seconds / 120.0)::integer);
  credits := greatest(1, floor(xp / 10.0)::integer);

  insert into public.xp_events (user_id, activity_id, amount, reason)
  values (new.user_id, new.id, xp, 'activity:' || new.discipline);

  insert into public.forge_credit_ledger (user_id, activity_id, amount, reason)
  values (new.user_id, new.id, credits, 'activity:' || new.discipline);

  perform public.evaluate_missions(new.user_id, new.id);
  return new;
end;
$$;

create or replace function public.claim_mission(target_user_id uuid, mission_slug text, target_activity_id uuid default null)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  mission_row public.missions%rowtype;
begin
  select * into mission_row from public.missions where slug = mission_slug and active = true limit 1;
  if mission_row.id is null then
    return;
  end if;

  insert into public.mission_completions (user_id, mission_id)
  values (target_user_id, mission_row.id)
  on conflict do nothing;

  if found then
    insert into public.xp_events (user_id, activity_id, mission_id, amount, reason)
    values (target_user_id, target_activity_id, mission_row.id, mission_row.xp_reward, 'mission:' || mission_row.slug);

    insert into public.forge_credit_ledger (user_id, activity_id, mission_id, amount, reason)
    values (target_user_id, target_activity_id, mission_row.id, mission_row.credit_reward, 'mission:' || mission_row.slug);
  end if;
end;
$$;

create or replace function public.evaluate_missions(target_user_id uuid, target_activity_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  discipline_count integer;
  streak_count integer;
begin
  perform public.claim_mission(target_user_id, 'first-forge', target_activity_id);

  select count(distinct discipline)
    into discipline_count
    from public.activities
    where user_id = target_user_id
      and performed_at >= now() - interval '7 days';

  if discipline_count >= 3 then
    perform public.claim_mission(target_user_id, 'tri-balance', target_activity_id);
  end if;

  select count(distinct performed_at::date)
    into streak_count
    from public.activities
    where user_id = target_user_id
      and performed_at >= now() - interval '7 days';

  if streak_count >= 3 then
    perform public.claim_mission(target_user_id, 'streak-ignite', target_activity_id);
  end if;
end;
$$;

drop trigger if exists on_activity_score on public.activities;
create trigger on_activity_score
after insert on public.activities
for each row execute function public.score_activity();

insert into public.missions (slug, title, description, kind, xp_reward, credit_reward)
values
  ('first-forge', 'First Forge', 'Log your first swim, bike, or run session.', 'single_activity', 50, 25),
  ('tri-balance', 'Tri-Balance', 'Complete swim, bike, and run within seven days.', 'discipline_balance', 150, 80),
  ('streak-ignite', 'Streak Ignite', 'Train on three distinct days within seven days.', 'weekly_streak', 120, 60)
on conflict (slug) do update set
  title = excluded.title,
  description = excluded.description,
  kind = excluded.kind,
  xp_reward = excluded.xp_reward,
  credit_reward = excluded.credit_reward,
  active = true;
