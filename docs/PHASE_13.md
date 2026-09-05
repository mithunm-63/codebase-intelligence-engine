# Phase 13 — Persistent & Incremental Git History

## Goal
Turn the Phase 12 sampled Git history view into a persistent repository-history layer that can be refreshed incrementally without re-importing the same commits.

## Delivered

- Persistent commit records in PostgreSQL
- Persistent file-change records for each imported commit
- Per-project sync state with latest known commit SHA and last-sync timestamp
- Incremental synchronization that stops when the previously known head is reached
- Bounded first-pass/import window of up to 40 new commits per sync
- GitHub API authentication through the existing `GITHUB_TOKEN` configuration when available
- Existing history UI updated with a **Sync GitHub** action
- Sync status showing new commits imported and total commits stored
- Existing ZIP-upload flow remains supported with a temporary, non-persistent history view

## Database model

```text
Project
  1 ─── 1 GitHistoryState
  1 ─── N GitCommitRecord
              1 ─── N GitFileChangeRecord
```

The tables are created/updated by the existing JPA `ddl-auto: update` deployment configuration.

## API

### Incremental sync

`POST /api/projects/{projectId}/history/sync?commits=25`

Imports only commits not already stored for the project, up to 40 new commits in one sync operation.

### Persistent history

`GET /api/projects/{projectId}/history/persistent?commits=25`

Reads the stored history without contacting GitHub.

### Existing history endpoint

`GET /api/projects/{projectId}/history?commits=25`

Remains available for compatibility with earlier clients.

## Incremental strategy

```text
GitHub latest commits
        ↓
compare with stored latest SHA
        ↓
new commits only
        ↓
fetch commit details
        ↓
store commit + changed files
        ↓
update project sync state
```

On later syncs, already-imported commits are not inserted again.

## Limits

- 5–40 commits are shown in the history window.
- At most 40 new commits are imported per synchronization request.
- Commit details are fetched only for commits that are new to the persistent store.

## Interpretation

Persistent history enables future phases to combine static risk with historical change frequency, trend risk over time, identify repeatedly modified classes, and correlate architecture changes with commits.
