# Dependency Sentinel — Phase 1

Dependency Sentinel is a Java developer-security product that turns a Maven `pom.xml` into a clean, searchable dependency inventory. Phase 1 intentionally focuses on onboarding, Maven parsing, direct dependencies, persistence, and scan history.

## Phase 1 flow

```text
Create project → Upload pom.xml → Parse Maven dependencies → Store scan → Review dependency inventory
```

## Stack

- Backend: Java 21 + Spring Boot + Spring Data JPA
- Database: PostgreSQL
- Maven parser: Maven Model
- Frontend: React + Vite
- Local orchestration: Docker Compose

## Run locally with Docker

From `dependency-sentinel/`:

```bash
docker compose up --build
```

Then open `http://localhost:5173` after starting the frontend separately.

## Run backend directly

```bash
cd backend
mvn spring-boot:run
```

Backend: `http://localhost:8081`
Health: `http://localhost:8081/api/health`

## Run frontend

```bash
cd frontend
npm install
npm run dev
```

Set `VITE_API_URL` when the backend is not on the local default:

```text
VITE_API_URL=https://your-api.example.com/api
```

## API

```http
POST /api/projects
Content-Type: application/json

{"name":"My Banking API"}
```

```http
POST /api/projects/{id}/scan
Content-Type: multipart/form-data

file=<pom.xml>
```

```http
GET /api/projects
GET /api/projects/{id}
GET /api/projects/{id}/dependencies
GET /api/projects/{id}/scans
```

## Deployment architecture

```text
GitHub repository
   ├── Vercel → frontend/ (Vite build)
   ├── Render → backend/ (Dockerfile)
   └── PostgreSQL → Render PostgreSQL or Neon
```

For a monorepo deployment, set each service's root directory to its own folder. The backend expects these environment variables:

```text
DATABASE_URL=jdbc:postgresql://HOST:5432/DB
DATABASE_USERNAME=...
DATABASE_PASSWORD=...
FRONTEND_ORIGIN=https://your-frontend-domain.vercel.app
PORT=8081
```

The backend does not execute uploaded project code. Phase 1 accepts only a file named `pom.xml` and a small upload size. Vulnerability intelligence, transitive dependencies, Gradle, licenses, recommendations, and CI/CD integrations belong to later phases.
