# Phase 5 — Neo4j Architecture Graph

## Goal

Project the resolved PostgreSQL dependency model into Neo4j and expose a graph API for class-level and package-level architecture exploration.

## Data model

Neo4j nodes:

- `Project`: one per analyzed project
- `Package`: logical Java package
- `CodeClass`: class/interface/enum/record/annotation type

Neo4j relationships:

- `Project-[:CONTAINS]->CodeClass`
- `Package-[:CONTAINS]->CodeClass`
- `CodeClass-[:DEPENDS_ON]->CodeClass`
- `Package-[:DEPENDS_ON]->Package`

Class dependency relationships retain:

- dependency type
- source line
- source member
- occurrence count
- evidence

Package edges aggregate dependency counts and dependency types between different packages.

## Synchronization

After AST and dependency analysis complete, `RepositoryIngestionService` synchronizes the project's relational model into Neo4j before the project is marked `READY`.

A manual repair/synchronization endpoint is also available:

```http
POST /api/projects/{projectId}/analysis/graph/sync
```

The operation deletes the existing Neo4j subgraph for the project and recreates it from PostgreSQL, making it deterministic and safe to repeat after a failed/partial deployment.

## Graph API

```http
GET /api/projects/{projectId}/analysis/graph?view=class&nodeLimit=300&edgeLimit=1200
```

or:

```http
GET /api/projects/{projectId}/analysis/graph?view=package&nodeLimit=300&edgeLimit=1200
```

The response contains nodes and edges ready for the React graph renderer.

## Frontend

The dashboard now has an Architecture Graph section with:

- class/package view toggle
- Neo4j synchronization button
- node/edge counts
- click-to-highlight relationships
- selected-node details
- zoom/reset controls

The browser intentionally requests a smaller graph (`120` nodes / `500` edges) for the public demo so an analyzed repository does not overload the browser.

## Why Neo4j is the source for the graph view

PostgreSQL remains the system of record for application metadata and the normalized dependency model. Neo4j is a projection optimized for traversals and architecture visualization. Later phases will use the same graph for cycles, shortest dependency paths, blast-radius traversal, centrality, and architecture-risk queries.

## Deployment

No new cloud service is required beyond the existing Neo4j endpoint. Set the existing Render environment variables:

```text
NEO4J_URI
NEO4J_USERNAME
NEO4J_PASSWORD
```

Push to GitHub and the connected Render/Vercel deployments rebuild automatically.
