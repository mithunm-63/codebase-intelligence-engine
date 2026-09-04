# Phase 9 — Request Execution Path Analysis

The engine now exposes an evidence-backed approximation of application request flow using the resolved project dependency graph.

## What it detects

- API entry-point classes such as `*Controller`, `api` packages, and Spring controller annotations.
- HTTP handler methods carrying `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, or `@RequestMapping`.
- Dependency paths from API/controller classes toward repository/DAO classes.
- The classes, layers, dependency relationships, source lines, and members involved in every discovered path.

## Endpoint

`GET /api/projects/{projectId}/analysis/execution-paths?maxPaths=25`

`maxPaths` is bounded to 1–100 and graph traversal depth is bounded to 10 hops to keep public deployments predictable.

## Example

```text
PaymentController → PaymentService → PaymentRepository
```

The response includes both a human-readable flow and structured nodes/edges so the frontend can render a flow diagram and link each step back to the analyzed source.

This is intentionally graph-based static analysis, not runtime tracing. Runtime tracing can be added later without changing the graph contract.
