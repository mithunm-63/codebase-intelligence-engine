# Phase 1 Real-World Deployment Checklist

## Target setup

```text
GitHub
  ├── Vercel → React frontend
  └── Render → Spring Boot backend (Docker)
                ├── Neon → PostgreSQL
                ├── Neo4j AuraDB → Graph database
                └── Upstash → Redis
```

The exact free-tier limits of third-party providers can change. Verify the provider dashboard before committing to a long-lived deployment.

## 1. GitHub

Create an empty public repository, then from the project root:

```bash
git init
git branch -M main
git add .
git commit -m "chore: initialize deployable phase 1 foundation"
git remote add origin https://github.com/YOUR_USERNAME/codebase-intelligence-engine.git
git push -u origin main
```

Never commit `.env`, database passwords, API keys, or GitHub tokens.

## 2. PostgreSQL

Create a PostgreSQL database with a provider such as Neon.

Collect:

```text
host
port
database
username
password
```

Build a JDBC URL:

```text
jdbc:postgresql://HOST:PORT/DATABASE?sslmode=require
```

Render environment variables:

```text
DB_URL=jdbc:postgresql://...
DB_USERNAME=...
DB_PASSWORD=...
```

## 3. Neo4j

Create an AuraDB instance. Copy its connection URI and credentials.

Render variables:

```text
NEO4J_URI=neo4j+s://...
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=...
```

## 4. Redis

Create an Upstash Redis database and copy its connection URL.

Use the provider's TLS URL when supplied:

```text
REDIS_URL=rediss://...
```

## 5. Render backend

Use **New → Blueprint** and connect the GitHub repository. Render reads the root `render.yaml` file.

The Blueprint configures:

```text
Runtime: Docker
Dockerfile: backend/Dockerfile
Health check: /api/health
Port: 10000
Demo mode: enabled
```

Provide these secret values in Render:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
NEO4J_URI
NEO4J_USERNAME
NEO4J_PASSWORD
REDIS_URL
APP_CORS_ALLOWED_ORIGINS
```

Set the CORS value temporarily to the Vercel URL after the frontend project exists.

## 6. Vercel frontend

Create a Vercel project from the same GitHub repository.

For this monorepo set:

```text
Root Directory: frontend
```

Add:

```text
VITE_API_BASE_URL=https://YOUR-BACKEND.onrender.com
```

Deploy.

## 7. Finish the CORS connection

Copy the Vercel production URL into Render:

```text
APP_CORS_ALLOWED_ORIGINS=https://YOUR-PROJECT.vercel.app
```

Redeploy the backend.

## 8. Verify

Backend:

```text
https://YOUR-BACKEND.onrender.com/api/health
```

Expected response contains:

```json
{
  "status": "UP"
}
```

Frontend:

```text
https://YOUR-PROJECT.vercel.app
```

The dashboard should show `Backend online`.

## 9. What is intentionally not solved in Phase 1

These are later production features:

- GitHub OAuth / private repository access
- repository upload and cloning
- job queue / workers
- persistent source-code storage
- rate limiting
- JWT authentication
- per-user quotas
- audit logs
- Sentry / centralized error monitoring
- custom domain
- automated database migrations
- incremental analysis

For the public Phase 1 demo, no private repository access is exposed and the analyzer itself does not yet exist.

## Phase 4 public-demo settings

The backend accepts these environment variables:

```text
MAX_REPOSITORY_SIZE_MB=50
MAX_UPLOAD_REQUEST_SIZE_MB=60
MAX_JAVA_FILES=2000
MAX_FILES=10000
```

For Phase 4 the public GitHub mode is intentionally limited to public HTTPS GitHub repositories. Private repository access will be added only after authentication and GitHub OAuth/token handling are implemented.
