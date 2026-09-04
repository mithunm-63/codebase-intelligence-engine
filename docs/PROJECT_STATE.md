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

## Phase 5 implementation

Resolved class-to-class dependency edges are projected from PostgreSQL into Neo4j. The projection creates project, package, and code-class nodes plus `CONTAINS` and `DEPENDS_ON` relationships. Class edges preserve dependency evidence; package edges aggregate cross-package relationships.

Repository analysis now performs AST analysis, dependency analysis, and Neo4j graph synchronization before setting the project to `READY`.

The graph API supports both `class` and `package` views with safe node/edge limits. A manual graph synchronization endpoint is available for projects analyzed by earlier phases.

## Current public API

- `GET /api/projects/{projectId}/analysis/ast`
- `GET /api/projects/{projectId}/analysis/classes`
- `GET /api/projects/{projectId}/analysis/classes/{classId}`
- `GET /api/projects/{projectId}/analysis/dependencies`
- `GET /api/projects/{projectId}/analysis/classes/{classId}/dependencies`
- `GET /api/projects/{projectId}/analysis/classes/{classId}/dependents`
- `POST /api/projects/{projectId}/analysis/graph/sync`
- `GET /api/projects/{projectId}/analysis/graph?view=class|package`

## Next phase

Phase 6: graph-native traversal, circular dependency detection, dependency paths, and change-impact analysis.

## Deployment

The application remains deployable through the same GitHub → Vercel/Render workflow. Phase 5 additionally requires the existing Render Neo4j environment variables to point to a reachable Neo4j instance.


### GitHub ingestion reliability fix
The public GitHub client supports an optional `GITHUB_TOKEN` for authenticated GitHub API requests, sends the current GitHub API version header, uses the repository `zipball_url` returned by GitHub metadata when available, and returns actionable messages for 403/rate-limit/404 failures. Public repository URLs also accept `www.github.com` and strip query strings/fragments.
### Phase 5 compiler fix — graph node generic types
- Replaced `Map.of(...)` in `ArchitectureGraphService` class/package node stream mappings with explicit `LinkedHashMap<String, Object>` rows.
- This prevents Java generic inference from producing `List<Map<String, String>>` where Neo4j parameter payloads require `List<Map<String, Object>>`.

