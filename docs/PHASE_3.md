# Phase 3 — Java AST & Symbol Extraction

Phase 3 is the first static-analysis milestone. After a repository is ingested, the backend parses every discovered `.java` source using JavaParser 3.28.0 with Java 21 language level.

## What is extracted

- packages and source paths
- classes, interfaces, enums, records, annotation declarations
- nested type names
- modifiers and annotations
- fields with declared types and source line
- methods and constructors
- method return types
- method signatures and parameters
- thrown exception types
- start/end source lines and line counts

## Persistence

The extracted model is persisted to PostgreSQL in:

- `code_classes`
- `code_methods`
- `code_fields`

The source files themselves remain temporary and are deleted after ingestion. This keeps the demo deployment from permanently storing repository source code while still preserving structural analysis.

## APIs

`GET /api/projects/{projectId}/analysis/ast`

Returns project-level AST statistics plus a sample of discovered types.

`GET /api/projects/{projectId}/analysis/classes`

Returns the indexed type list.

`GET /api/projects/{projectId}/analysis/classes/{classId}`

Returns fields and methods for one indexed type.

## Error handling

A Java file that cannot be parsed is recorded as a parse error instead of failing the whole repository analysis. Valid files continue to be indexed.

## Why this matters

The next phase can now operate on structured source symbols rather than raw text. Phase 4 will add symbol resolution and dependency extraction such as `PaymentService -> PaymentRepository` and method-call relationships.
