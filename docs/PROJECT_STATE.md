# Project State

## Completed

- Phase 1: deployable foundation
- Phase 2: repository ingestion and Java-file discovery
- Phase 2 fix: explicit Jackson databind dependency for GitHub API JSON
- Phase 3: Java AST and symbol extraction

## Phase 3 implementation

JavaParser 3.28.0 parses Java 21 source. Parsed types, methods, constructors, and fields are persisted in PostgreSQL. The ingestion flow now performs AST analysis before temporary source cleanup.

## Next phase

Phase 4: symbol resolution + dependency analysis.

Target relationships include imports, field types, parameter/return types, inheritance/implementation, annotations, and method calls.


### Phase 3 build fix
- Corrected `NodeWithModifiers` import to `com.github.javaparser.ast.nodeTypes.NodeWithModifiers`.
- Added missing `CodeImport` entity and `CodeImportRepository`.
- Added missing `jakarta.persistence.Lob` import in `Project`.
