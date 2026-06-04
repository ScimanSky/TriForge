"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ActivityRow, ForgeCreditLedgerRow, MissionCompletionRow, MissionRow, Profile, XpEventRow } from "@/lib/supabase/types";
import { getSupabaseBrowser, isSupabaseConfigured } from "@/lib/supabase/browser";

type State = {
  profile: Profile | null;
  activities: ActivityRow[];
  missions: MissionRow[];
  completions: MissionCompletionRow[];
  ledger: ForgeCreditLedgerRow[];
  xpEvents: XpEventRow[];
  loading: boolean;
  supabaseReady: boolean;
};

const emptyState: State = {
  profile: null,
  activities: [],
  missions: [],
  completions: [],
  ledger: [],
  xpEvents: [],
  loading: false,
  supabaseReady: false
};

export function useTriForgeData() {
  const [state, setState] = useState<State>({ ...emptyState, supabaseReady: isSupabaseConfigured() });

  const refresh = useCallback(async () => {
    if (!isSupabaseConfigured()) {
      setState({ ...emptyState, supabaseReady: false });
      return;
    }

    const supabase = getSupabaseBrowser();
    setState((current) => ({ ...current, loading: true, supabaseReady: true }));

    const { data: auth } = await supabase.auth.getUser();
    const user = auth.user;
    if (!user) {
      const { data: missions } = await supabase.from("missions").select("*").eq("active", true).order("created_at");
      setState({ ...emptyState, missions: missions ?? [], loading: false, supabaseReady: true });
      return;
    }

    const [profileResult, activitiesResult, missionsResult, completionsResult, ledgerResult, xpResult] = await Promise.all([
      supabase.from("profiles").select("*").eq("id", user.id).maybeSingle(),
      supabase.from("activities").select("*").eq("user_id", user.id).order("performed_at", { ascending: false }),
      supabase.from("missions").select("*").eq("active", true).order("created_at"),
      supabase.from("mission_completions").select("*").eq("user_id", user.id).order("completed_at", { ascending: false }),
      supabase.from("forge_credit_ledger").select("*").eq("user_id", user.id).order("created_at", { ascending: false }),
      supabase.from("xp_events").select("*").eq("user_id", user.id).order("created_at", { ascending: false })
    ]);

    setState({
      profile: profileResult.data ?? null,
      activities: activitiesResult.data ?? [],
      missions: missionsResult.data ?? [],
      completions: completionsResult.data ?? [],
      ledger: ledgerResult.data ?? [],
      xpEvents: xpResult.data ?? [],
      loading: false,
      supabaseReady: true
    });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const xpTotal = useMemo(() => state.xpEvents.reduce((sum, item) => sum + item.amount, 0), [state.xpEvents]);
  const creditBalance = useMemo(() => state.ledger.reduce((sum, item) => sum + item.amount, 0), [state.ledger]);
  const streak = useMemo(() => getCurrentStreakDays(state.activities), [state.activities]);

  return {
    ...state,
    xpTotal,
    creditBalance,
    streak,
    refresh
  };
}

function getCurrentStreakDays(activities: ActivityRow[]): number {
  const days = new Set(activities.map((item) => item.performed_at.slice(0, 10)));
  let streak = 0;
  const cursor = new Date();

  while (days.has(cursor.toISOString().slice(0, 10))) {
    streak += 1;
    cursor.setDate(cursor.getDate() - 1);
  }

  return streak;
}
