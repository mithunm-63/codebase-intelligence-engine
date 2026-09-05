# Phase 15 — Revision-Aware Incremental Code Analysis

## Goal
Avoid unnecessary repository analysis when the GitHub revision has not changed, and preserve the existing Java intelligence when only non-Java files changed.

## Delivered

- Persistent code-analysis revision state per project
- Comparison between the last analyzed commit and the current GitHub head
- Automatic synchronization with the persistent Git history layer
- No-op path when repository head is unchanged
- No Java rebuild when only non-Java files changed
- Changed-file reporting for recent commits
- Safe full-reanalysis fallback when Java source changes are detected
- Safe full-reanalysis fallback when the previous analyzed revision is outside the retained history window
- Dashboard action: **Check for changes**

## API

`POST /api/projects/{projectId}/analysis/incremental/refresh`

Response statuses include:

- `BASELINE_INITIALIZED` — stores the current revision as the analysis baseline
- `NO_CHANGES` — current head equals the analyzed revision
- `NO_JAVA_CHANGES` — repository changed, but no Java source file changed
- `JAVA_CHANGES_REANALYZED` — Java changes were detected and the current safe full-analysis pipeline was executed
- `FULL_REBUILD_REQUIRED` — the prior analysis revision is outside the retained history window, so a full analysis was executed

## Current safety model

The existing AST and dependency persistence pipeline rebuilds the complete symbol/dependency model from a complete source tree. Phase 15 therefore does not attempt unsafe file-level mutation of that model. Java changes take the safe full-reanalysis path, while unchanged and non-Java-only revisions avoid rebuilding the Java intelligence entirely.

This creates the revision-aware foundation for a later file-scoped analyzer without compromising correctness.

## Flow

```text
Current GitHub head
        ↓
Persistent history sync
        ↓
compare with last analyzed commit
        ↓
 ┌───────────────┬───────────────────┬──────────────────────┐
 │ no change     │ non-Java changes  │ Java changes         │
 ↓               ↓                   ↓
NO_CHANGES       NO_JAVA_CHANGES     safe full re-analysis
                                      ↓
                               update analyzed revision
```

## Database

`code_analysis_state` stores the last commit revision whose source was successfully considered current for the project.

```text
Project
  1 ─── 1 CodeAnalysisState
```

The table is created/updated automatically through the existing JPA schema strategy.
