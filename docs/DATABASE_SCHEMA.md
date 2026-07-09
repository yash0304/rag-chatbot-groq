# MindQuest — Database Schema

PostgreSQL 15+ (SQLAlchemy 2.0 models in `backend/app/models/`). UUID primary keys via a
portable GUID type (native UUID on Postgres, CHAR(36) on SQLite for tests). All tables have
`created_at`/`updated_at` timestamps. All user-owned tables cascade on user deletion.

## ER overview

```
users ──< documents ──< document_chunks
  │           └──< document_tags >── tags
  ├──< chat_sessions ──< chat_messages
  ├──< quests
  ├──< habits ──< habit_checkins
  ├──< goals ──< goal_milestones
  ├──< xp_events
  ├──< user_achievements >── achievements (catalog)
  ├──< user_skills       >── skills (catalog, tree-structured)
  ├──< user_collectibles >── collectibles (catalog)
  └──< weekly_reviews
```

## Tables

### users
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| email | citext/varchar UNIQUE NOT NULL | login identity |
| password_hash | varchar NOT NULL | PBKDF2-SHA256, 600k iterations |
| display_name | varchar NOT NULL | |
| hero_name | varchar | RPG alias (leaderboard identity) |
| plan | varchar DEFAULT 'free' | free / adventurer / guildmaster |
| xp | bigint DEFAULT 0 | denormalized total (SoT = xp_events) |
| level | int DEFAULT 1 | derived, cached |
| skill_points | int DEFAULT 0 | unspent points |
| leaderboard_opt_in | bool DEFAULT false | FR-14 |
| is_active | bool DEFAULT true | |

### documents
| column | type | notes |
|---|---|---|
| id | uuid PK · user_id FK | |
| title, filename, mime_type | varchar | |
| status | varchar | processing / ready / failed |
| error | text NULL | failure reason |
| summary | text | AI-generated |
| domain | varchar | knowledge-map region (AI-assigned) |
| ocr_used | bool | FR-3 audit |
| char_count, chunk_count | int | |
| storage_path | varchar | raw file location |

### document_chunks
`id uuid PK, document_id FK, user_id FK (denorm for search), seq int, text text,
location varchar` (e.g. "p. 3" / "§2") — embedding lives in Qdrant under the same `id`.
Index: `(document_id, seq)`.

### tags / document_tags
`tags(id, user_id, name UNIQUE per user)`; join table `document_tags(document_id, tag_id)`.
Tags power the knowledge graph edges.

### chat_sessions / chat_messages
`chat_sessions(id, user_id, title)`;
`chat_messages(id, session_id, role user|assistant, content text, citations jsonb)` —
citations: `[{index, document_id, chunk_id, title, snippet, location}]`.

### quests
| column | type | notes |
|---|---|---|
| id uuid PK · user_id FK · goal_id FK NULL | | quest may belong to a story arc |
| title, description | | |
| difficulty | varchar | trivial/easy/normal/hard/epic |
| xp_reward | int | derived from difficulty at creation |
| status | varchar | draft / active / completed / abandoned |
| source | varchar | manual / ai |
| due_at, completed_at | timestamptz NULL | |

### habits / habit_checkins
`habits(id, user_id, title, cadence daily|weekdays|weekly, streak int, best_streak int,
last_checkin_date date NULL, xp_base int)`;
`habit_checkins(id, habit_id, user_id, date date, xp_awarded int)` —
**UNIQUE (habit_id, date)** enforces one check-in/day.

### goals / goal_milestones
`goals(id, user_id, title, narrative text, status active|completed|archived, arc_theme varchar)`;
`goal_milestones(id, goal_id, seq, title, completed bool, completed_at)`.

### xp_events (append-only ledger — source of truth for all progression)
| column | type | notes |
|---|---|---|
| id uuid PK · user_id FK | | |
| kind | varchar | document_uploaded, document_processed, quest_completed, habit_checkin, milestone_completed, goal_completed, knowledge_consulted, achievement_bonus, weekly_review |
| amount | int | XP granted |
| ref_id | uuid NULL | the causing entity |
| meta | jsonb | e.g. `{streak: 5, multiplier: 1.25}` |
Index `(user_id, created_at)`; UNIQUE `(user_id, kind, ref_id, day)` for daily-capped kinds
(enforced in service layer for portability).

### achievements / user_achievements
Catalog `achievements(id, code UNIQUE, name, description, icon, xp_bonus, secret bool)` seeded
at startup; `user_achievements(user_id, achievement_id, unlocked_at)` UNIQUE pair.

### skills / user_skills
Catalog `skills(id, code UNIQUE, tree scholar|explorer|strategist|forger, tier int, name,
description, cost int, parent_code varchar NULL)`;
`user_skills(user_id, skill_id, unlocked_at)` UNIQUE pair. Parent must be owned before child.

### collectibles / user_collectibles
Catalog of original-lore items (`code, name, rarity common|rare|epic|legendary, lore text`);
join table with `acquired_at` and `source` (achievement / quest / narrator).

### weekly_reviews
`(id, user_id, week_start date, stats jsonb, narrative text, suggestions jsonb)`
UNIQUE `(user_id, week_start)`.

### refresh_tokens
`(id, user_id, token_hash UNIQUE, expires_at, revoked bool)` — rotation on every refresh.

## Vector store (Qdrant)

Collection `mindquest_chunks` — size = provider embedding dim, cosine distance.
Point id = `document_chunks.id`; payload `{user_id, document_id, chunk_id, seq}`;
payload index on `user_id`. All searches filter by `user_id` (hard tenant isolation).

## Migrations

Alembic configured in `backend/alembic/`; models are the schema source —
`alembic revision --autogenerate` per change, `alembic upgrade head` on deploy.
Dev/tests may use `Base.metadata.create_all` (same metadata).
