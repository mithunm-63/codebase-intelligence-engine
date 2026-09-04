# Codebase Intelligence Engine

A deployable static-analysis platform for Java/Spring Boot repositories. Think of it as a **Google Maps for a codebase**: ingest a repository, build a structural model, map dependencies, detect architectural risks, and explain the blast radius of a change.

The core intelligence will come from our own Java analysis engine. An LLM will be added later as a grounded explanation/query layer rather than the source of truth.

## Current milestone — Phase 2

Phase 2 adds **real repository ingestion and bounded source discovery** on top of the Phase 1 deployment foundation.

Supported inputs:

- Public GitHub HTTPS repository URL
- ZIP upload

The ingestion layer currently:

- creates a project record in PostgreSQL
- downloads public GitHub repositories through the GitHub API + codeload archive
- accepts ZIP uploads
- enforces repository-size, total-file and Java-file limits
- rejects unsafe ZIP paths
- applies an uncompressed-size limit while extracting archives
- ignores `.git`, `target`, `node_modules`, and `.idea`
- counts total Java files, `src/main/java` files, and `src/test/java` files
- returns a small sample of discovered repository paths
- removes the temporary source workspace after the scan

## Architecture

```text
GitHub / ZIP
     ↓
Repository Ingestion
     ↓
Safety + Size Limits
     ↓
Temporary Workspace
     ↓
File Discovery
     ↓
Project Metadata (PostgreSQL)
     ↓
Next: JavaParser AST + Symbols
```

## Repository layout

```text
codebase-intelligence-engine/
├── backend/
│   ├── src/main/java/com/codeintel/
│   │   ├── api/
│   │   ├── ingestion/
│   │   └── project/
│   ├── src/test/
│   ├── src/main/resources/application.yml
│   ├── Dockerfile
│   └── pom.xml
├── frontend/
│   ├── src/
│   ├── vercel.json
│   └── package.json
├── .github/workflows/
├── docs/
├── docker-compose.yml
├── render.yaml
└── README.md
```

## API

Create a project:

```http
POST /api/projects
Content-Type: application/json
```

```json
{
  "name": "payment-platform",
  "sourceType": "GITHUB_PUBLIC",
  "sourceUrl": "https://github.com/example/payment-platform"
}
```

List projects:

```http
GET /api/projects
```

Get a project:

```http
GET /api/projects/{projectId}
```

Ingest a public GitHub repository:

```http
POST /api/projects/{projectId}/ingest/github
Content-Type: application/json
```

```json
{
  "repositoryUrl": "https://github.com/example/payment-platform"
}
```

Ingest a ZIP:

```http
POST /api/projects/{projectId}/ingest/zip
Content-Type: multipart/form-data
```

Multipart field:

```text
file
```

## Local development

Prerequisites:

- Git
- Java 21+
- Maven 3.9+
- Node.js 22+
- npm
- Docker Desktop / Docker Engine with Compose

Start databases:

```bash
docker compose up -d
```

Start backend:

```bash
cd backend
mvn spring-boot:run
```

Start frontend:

```bash
cd frontend
npm install
npm run dev
```

Open:

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
Health:   http://localhost:8080/api/health
```

## Public deployment

```text
GitHub
  ├── Vercel → React frontend
  └── Render → Spring Boot backend
                ├── Neon → PostgreSQL
                ├── Neo4j AuraDB → graph database
                └── Upstash → Redis
```

Phase 2 still keeps the complete platform dependencies configurable, even though ingestion itself only persists project metadata in PostgreSQL and uses temporary filesystem storage.

The public demo is intentionally bounded:

```text
DEMO_MODE=true
MAX_REPOSITORY_SIZE_MB=50
MAX_UPLOAD_REQUEST_SIZE_MB=60
MAX_JAVA_FILES=2000
MAX_FILES=10000
```

## Git workflow

```bash
git add .
git commit -m "feat: add repository ingestion"
git push
```

The connected Vercel and Render services can then deploy the new commit.

## Security

The public GitHub ingestion mode supports only public `https://github.com/owner/repository` URLs. Private repository access is deferred until authentication, authorization, token handling, quotas, and auditing are implemented.

The ZIP extractor validates normalized paths and applies both compressed-upload and uncompressed-content limits. Repository code is never executed during ingestion.

## Testing

Run backend tests with:

```bash
cd backend
mvn test
```

The Phase 2 suite includes archive safety and source-root/file-count tests.

## Next milestone

**Phase 3 — JavaParser AST + symbol extraction**

```text
Java file
   ↓
JavaParser
   ↓
AST
   ↓
Classes
Methods
Fields
Annotations
Imports
Types
```
