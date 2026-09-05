# Project State

## Completed

- Phase 1: deployable foundation
- Phase 2: repository ingestion and Java-file discovery
- Phase 2 fix: explicit Jackson databind dependency for GitHub API JSON
- Phase 3: Java AST and symbol extraction
- Phase 3 fixes: corrected JavaParser modifier import, added CodeImport persistence, fixed `Lob`, and aligned checked exception handling
- Phase 4: symbol resolution + dependency analysis
- Phase 4 fixes: aligned ingestion tests, repaired PostgreSQL status/text compatibility, and switched to the Spring Boot JDBC starter
- Phase 5: Neo4j architecture graph projection and graph visualization API/UI
- Phase 6: circular dependency detection, graph traversal, dependency paths, and change-impact analysis
- Phase 7: deterministic code hotspot and risk analysis
- Phase 8: architecture rules and architecture drift detection
- Phase 9: execution path analysis from API/controller entry points toward repository/DAO layers
- Phase 10: indexed codebase search and class navigation
- Phase 11: grounded natural-language codebase assistant using Gemini as the explanation layer
- Phase 12: Git/GitHub history intelligence
- Phase 13: persistent and incremental Git/GitHub history synchronization
- Phase 14: historical risk intelligence combining current structural risk with Git change pressure

## Analysis capabilities

- AST and symbol extraction
- Resolved dependency graph
- Circular dependency detection using strongly connected components
- Bounded graph traversal and execution-path discovery
- Evidence-backed change-impact analysis
- AST-derived cyclomatic complexity
- Fan-in/fan-out and explainable risk scoring
- Architecture-layer rules and drift violations
- Indexed search across classes, methods, endpoints, packages, and dependencies
- Grounded natural-language explanations using project analysis evidence
- GitHub commit/file history, change hotspots, authors, activity, and direct commit links
- Persistent incremental Git history synchronization
- Historical risk scoring from static risk + repository change pressure
- Recent-vs-older change trend classification

## Public API highlights

- `GET /api/projects/{projectId}/analysis/ast`
- `GET /api/projects/{projectId}/analysis/classes`
- `GET /api/projects/{projectId}/analysis/classes/{classId}`
- `GET /api/projects/{projectId}/analysis/dependencies`
- `GET /api/projects/{projectId}/analysis/classes/{classId}/dependencies`
- `GET /api/projects/{projectId}/analysis/classes/{classId}/dependents`
- `POST /api/projects/{projectId}/analysis/graph/sync`
- `GET /api/projects/{projectId}/analysis/graph?view=class|package`
- `GET /api/projects/{projectId}/analysis/cycles`
- `GET /api/projects/{projectId}/analysis/risks`
- `GET /api/projects/{projectId}/analysis/impact/{classId}`
- `GET /api/projects/{projectId}/analysis/execution-paths?maxPaths=25`
- `GET /api/projects/{projectId}/analysis/search?q=PaymentService&type=ALL&limit=30`
- `POST /api/projects/{projectId}/analysis/ask`
- `GET /api/projects/{projectId}/history?commits=25`
- `POST /api/projects/{projectId}/history/sync?commits=25`
- `GET /api/projects/{projectId}/history/persistent?commits=25`
- `GET /api/projects/{projectId}/analysis/historical-risk`

## Deployment

The application remains deployable through the same GitHub → Vercel/Render workflow. The production-style free-tier deployment uses managed PostgreSQL, Neo4j, and Redis where configured. Backend secrets remain server-side.

## Phase 14

Historical risk combines the deterministic static-analysis score with persisted Git change pressure. The dashboard ranks classes by the combined score and shows commit frequency, churn, and recent-vs-older trend so developers can prioritize high-risk areas that are also actively changing.
