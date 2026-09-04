# Phase 10 — Codebase Search & Navigation

Phase 10 adds an indexed search layer over the analyzed project model.

## Search types

- Everything
- Classes
- Methods
- Endpoints
- Packages
- Dependencies

## API

`GET /api/projects/{projectId}/analysis/search?q={query}&type={type}&limit={limit}`

The backend searches the PostgreSQL analysis model and returns relevance-scored results with source path, class/method identity, dependency type, source line, and source member evidence where available.

## Navigation

Search results can open a class detail overlay showing source location, method signatures, and project dependencies without changing the underlying analysis state.

## Design goal

This phase turns the dashboard from a passive report into a lightweight codebase navigation surface. Later phases can connect the same search index to commit history, semantic search, architecture explanations, and grounded natural-language queries.
