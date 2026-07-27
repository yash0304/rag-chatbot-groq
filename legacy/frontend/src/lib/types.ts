export interface User {
  id: string;
  email: string;
  display_name: string;
  hero_name: string | null;
  plan: string;
  xp: number;
  level: number;
  skill_points: number;
  leaderboard_opt_in: boolean;
}

export interface Doc {
  id: string;
  title: string;
  filename: string;
  status: "processing" | "ready" | "failed";
  error: string | null;
  summary: string | null;
  domain: string | null;
  ocr_used: boolean;
  chunk_count: number;
  created_at: string;
  tags: { id: string; name: string }[];
}

export interface Citation {
  index: number;
  document_id: string;
  chunk_id: string;
  title: string;
  snippet: string;
  location: string | null;
}

export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  citations: Citation[];
  created_at: string;
}

export interface Quest {
  id: string;
  goal_id: string | null;
  title: string;
  description: string | null;
  difficulty: "trivial" | "easy" | "normal" | "hard" | "epic";
  xp_reward: number;
  status: "draft" | "active" | "completed" | "abandoned";
  source: "manual" | "ai";
  due_at: string | null;
  completed_at: string | null;
}

export interface Habit {
  id: string;
  title: string;
  cadence: string;
  streak: number;
  best_streak: number;
  checked_in_today: boolean;
  xp_base: number;
}

export interface Milestone {
  id: string;
  seq: number;
  title: string;
  completed: boolean;
}

export interface Goal {
  id: string;
  title: string;
  narrative: string | null;
  arc_theme: string | null;
  status: string;
  milestones: Milestone[];
}

export interface Profile {
  xp: number;
  level: number;
  xp_for_current_level: number;
  xp_for_next_level: number;
  progress_pct: number;
  skill_points: number;
  current_streak_max: number;
}

export interface Achievement {
  code: string;
  name: string;
  description: string;
  icon: string;
  xp_bonus: number;
  unlocked_at: string | null;
}

export interface Skill {
  code: string;
  tree: string;
  tier: number;
  name: string;
  description: string;
  cost: number;
  parent_code: string | null;
  owned: boolean;
  available: boolean;
}

export interface Collectible {
  code: string;
  name: string;
  rarity: string;
  lore: string;
  source: string;
  acquired_at: string;
}

export interface GraphNode {
  id: string;
  label: string;
  type: "domain" | "document" | "tag";
  size: number;
}

export interface GraphEdge {
  source: string;
  target: string;
  weight: number;
}

export interface WeeklyReview {
  id: string;
  week_start: string;
  stats: Record<string, unknown>;
  narrative: string;
  suggestions: string[];
}

export interface Summary {
  documents: number;
  quests_completed: number;
  quests_active: number;
  habits_active: number;
  current_streak_max: number;
  xp_7d: number;
  xp_total: number;
  level: number;
}
