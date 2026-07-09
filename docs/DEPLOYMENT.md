# MindQuest — Deployment Guide

## Local development (full stack)

```bash
cp .env.example .env          # fill in AI keys, or leave AI_PROVIDER=stub for offline mode
docker compose up --build
# web:      http://localhost:3000
# api:      http://localhost:8000  (Swagger: /docs)
# qdrant:   http://localhost:6333
```

Without Docker:

```bash
# backend
cd backend && python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt
uvicorn app.main:app --reload            # SQLite + stub AI + memory vectors by default

# frontend
cd frontend && npm install && npm run dev
```

## Environments

| Env | DB | Vectors | AI | Notes |
|---|---|---|---|---|
| test/CI | SQLite | in-memory | stub | fully offline |
| dev | Postgres (compose) | Qdrant (compose) | stub or real | |
| staging/prod | managed Postgres | managed/self-hosted Qdrant | OpenAI or Groq | Redis required |

## Production topology

- **Images:** `backend/Dockerfile` (uvicorn, non-root user), `frontend/Dockerfile`
  (Next.js standalone output, non-root). Push to your registry from CI on tags.
- **Runtime:** any container platform (ECS/Cloud Run/Fly/k8s). API is stateless —
  scale horizontally. Set `WEB_CONCURRENCY` for uvicorn workers.
- **Migrations:** `alembic upgrade head` as a release step before rollout.
- **Ingress:** TLS terminates at the LB; HSTS on; frontend proxies `/api` to backend
  (see `next.config.mjs` rewrite) or use a shared domain with path routing.
- **Files:** mount object storage (S3/GCS) at `STORAGE_DIR` or swap the storage helper.

## Required production env vars

```
DATABASE_URL=postgresql+psycopg://user:pass@host:5432/mindquest
QDRANT_URL=http://qdrant:6333
REDIS_URL=redis://redis:6379/0
AI_PROVIDER=openai            # or groq
OPENAI_API_KEY=...            # if openai
GROQ_API_KEY=...              # if groq
JWT_SECRET=<64+ random chars>
CORS_ORIGINS=https://app.yourdomain.com
ENVIRONMENT=production
```

## Release pipeline (CI/CD)

1. PR → lint + tests + build (see TESTING_STRATEGY.md).
2. Merge to `main` → build & push Docker images tagged with git SHA.
3. Deploy staging automatically; run smoke test (`/healthz`, `/readyz`, register+chat probe).
4. Manual promotion to prod; migrations run first; instant rollback = previous image tag.

## Backups & DR
- Postgres: automated daily snapshots + WAL/PITR. Restore drill quarterly.
- Qdrant: nightly collection snapshots to object storage. Vectors are **recomputable**
  from `document_chunks` (see `workers/ingestion.py:reindex_user`) — worst case re-embed.
- Raw files: object-storage versioning.

## Monitoring
- `/healthz` liveness, `/readyz` dependency checks (DB, vectors, AI config).
- JSON logs to stdout → your log stack. Track: request latency, 5xx rate, ingestion
  failures (`documents.status=failed`), AI provider error rate, token spend/user.
