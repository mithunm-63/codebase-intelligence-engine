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

## Architecture graph

Resolved class-to-class dependency edges are projected from PostgreSQL into Neo4j. The projection creates project, package, and code-class nodes plus `CONTAINS` and `DEPENDS_ON` relationships. Class edges preserve dependency evidence; package edges aggregate cross-package relationships.

Repository analysis performs AST analysis, dependency analysis, and Neo4j graph synchronization before setting the project to `READY` when graph synchronization is available.

The graph API supports both `class` and `package` views with safe node/edge limits. A manual graph synchronization endpoint is available for projects analyzed by earlier phases.

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

## Deployment

The application remains deployable through the same GitHub → Vercel/Render workflow. The production-style free-tier deployment uses managed PostgreSQL, Neo4j, and Redis where configured. Backend secrets remain server-side.

### GitHub ingestion reliability
The public GitHub client supports an optional `GITHUB_TOKEN` for authenticated GitHub API requests, sends the GitHub API version header, uses the repository `zipball_url` returned by GitHub metadata when available, and returns actionable messages for 403/rate-limit/404 failures. Public repository URLs accept `www.github.com` and strip query strings/fragments.

### Neo4j availability behavior
Repository AST and dependency analysis can complete even when Neo4j is temporarily unavailable. In that case the project is still marked `READY`, the response reports `graphStatus=UNAVAILABLE`, and the UI shows the Neo4j configuration error. Once Neo4j is reachable, use **Sync Neo4j** to rebuild the graph.

### Phase 5 compiler fix — graph node generic types
- Replaced `Map.of(...)` in `ArchitectureGraphService` class/package node stream mappings with explicit `LinkedHashMap<String, Object>` rows.
- This prevents Java generic inference from producing `List<Map<String, String>>` where Neo4j parameter payloads require `List<Map<String, Object>>`.

### Neo4j sync regression fix
- Fixed package-to-class containment sync to `UNWIND` each class row and use its explicit `packageId` parameter.
- Prevents Neo4j `Expected $packageId, but got $projectId` errors during automatic graph synchronization.

## Phase 7
Added deterministic code hotspot and risk analysis with AST-derived cyclomatic complexity, fan-in/fan-out, class size, method size, and risk ranking APIs.

## Phase 8
Added package-derived architecture rules, persisted project rules, drift detection, compliance scoring, and dashboard controls for adding and removing rules.

## Phase 9
Added bounded execution-path discovery from Spring MVC API/controller entry points toward repository/DAO classes using the resolved dependency graph.

## Phase 10
Added indexed search and class-detail navigation across the persisted analysis model.

## Phase 11
Added a grounded Gemini explanation layer. The model receives bounded analysis evidence and is not treated as the source of truth for dependencies or risk.

## Phase 12
Added sampled GitHub commit history, file-level churn hotspots, contributor count, recent activity, and direct commit links.

## Phase 13
Added persistent Git history records and incremental synchronization state. New syncs import only commits newer than the stored head, while the dashboard can display the persisted history without re-fetching GitHub.
