# Deploy Now — Phase 1

## Push to GitHub

```bash
git init
git branch -M main
git add .
git commit -m "chore: initialize deployable phase 1 foundation"
git remote add origin https://github.com/YOUR_USERNAME/codebase-intelligence-engine.git
git push -u origin main
```

## Vercel

- Import the GitHub repo.
- Root Directory: `frontend`
- Add `VITE_API_BASE_URL=https://YOUR-BACKEND.onrender.com`
- Deploy.

## Render

- New → Blueprint.
- Select this GitHub repo.
- Render reads `render.yaml`.
- Add the PostgreSQL, Neo4j, Redis and CORS environment variables listed in `docs/DEPLOYMENT.md`.
- Deploy.

## Cloud data services

- PostgreSQL: Neon
- Graph: Neo4j AuraDB
- Redis: Upstash

## Final connection

Put the Vercel URL into Render:

```text
APP_CORS_ALLOWED_ORIGINS=https://YOUR-PROJECT.vercel.app
```

Then verify:

```text
https://YOUR-BACKEND.onrender.com/api/health
https://YOUR-PROJECT.vercel.app
```
