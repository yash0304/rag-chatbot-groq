.PHONY: dev backend frontend test lint up down seed

up:            ## Full stack via Docker
	docker compose up --build

down:
	docker compose down

backend:       ## Run API locally (SQLite + stub AI, no services needed)
	cd backend && uvicorn app.main:app --reload

frontend:      ## Run web app locally
	cd frontend && npm run dev

test:          ## All tests
	cd backend && python -m pytest -q
	cd frontend && npm run test && npm run typecheck

lint:
	cd backend && ruff check app tests
	cd frontend && npm run lint
