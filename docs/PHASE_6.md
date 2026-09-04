# Phase 6 — Impact Analysis and Circular Dependencies

Phase 6 turns the Neo4j dependency graph into developer-facing architectural intelligence.

## Features

- Change impact analysis for a selected class.
- Direct and transitive dependent discovery.
- Depth-limited breadth-first blast-radius traversal.
- Explainable impact-risk score from graph evidence.
- Strongly connected component detection using Tarjan's algorithm.
- Circular dependency reporting at the class level.
- Circular dependency cycles surfaced in the React dashboard.
- Public REST endpoints for impact and cycle analysis.

## API

`GET /api/projects/{projectId}/analysis/impact/{classId}`

`GET /api/projects/{projectId}/analysis/cycles`

## Safety limits

Impact depth is capped at 12, returned affected classes at 200, and graph edges read for analysis at 20,000 for the public demo. These limits are application-level safeguards and can be adjusted through code/configuration later.

## Example

For `PaymentService`:

`PaymentService -> OrderService -> InvoiceService -> PaymentService`

The impact endpoint reports direct dependents, the transitive blast radius, maximum graph depth, risk level, risk factors, and cycles involving the target class.
