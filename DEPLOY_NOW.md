# Deploy Now — Phase 2

Phase 2 uses the same cloud deployment shape as Phase 1, but the frontend can now ingest public GitHub repositories and ZIPs.

## 1. Push to GitHub

New repository:

```bash
git init
git branch -M main
git add .
git commit -m "feat: add repository ingestion"
git remote add origin https://github.com/YOUR_USERNAME/codebase-intelligence-engine.git
git push -u origin main
```

Existing repository:

```bash
git add .
git commit -m "feat: add repository ingestion"
git push
```

## 2. Render backend

Connect the repository with **New → Blueprint**. Render reads `render.yaml`.

Required secret variables:

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

Demo limits:

```text
DEMO_MODE=true
MAX_REPOSITORY_SIZE_MB=50
MAX_UPLOAD_REQUEST_SIZE_MB=60
MAX_JAVA_FILES=2000
MAX_FILES=10000
MAX_ANALYSIS_SECONDS=120
```

## 3. Vercel frontend

Import the same GitHub repository.

Set:

```text
Root Directory: frontend
```

Set the environment variable:

```text
VITE_API_BASE_URL=https://YOUR-BACKEND.onrender.com
```

Deploy.

## 4. CORS

After Vercel gives you the production URL, set this on Render:

```text
APP_CORS_ALLOWED_ORIGINS=https://YOUR-PROJECT.vercel.app
```

Redeploy the backend.

## 5. Verify

Backend:

```text
https://YOUR-BACKEND.onrender.com/api/health
```

Frontend:

```text
https://YOUR-PROJECT.vercel.app
```

Then use the Repository ingestion form with a small public GitHub Java/Spring Boot repository.

## Important

Phase 2 supports only public GitHub repositories. Private repositories are intentionally not supported yet. Never put GitHub tokens, database passwords, Redis credentials, or Neo4j passwords into GitHub.
