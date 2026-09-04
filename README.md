# Codebase Intelligence Engine

A deployable static-analysis platform for Java/Spring Boot repositories that maps dependencies, detects architectural risks, and explains the blast radius of code changes.

## Product goal

Think of this as a **Google Maps for a software codebase**.

The final product will answer questions such as:

- What breaks if I modify `PaymentService`?
- Which classes have the highest architectural coupling?
- Where are circular dependencies?
- Which modules are becoming difficult to maintain?
- What is the dependency path from controller → service → repository?
- Why is a particular class risky?

The core intelligence will come from our own Java static-analysis engine. An LLM will be added later as an explanation/query layer rather than the source of truth.

## Phase 1 — deployment-ready foundation

Included now:

- Spring Boot 4.1.1 / Java 21 backend
- React + Vite frontend
- PostgreSQL, Neo4j, and Redis configuration
- Docker Compose for local infrastructure
- Dockerfile for Render deployment
- `render.yaml` Blueprint for the backend
- Vercel SPA configuration
- CORS configuration driven by environment variables
- Environment-variable based production configuration
- Health endpoint for public deployment
- GitHub Actions CI for backend and frontend
- Demo-mode limits prepared for the future analyzer

## Repository layout

```text
codebase-intelligence-engine/
├── backend/
│   ├── src/main/java/com/codeintel/
│   ├── src/main/resources/application.yml
│   ├── src/test/
│   ├── Dockerfile
│   └── pom.xml
├── frontend/
│   ├── src/
│   ├── vercel.json
│   ├── .env.example
│   └── package.json
├── .github/workflows/
├── docs/
├── docker-compose.yml
├── render.yaml
├── .env.example
└── README.md
```

## Local setup

Prerequisites:

- Git
- Java 21+
- Maven 3.9+
- Node.js 22+
- npm 10+
- Docker Desktop / Docker Engine with Compose

Start local databases:

```bash
docker compose up -d
```

Start the backend:

```bash
cd backend
mvn spring-boot:run
```

Start the frontend:

```bash
cd frontend
npm install
npm run dev
```

Open:

- Frontend: `http://localhost:5173`
- Backend health: `http://localhost:8080/api/health`
- Actuator health: `http://localhost:8080/actuator/health`
- Neo4j browser: `http://localhost:7474`

## GitHub + Vercel + Render deployment

### 1. Push this repository to GitHub

From the project root:

```bash
git init
git branch -M main
git add .
git commit -m "chore: initialize deployable phase 1 foundation"
git remote add origin https://github.com/YOUR_USERNAME/codebase-intelligence-engine.git
git push -u origin main
```

### 2. Deploy frontend on Vercel

Create a Vercel project from the GitHub repository.

Because this is a monorepo, set **Root Directory** to:

```text
frontend
```

Vercel can then build the Vite app from that directory. Add this environment variable:

```text
VITE_API_BASE_URL=https://YOUR-BACKEND.onrender.com
```

Redeploy after adding the variable.

### 3. Deploy backend on Render

Render can deploy the included `render.yaml` as a Blueprint from the repository root.

The Blueprint points Render at:

```text
backend/Dockerfile
```

and uses `/api/health` as the health-check path.

Set the following secret environment variables in Render:

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

Set `APP_CORS_ALLOWED_ORIGINS` to the exact Vercel URL, for example:

```text
https://your-project.vercel.app
```

### 4. Create the cloud data services

The application expects managed services to provide connection details for:

- PostgreSQL
- Neo4j
- Redis

Recommended providers for a small portfolio demo are **Neon for PostgreSQL**, **Neo4j AuraDB Free**, and **Upstash Redis**, subject to their current free-tier availability and limits.

Convert the provider connection information to the variables expected by Spring Boot. Never commit real passwords, tokens, or private connection strings to GitHub.

### 5. Verify the live deployment

Backend:

```text
https://YOUR-BACKEND.onrender.com/api/health
```

Frontend:

```text
https://YOUR-PROJECT.vercel.app
```

On the frontend, the API status card should report that the backend is online.

## Public demo limits

The Phase 1 configuration prepares the system for a constrained public demo:

```text
DEMO_MODE=true
MAX_REPOSITORY_SIZE_MB=50
MAX_JAVA_FILES=2000
MAX_ANALYSIS_SECONDS=120
```

These are application-level limits. Later phases will enforce them inside the repository ingestion and analysis engine.

## Important deployment notes

1. **Do not expose local database ports in production.** Docker Compose is for local development only.
2. **Do not commit `.env` files or secrets.** Only `.env.example` belongs in GitHub.
3. The backend listens on the platform-provided `PORT` environment variable, so Render can route traffic correctly.
4. The `/api/health` endpoint deliberately does not require a database query, so the web service can report basic process health independently of external database availability.
5. CORS is controlled by `APP_CORS_ALLOWED_ORIGINS`; update it after the Vercel URL is known.
6. The public deployment is a demonstration environment, not an unlimited code-analysis service. Large repositories and many concurrent analyses will be handled later with queueing, resource controls, and incremental analysis.

## Future phases

1. Repository ingestion
2. Java AST + symbol extraction
3. Symbol resolution + dependency analysis
4. Neo4j architecture graph
5. Impact analysis + cycle detection
6. Risk/hotspot engine
7. Architecture rules + drift detection
8. Full REST API
9. React analysis dashboard
10. Public deployment of the first real analyzer
11. Grounded natural-language / LLM query layer
12. GitHub integration
13. Incremental analysis
14. JWT + multi-user authorization
15. Production hardening and CI/CD expansion

## License

Choose a license before publishing publicly (MIT is a common portfolio-project choice).
