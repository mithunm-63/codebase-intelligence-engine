# Phase 14 — Historical Risk Intelligence

Phase 14 combines the current static-analysis risk model with persisted Git history.

## Goal
Identify code that is not merely structurally risky, but also repeatedly changed. This creates a practical review-priority signal from two independent dimensions: current code risk and historical change pressure.

## Delivered

- Historical risk API for analyzed projects
- Current static risk blended with Git change pressure
- Per-class commit count, additions, deletions, and churn
- Recent-vs-older change-pressure comparison
- Rising / stable / cooling change trend
- CRITICAL / HIGH / MEDIUM / LOW historical priority
- Bounded top-priority dashboard view
- Separate UI card integrated below Repository history

## Score model

```text
current structural risk ──┐
                          ├──> historical risk score
Git change pressure ──────┘
```

The current implementation weights the static risk score at 65% and historical change pressure at 35%. Change pressure is derived from commit frequency and line churn in the persisted Git history window.

## API

`GET /api/projects/{projectId}/analysis/historical-risk`

The response includes project-level summary metrics and up to 20 highest-priority matched Java classes.

## Trend

The stored commit window is split into recent and older halves. A materially higher recent pressure is labelled `RISING`; materially lower pressure is `COOLING`; otherwise the class is `STABLE`.

## Interpretation

Historical risk is a prioritization signal, not proof of defects. A class becomes more interesting when it is both structurally risky and frequently changed. A class with high change pressure but low structural risk should be reviewed for churn or evolving requirements rather than assumed to be faulty.
