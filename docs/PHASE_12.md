# Phase 12 — Git/GitHub History Intelligence

Phase 12 connects repository history with the existing static codebase model.

## Delivered

- GitHub commit history for the analyzed repository
- Recent commit list with author, date, additions, deletions, and changed-file count
- File change hotspots ranked by line churn
- Contributor count
- Total additions, deletions, and churn across the sampled history
- Recent daily activity
- Direct links to GitHub commits
- Short in-memory cache to avoid repeated GitHub calls
- GitHub API access through the existing `GITHUB_TOKEN` backend configuration when available

## API

`GET /api/projects/{projectId}/history?commits=25`

The endpoint currently samples 5–40 recent commits. History is computed from the project's stored GitHub source URL and is intentionally not persisted yet; Phase 13 will build the incremental/persistent history layer.

## UI

The dashboard now includes a **Repository history** panel after the codebase assistant. It surfaces change hotspots, recent commits, and recent activity without adding another navigation surface.

## Interpretation

A history hotspot means a file has accumulated high line churn within the sampled commits. It is a signal for review priority, not proof that the file contains bugs.
