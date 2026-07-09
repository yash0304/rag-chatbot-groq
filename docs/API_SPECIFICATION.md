# MindQuest — API Specification (v1)

Base URL: `/api/v1`. JSON everywhere except file upload (multipart). Auth: `Authorization: Bearer <access_token>`.
Live OpenAPI: `GET /docs` (Swagger UI) / `GET /openapi.json`.

## Conventions
- IDs are UUID strings. Timestamps are ISO-8601 UTC.
- Errors: `{"detail": "message"}` with proper status (400 validation, 401 auth, 403 forbidden, 404 missing, 409 conflict, 429 rate-limited).
- Pagination: `?limit=` (default 50, max 200) `&offset=`.
- Rate limits: global 120 req/min/user; AI endpoints 20 req/min/user (429 + `Retry-After`).

## Auth
| Method | Path | Body → Response |
|---|---|---|
| POST | /auth/register | `{email, password, display_name}` → `201 UserOut` |
| POST | /auth/login | `{email, password}` → `{access_token, refresh_token, token_type}` |
| POST | /auth/refresh | `{refresh_token}` → new token pair (rotation; old refresh revoked) |
| POST | /auth/logout | `{refresh_token}` → `204` (revokes) |

## Users
| GET | /users/me | → `UserOut` (id, email, display_name, hero_name, plan, xp, level, skill_points, leaderboard_opt_in) |
| PATCH | /users/me | `{display_name?, hero_name?, leaderboard_opt_in?}` → `UserOut` |
| DELETE | /users/me | `204` — cascades DB rows and vectors |

## Documents (Second Brain)
| POST | /documents | multipart `file` (+ optional `title`) → `202 DocumentOut(status=processing)`; pipeline runs async |
| GET | /documents | list `DocumentOut[]` (`?status=&domain=&q=`) |
| GET | /documents/{id} | `DocumentDetail` (adds summary, tags, chunk_count, ocr_used) |
| GET | /documents/{id}/chunks | chunk texts + locations |
| DELETE | /documents/{id} | `204` — removes vectors too |

`DocumentOut`: `{id, title, filename, mime_type, status, domain, summary, tags[], chunk_count, ocr_used, created_at}`

## Search & Chat
| POST | /search | `{query, limit?=8}` → `{results: [{chunk_id, document_id, title, snippet, location, score}]}` |
| POST | /chat/sessions | `{title?}` → `ChatSessionOut` |
| GET | /chat/sessions | list |
| GET | /chat/sessions/{id}/messages | history with citations |
| POST | /chat/sessions/{id}/messages | `{content}` → `{message: {role:"assistant", content, citations: [{index, document_id, chunk_id, title, snippet, location}]}}` |

## Quests
| POST | /quests | `{title, description?, difficulty, goal_id?, due_at?}` → `QuestOut` (xp_reward derived: trivial 10 / easy 25 / normal 50 / hard 100 / epic 250) |
| GET | /quests | `?status=` |
| POST | /quests/generate | `{goal_id?, count?=3}` → AI-drafted `QuestOut[]` with `status=draft` |
| POST | /quests/{id}/accept | draft → active |
| POST | /quests/{id}/complete | → `{quest, xp_awarded, level_up: bool, achievements_unlocked[]}` |
| PATCH | /quests/{id} | edit; DELETE abandons |

## Habits (daily missions)
| POST | /habits | `{title, cadence}` → `HabitOut` |
| GET | /habits | includes `streak, best_streak, checked_in_today` |
| POST | /habits/{id}/checkin | → `{habit, xp_awarded, multiplier, achievements_unlocked[]}` (409 if already today) |
| DELETE | /habits/{id} | `204` |

## Goals (story arcs)
| POST | /goals | `{title, narrative?, milestones: [title,...]}` → `GoalOut` (narrator assigns `arc_theme`) |
| GET | /goals · GET /goals/{id} | with milestone progress |
| POST | /goals/{id}/milestones/{mid}/complete | → XP + arc progression; completing last milestone completes the goal (bonus XP) |

## Gamification
| GET | /gamification/profile | `{xp, level, xp_for_next_level, progress_pct, skill_points, streak_summary}` |
| GET | /gamification/xp-events | ledger, paginated |
| GET | /gamification/achievements | full catalog + `unlocked_at` per user |
| GET | /gamification/skills | trees with owned/available/locked state |
| POST | /gamification/skills/{code}/unlock | spends points (409 if unaffordable/parent missing) |
| GET | /gamification/collectibles | owned collectibles with lore |
| GET | /leaderboard | opt-in users: `{hero_name, level, xp}` top 100 (404 if disabled by env) |

## Knowledge graph & analytics
| GET | /graph | `{nodes: [{id, label, type: domain|document|tag, size}], edges: [{source, target, weight}]}` |
| GET | /analytics/summary | totals: documents, quests_completed, habits_active, current_streak_max, xp_7d, level |
| GET | /analytics/xp-daily?days=30 | `[{date, xp}]` |
| GET | /analytics/activity-heatmap?weeks=12 | `[{date, count}]` |

## Weekly review
| POST | /reviews/weekly | generates (or returns existing) review for current week → `{week_start, stats, narrative, suggestions[]}` |
| GET | /reviews | past reviews |

## Health
`GET /healthz` (liveness) · `GET /readyz` (DB/vector/AI reachability report).
