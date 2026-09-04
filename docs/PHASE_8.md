# Phase 8 — Architecture Rules & Drift

Phase 8 adds explicit architecture constraints on top of the existing Java dependency graph.

## API

`GET /api/projects/{projectId}/architecture-rules`

Returns configured rules and the current compliance report.

`POST /api/projects/{projectId}/architecture-rules`

Body:

```json
{
  "sourceLayer": "API",
  "targetLayer": "REPOSITORY",
  "allowed": false,
  "severity": "HIGH",
  "description": "Controllers must depend on services, not repositories directly."
}
```

`DELETE /api/projects/{projectId}/architecture-rules/{ruleId}`

`GET /api/projects/{projectId}/architecture-rules/analysis`

## Layer inference

The analyzer derives layers from Java package names: `API` for `api`/`controller`, `SERVICE` for `service`, `REPOSITORY` for `repository`/`dao`, `CONFIG` for `config`, `MODEL` for `model`/`entity`, and `OTHER` otherwise.

## Compliance

Every resolved dependency crossing two different inferred layers is checked against configured rules. Violations include source/target classes, relationship type, source line, severity, and rule description. Compliance starts at 100 and decreases in proportion to violating dependency edges.
