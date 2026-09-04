# Project State

## Current milestone
Milestone 1 — Deployment-ready Foundation

## Completed
- Root repository structure
- Spring Boot backend skeleton
- Java 21 configuration
- PostgreSQL configuration via environment variables
- Neo4j configuration via environment variables
- Redis configuration via environment variables
- Lightweight backend health endpoint
- CORS configuration for Vercel → Render calls
- React/Vite frontend shell with backend health check
- Docker Compose infrastructure for local development
- Backend Dockerfile for Render
- Render Blueprint (`render.yaml`)
- Vercel SPA configuration
- Frontend environment configuration
- GitHub Actions CI for backend and frontend
- Demo-mode analysis limits prepared for later repository ingestion
- Deployment guide

## Not yet implemented
- Repository ingestion
- GitHub repository cloning
- ZIP upload
- JavaParser AST extraction
- Symbol resolution
- Dependency graph
- Neo4j domain model
- Impact analysis
- Cycle detection
- Hotspot/risk engine
- Architecture rules
- LLM query layer
- JWT
- Per-user quotas/rate limiting
- Background analysis jobs
- Incremental analysis

## Deployment target

```text
GitHub
  ├── Vercel → frontend
  └── Render → Spring Boot API
                ├── Neon PostgreSQL
                ├── Neo4j AuraDB
                └── Upstash Redis
```

## Phase 1 demo policy

The public deployment is intentionally a small demonstration environment. Configuration prepared in `render.yaml`:

```text
DEMO_MODE=true
MAX_REPOSITORY_SIZE_MB=50
MAX_JAVA_FILES=2000
MAX_ANALYSIS_SECONDS=120
```

The limits become enforced when repository ingestion is implemented.

## Next phase
Milestone 2 — Repository ingestion + Java AST analysis.
