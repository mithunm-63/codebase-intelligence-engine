# Codebase Intelligence Engine

> A "Google Maps for your codebase": ingest a Java/Spring Boot repository, build a structural model of it, map its dependencies, detect architectural risks, and see what a change could break.

**Live demo:** [codebase-intelligence-engine.vercel.app](https://codebase-intelligence-engine.vercel.app)

Codebase Intelligence Engine is a static-analysis platform. Its core intelligence comes from its own deterministic Java analysis engine (JavaParser AST, symbol resolution, graph analysis). An LLM is used only as a grounded explanation and Q&A layer on top of that analysis, never as the source of truth.

---

## Table of contents

- [Features](#features)
- [How it works](#how-it-works)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [API reference](#api-reference)
- [Deployment](#deployment)
- [Security](#security)
- [Testing](#testing)
- [Roadmap](#roadmap)

---

## Features

**Repository ingestion**
- Import a public GitHub repository (HTTPS URL) or upload a ZIP archive
- Enforced limits on repository size, total file count, Java file count and analysis time
- Safe ZIP extraction: path-traversal protection and an uncompressed-size cap
- Ignores `.git`, `target`, `node_modules` and `.idea`
- Reports counts for total Java files, `src/main/java` and `src/test/java`
- Source is processed in a temporary workspace that is deleted after the scan; repository code is never executed

**Static analysis**
- Full AST parsing with JavaParser
- Symbol index of packages, classes, methods and fields
- Dependency resolution against project types (not just name matching)
- Relationship types captured: `FIELD_TYPE`, `METHOD_PARAMETER`, `METHOD_RETURN_TYPE`, `EXTENDS`, `IMPLEMENTS`, `ANNOTATION`, `METHOD_CALL`, `OBJECT_CREATION`
- Per-class views of direct dependencies and incoming dependents

**Architecture graph**
- Resolved dependencies projected into **Neo4j** for graph-native traversal
- Interactive class and package graph visualization in the UI
- Manual **Sync Neo4j** action to rebuild the graph
- Graceful degradation: if Neo4j is unreachable, analysis still completes, the project is marked `READY`, and the response reports `graphStatus=UNAVAILABLE`

**Hotspot and risk analysis**
- AST-derived cyclomatic complexity
- Fan-in / fan-out coupling
- Class size and method size
- Deterministic risk ranking to surface the riskiest classes

**AI assistant (optional)**
- Natural-language Q&A over the analysis results, powered by Google Gemini
- Requires a `GEMINI_API_KEY`; everything else works without it

---

## How it works

```
GitHub URL / ZIP upload
        ↓
Repository ingestion
        ↓
Safety + size limits
        ↓
Temporary workspace
        ↓
JavaParser AST
        ↓
Symbol index
        ↓
Dependency resolution
        ↓
PostgreSQL dependency model
        ↓
Neo4j architecture graph
        ↓
Graph API + visualization  ·  Hotspot / risk analysis  ·  AI Q&A
```

PostgreSQL stores projects and the resolved dependency model. Neo4j holds the architecture graph for traversal. Redis is used for supporting infrastructure.

---

## Tech stack

| Layer | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot, Maven |
| Parsing | JavaParser |
| Relational store | PostgreSQL |
| Graph store | Neo4j |
| Cache | Redis |
| Frontend | React (Vite dev server) |
| AI layer | Google Gemini (optional) |
| Local infra | Docker Compose |
| Hosting | Vercel (frontend), Render (backend), Neon (Postgres), Neo4j AuraDB, Upstash (Redis) |
| CI | GitHub Actions |

---

## Project structure

```
codebase-intelligence-engine/
├── backend/
│   ├── src/main/java/com/codeintel/
│   │   ├── api/            # REST controllers
│   │   ├── analysis/       # Dependency, graph and risk analysis
│   │   ├── dependency/     # Dependency resolution
│   │   ├── ingestion/      # GitHub / ZIP ingestion and safety checks
│   │   ├── parser/         # JavaParser AST + symbol index
│   │   └── project/        # Project entities and persistence
│   ├── src/test/
│   ├── src/main/resources/application.yml
│   ├── Dockerfile
│   └── pom.xml
├── frontend/               # React app (src/, vercel.json, package.json)
├── docs/                   # Per-phase design notes (e.g. PHASE_5.md)
├── samples/
│   └── dependency-demo/    # Small sample project for trying the analyzer
├── .github/workflows/      # CI
├── docker-compose.yml      # Local PostgreSQL, Neo4j, Redis
├── render.yaml             # Render blueprint for the backend
├── .env.example
└── DEPLOY_NOW.md
```

---

## Getting started

### Prerequisites

- Git
- Java 21+
- Maven 3.9+
- Node.js 22+ and npm
- Docker Desktop or Docker Engine with Compose

### 1. Clone and configure

```bash
git clone https://github.com/mithunm-63/codebase-intelligence-engine.git
cd codebase-intelligence-engine
cp .env.example .env
```

The defaults in `.env.example` match the local Docker Compose setup. Add a `GITHUB_TOKEN` and/or `GEMINI_API_KEY` if you want them (see [Configuration](#configuration)).

### 2. Start the databases

```bash
docker compose up -d
```

### 3. Start the backend

```bash
cd backend
mvn spring-boot:run
```

### 4. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

### 5. Open the app

| Service | URL |
| --- | --- |
| Frontend | http://localhost:5173 |
| Backend | http://localhost:8080 |
| Health check | http://localhost:8080/api/health |

### 6. Try it

Create a project, then ingest either a public GitHub repository or a ZIP file. The `samples/dependency-demo` folder is a small project you can zip and upload for a quick first run.

---

## Configuration

All settings are environment variables (see `.env.example`).

| Variable | Default | Description |
| --- | --- | --- |
| `PORT` | `8080` | Backend port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/codeintel` | PostgreSQL JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `codeintel` / `codeintel` | PostgreSQL credentials |
| `NEO4J_URI` | `bolt://localhost:7687` | Neo4j connection URI |
| `NEO4J_USERNAME` / `NEO4J_PASSWORD` | `neo4j` / `codeintelneo4j` | Neo4j credentials |
| `REDIS_URL` | `redis://localhost:6379` | Redis connection URL |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Allowed frontend origins |
| `GITHUB_TOKEN` | *(empty)* | Optional server-side token to avoid GitHub API rate limits |
| `GEMINI_API_KEY` | *(empty)* | Required only for AI Q&A |
| `GEMINI_MODEL` | see `.env.example` | Gemini model used for the assistant |
| `GEMINI_API_ENDPOINT` | `https://generativelanguage.googleapis.com/v1beta/models` | Gemini API endpoint |
| `DEMO_MODE` | `false` | Enables the bounded public-demo behavior |
| `MAX_REPOSITORY_SIZE_MB` | `50` | Maximum repository size |
| `MAX_UPLOAD_REQUEST_SIZE_MB` | `60` | Maximum upload request size |
| `MAX_JAVA_FILES` | `2000` | Maximum number of Java files analyzed |
| `MAX_FILES` | `10000` | Maximum total files in a repository |
| `MAX_ANALYSIS_SECONDS` | `120` | Analysis time limit |

---

## API reference

Base path: `/api`

### Projects

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/projects` | Create a project |
| `GET` | `/projects` | List projects |
| `GET` | `/projects/{projectId}` | Get a project |

```json
POST /api/projects
{
  "name": "payment-platform",
  "sourceType": "GITHUB_PUBLIC",
  "sourceUrl": "https://github.com/example/payment-platform"
}
```

### Ingestion

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/projects/{projectId}/ingest/github` | Ingest a public GitHub repository |
| `POST` | `/projects/{projectId}/ingest/zip` | Ingest a ZIP (`multipart/form-data`, field name `file`) |

```json
POST /api/projects/{projectId}/ingest/github
{
  "repositoryUrl": "https://github.com/example/payment-platform"
}
```

### Dependency analysis

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/projects/{projectId}/analysis/dependencies` | Project-wide dependency model |
| `GET` | `/projects/{projectId}/analysis/classes/{classId}/dependencies` | What a class depends on |
| `GET` | `/projects/{projectId}/analysis/classes/{classId}/dependents` | What depends on a class |

The class-detail endpoint also returns direct `dependencies` and incoming `dependents`.

### Health

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/health` | Service health check |

---

## Deployment

The hosted demo runs on free-tier managed services:

```
GitHub
  ├── Vercel → React frontend
  └── Render → Spring Boot backend
                ├── Neon → PostgreSQL
                ├── Neo4j AuraDB → graph database
                └── Upstash → Redis
```

- `render.yaml` defines the backend service; `frontend/vercel.json` configures the frontend.
- Set the environment variables from the table above in Render. Set `GITHUB_TOKEN` there too: unauthenticated GitHub API limits are easy to exhaust on a hosted backend, and the token is server-side only.
- The public demo runs with `DEMO_MODE=true` and the limits listed above.
- See `DEPLOY_NOW.md` for step-by-step instructions.

Pushing to `main` triggers redeploys on the connected Vercel and Render services.

---

## Security

- Public GitHub ingestion accepts only `https://github.com/owner/repository` URLs. Private repository access is intentionally deferred until authentication, authorization, token handling, quotas and auditing are in place.
- ZIP extraction validates normalized paths (blocking path traversal) and enforces both compressed-upload and uncompressed-content limits.
- Uploaded or downloaded repository code is only parsed, never executed, and the temporary workspace is removed after the scan.
- The optional `GITHUB_TOKEN` and `GEMINI_API_KEY` stay on the server and are never sent to the frontend.

---

## Testing

```bash
cd backend
mvn test
```

The suite covers repository-ingestion safety and dependency analysis (field dependencies, inheritance, parameter resolution and method calls).

---

## Roadmap

- [x] Repository ingestion (GitHub / ZIP) with safety limits
- [x] AST parsing and symbol index
- [x] Dependency resolution and persistence
- [x] Neo4j architecture graph and visualization
- [x] Hotspot and risk analysis
- [ ] Graph traversals and impact analysis: reachability, paths, cycles, blast radius
- [ ] Change-impact intelligence
- [ ] Private repository support with authentication

---

## Contributing

Issues and pull requests are welcome. Please open an issue first for larger changes. Use conventional commit messages, for example `feat: add repository ingestion`.

## License

No license file is currently included. Add one (for example MIT) before accepting outside contributions.
