# MindQuest — Security Checklist & Practices

## Authentication & sessions
- [x] Passwords hashed with PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte random salt,
      constant-time comparison (`app/core/security.py`).
- [x] JWT access tokens: 30 min TTL, HS256, `sub`+`exp`+`iat`+`type` claims.
- [x] Refresh tokens: opaque 256-bit random, stored **hashed**, 30-day TTL, **rotated on
      every use**, revocable, revoked on logout. Reuse of a revoked token is rejected.
- [x] Registration enforces password policy (≥8 chars); email normalized lowercase.
- [x] Generic "invalid credentials" error (no user enumeration on login).

## Authorization & tenancy
- [x] Every query filters by `user_id` from the verified token — no object is fetchable
      across tenants (covered by tests).
- [x] Vector search always applies a `user_id` payload filter in Qdrant.
- [x] Leaderboard exposes only opt-in users and only `hero_name/level/xp`.

## Input & upload safety
- [x] Pydantic validation on all bodies; size-limited uploads (25 MB), extension+MIME
      allowlist (pdf, txt, md, png, jpg), files stored outside web root with UUID names.
- [x] SQLAlchemy parameterized queries only (no raw SQL string building).
- [x] Chat/citation output returns data, never raw HTML; frontend renders as text.

## AI-specific
- [x] Prompt-injection containment: retrieved document text is wrapped in delimited context
      blocks; system prompt instructs the model to treat it as data; citations are validated
      server-side against the actual retrieved set (fabricated `[n]` markers are stripped).
- [x] Provider keys only server-side; never sent to clients; stub provider default in dev.
- [x] Per-user AI rate limits + daily XP caps prevent farming/abuse loops.

## Transport & headers
- [x] CORS allowlist from env (`CORS_ORIGINS`), credentials mode explicit.
- [x] Security headers middleware: `X-Content-Type-Options`, `X-Frame-Options: DENY`,
      `Referrer-Policy`, minimal `Content-Security-Policy` on API responses.
- [ ] TLS/HSTS — terminate at the load balancer (deployment responsibility).

## Rate limiting & abuse
- [x] Sliding-window limiter (Redis-backed, in-memory fallback): 120 req/min global,
      20 req/min AI endpoints, 10 req/min auth endpoints per IP.

## Secrets & supply chain
- [x] No secrets in repo; `.env` gitignored; `.env.example` documents every variable.
- [x] Dependencies pinned; `pip-audit`/`npm audit` run in CI (report job).
- [x] Docker images run as non-root; minimal base images.

## Privacy
- [x] `DELETE /users/me` cascades all rows, vectors, and stored files.
- [x] Logs exclude document content and message bodies (IDs only).

## Incident response (process)
- Rotate `JWT_SECRET` (invalidates sessions) + provider keys on suspicion of leak.
- Revoke all refresh tokens: `UPDATE refresh_tokens SET revoked = true`.
