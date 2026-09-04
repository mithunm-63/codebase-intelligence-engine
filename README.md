# Codebase Intelligence Engine

A deployable static-analysis platform for Java/Spring Boot repositories. Think of it as a **Google Maps for a codebase**: ingest a repository, build a structural model, map dependencies, detect architectural risks, and explain the blast radius of a change.

The core intelligence will come from our own Java analysis engine. An LLM will be added later as a grounded explanation/query layer rather than the source of truth.

## Current milestone — Phase 4

Phase 4 adds **project-aware symbol resolution and class-to-class dependency analysis** on top of the repository ingestion and AST foundation.

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
JavaParser AST
     ↓
Symbol Index
     ↓
Dependency Resolution
     ↓
PostgreSQL Dependency Model
     ↓
Next: Neo4j Architecture Graph
```

Phase 4 resolves project types instead of treating every name as a dependency. It records relationship types such as `FIELD_TYPE`, `METHOD_PARAMETER`, `METHOD_RETURN_TYPE`, `EXTENDS`, `IMPLEMENTS`, `ANNOTATION`, `METHOD_CALL`, and `OBJECT_CREATION`.

## Repository layout

```text
codebase-intelligence-engine/
├── backend/
│   ├── src/main/java/com/codeintel/
│   │   ├── api/
│   │   ├── analysis/
│   │   ├── dependency/
│   │   ├── ingestion/
│   │   ├── parser/
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
├── samples/
│   └── dependency-demo/
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

Dependency analysis:

```http
GET /api/projects/{projectId}/analysis/dependencies
GET /api/projects/{projectId}/analysis/classes/{classId}/dependencies
GET /api/projects/{projectId}/analysis/classes/{classId}/dependents
```

The class-detail endpoint also returns direct `dependencies` and incoming `dependents`.

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

The public demo keeps the complete platform dependencies configurable. Phase 4 persists the resolved dependency model in PostgreSQL; Neo4j remains ready for the Phase 5 graph projection.

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

The suite includes repository-ingestion safety tests plus a Phase 4 dependency-analysis test covering field dependencies, inheritance, parameter resolution, and method calls.

## Phase 4 — Symbol Resolution + Dependency Analysis

Repositories now move through: AST → symbol index → dependency resolution. The UI displays resolved dependency counts, direct dependencies, and incoming dependents for each class.

See `docs/PHASE_4.md` for details.

## Next milestone

**Phase 5 — Neo4j Architecture Graph + Graph Traversals**

```text
PostgreSQL dependency edges
           ↓
        Neo4j graph
           ↓
Graph traversals / cycles / paths
           ↓
Architecture intelligence
```
