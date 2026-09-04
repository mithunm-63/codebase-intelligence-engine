# Project State

## Completed

- Phase 1: deployable foundation
- Phase 2: repository ingestion and Java-file discovery
- Phase 2 fix: explicit Jackson databind dependency for GitHub API JSON
- Phase 3: Java AST and symbol extraction
- Phase 3 fixes: corrected JavaParser modifier import, added CodeImport persistence, fixed `Lob`, and aligned checked exception handling
- Phase 4: symbol resolution + dependency analysis

## Phase 4 implementation

JavaParser source is parsed a second time into a project-aware dependency model. The analyzer resolves project types using imports, package context, and unique symbol names. It records resolved edges for imports, field types, method parameters and returns, thrown types, inheritance, implementations, annotations, method calls, and object creation. Repeated references are aggregated into one logical edge with an occurrence count. Ambiguous project references are counted and sampled rather than guessed.

Resolved dependency edges are persisted in PostgreSQL. `ProjectStatus.READY` is now assigned after both AST and dependency analysis complete successfully.

## Current public API

- `GET /api/projects/{projectId}/analysis/ast`
- `GET /api/projects/{projectId}/analysis/classes`
- `GET /api/projects/{projectId}/analysis/classes/{classId}`
- `GET /api/projects/{projectId}/analysis/dependencies`
- `GET /api/projects/{projectId}/analysis/classes/{classId}/dependencies`
- `GET /api/projects/{projectId}/analysis/classes/{classId}/dependents`

## Next phase

Phase 5: project dependencies projected into Neo4j as the architecture graph, followed by graph-native traversal queries and graph visualization data.
