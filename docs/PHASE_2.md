# Phase 2 — Repository Ingestion + File Discovery

## Goal
Turn a public GitHub repository or ZIP upload into a validated, bounded Java repository input for the static-analysis engine.

## Supported inputs
- Public GitHub HTTPS repository URL
- ZIP archive upload

Private GitHub repositories are intentionally excluded from the public demo until authentication and secret handling are implemented.

## API

### Create project
`POST /api/projects`

```json
{
  "name": "payment-platform",
  "sourceType": "GITHUB_PUBLIC",
  "sourceUrl": "https://github.com/example/payment-platform"
}
```

### List projects
`GET /api/projects`

### Read project
`GET /api/projects/{projectId}`

### Ingest GitHub
`POST /api/projects/{projectId}/ingest/github`

```json
{
  "repositoryUrl": "https://github.com/example/payment-platform"
}
```

### Ingest ZIP
`POST /api/projects/{projectId}/ingest/zip`

Multipart field:

```text
file
```

## Processing flow

```text
GitHub / ZIP
     ↓
Validate source
     ↓
Download / receive archive
     ↓
Check compressed-size limit
     ↓
Safely extract to temporary workspace
     ↓
Ignore build/IDE metadata
     ↓
Count files + Java source roots
     ↓
Persist project metadata
     ↓
Delete temporary workspace
```

## Safety boundaries
The ingestion layer enforces repository size, total-file, and Java-file limits. ZIP paths are normalized and checked to prevent traversal outside the workspace. An uncompressed-size limit also protects against decompression bombs. GitHub mode uses only the public GitHub API and codeload archive endpoints; it does not execute code from the repository.

## Phase 2 output
The ingestion response contains:
- project id
- READY/FAILED status
- repository size in bytes
- total files
- Java file count
- `src/main/java` Java count
- `src/test/java` Java count
- sample discovered paths

## Design decision
The source files are not stored permanently yet. This reduces privacy and storage concerns for the public demo. Phase 3 will consume the repository as part of a real analysis run, and a later phase can add object storage if persistent re-analysis is required.


### Build dependency fix
The backend explicitly declares `com.fasterxml.jackson.core:jackson-databind` because `GitHubRepositoryClient` parses GitHub API JSON with `ObjectMapper` and `JsonNode`.
