# Project State

## Current milestone
Milestone 2 — Repository ingestion + file discovery

## Completed
- Milestone 1 deployment-ready foundation
- Spring Boot REST API with health endpoint
- React/Vite deployment-ready frontend
- PostgreSQL, Neo4j and Redis configuration through environment variables
- Docker Compose and Render/Vercel deployment configuration
- Project persistence in PostgreSQL
- Public GitHub repository ingestion
- ZIP repository upload
- ZIP path traversal protection
- Repository upload/archive size limits
- Total-file limit
- Java-file limit
- Ignoring `.git`, `target`, `node_modules`, and `.idea` content during ingestion
- Maven-project-friendly source discovery metrics (`src/main/java`, `src/test/java`)
- Project listing and project detail APIs
- Frontend repository ingestion workflow
- Unit tests for archive safety and Java-file/root counting

## Not yet implemented
- JavaParser AST extraction
- Symbol resolution
- Dependency extraction
- Neo4j domain model and graph writes
- Impact analysis
- Cycle detection
- Hotspot/risk engine
- Architecture rules
- LLM query layer
- JWT
- Per-user quotas/rate limiting
- Background analysis jobs
- Incremental analysis

## Public demo ingestion policy
- Public GitHub repositories only in GitHub mode
- ZIP uploads accepted up to configured repository size
- Java-file and total-file limits enforced server-side
- Raw repository contents are processed in a temporary workspace and deleted after ingestion in this phase

## Git deployment flow

```text
Developer
   ↓ git push
GitHub
   ├── Vercel → frontend
   └── Render → backend
```

Use the same repository for both services.

## Next milestone
Milestone 3 — JavaParser AST + symbol extraction
