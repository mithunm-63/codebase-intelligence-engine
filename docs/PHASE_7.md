# Phase 7 — Code Hotspots & Risk Engine

Phase 7 adds deterministic, explainable maintainability risk scoring to the Java analysis pipeline.

## Signals
- fan-in / fan-out from resolved project dependencies
- class size and method count
- average method size
- AST-derived cyclomatic complexity per method
- circular strongly connected components

## API
- `GET /api/projects/{projectId}/analysis/risks`
- `GET /api/projects/{projectId}/analysis/risks/{classId}`

The risk score is a bounded heuristic, not an ML prediction. Every hotspot includes the metrics and factors that contributed to its score.
