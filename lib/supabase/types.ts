import type { Discipline } from "@/lib/scoring";

export type Profile = {
  id: string;
  display_name: string;
  created_at: string;
  updated_at: string;
};

export type ActivityRow = {
  id: string;
  user_id: string;
  discipline: Discipline;
  distance_meters: number;
  duration_seconds: number;
  performed_at: string;
  notes: string | null;
  created_at: string;
};

export type MissionRow = {
  id: string;
  slug: string;
  title: string;
  description: string;
  kind: "single_activity" | "weekly_streak" | "discipline_balance";
  xp_reward: number;
  credit_reward: number;
  active: boolean;
  created_at: string;
};

export type MissionCompletionRow = {
  id: string;
  user_id: string;
  mission_id: string;
  completed_at: string;
};

export type XpEventRow = {
  id: string;
  user_id: string;
  activity_id: string | null;
  mission_id: string | null;
  amount: number;
  reason: string;
  created_at: string;
};

export type ForgeCreditLedgerRow = {
  id: string;
  user_id: string;
  activity_id: string | null;
  mission_id: string | null;
  amount: number;
  reason: string;
  created_at: string;
};

export type Database = {
  public: {
    Tables: {
      profiles: {
        Row: Profile;
        Insert: Partial<Profile> & { id: string };
        Update: Partial<Profile>;
      };
      activities: {
        Row: ActivityRow;
        Insert: Omit<ActivityRow, "id" | "created_at" | "performed_at"> & { performed_at?: string };
        Update: Partial<ActivityRow>;
      };
      missions: {
        Row: MissionRow;
        Insert: Omit<MissionRow, "id" | "created_at"> & { id?: string };
        Update: Partial<MissionRow>;
      };
      mission_completions: {
        Row: MissionCompletionRow;
        Insert: Omit<MissionCompletionRow, "id" | "completed_at"> & { id?: string; completed_at?: string };
        Update: Partial<MissionCompletionRow>;
      };
      xp_events: {
        Row: XpEventRow;
        Insert: Omit<XpEventRow, "id" | "created_at"> & { id?: string; created_at?: string };
        Update: Partial<XpEventRow>;
      };
      forge_credit_ledger: {
        Row: ForgeCreditLedgerRow;
        Insert: Omit<ForgeCreditLedgerRow, "id" | "created_at"> & { id?: string; created_at?: string };
        Update: Partial<ForgeCreditLedgerRow>;
      };
    };
  };
};
