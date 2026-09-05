# Phase 11 — Grounded Natural-Language Codebase Assistant

## Goal
Allow developers to ask questions such as:

- Why is `PaymentService` risky?
- What breaks if I modify `PaymentService`?
- Where is the strongest coupling?

The language model is an explanation layer only. The authoritative evidence comes from the project's static-analysis data already stored in PostgreSQL and exposed by the analysis engine.

## Backend

Endpoint:

`POST /api/projects/{projectId}/analysis/ask`

Request:

```json
{"question":"Why is PaymentService risky?"}
```

Response contains:

- project id
- configured Gemini model
- grounded natural-language answer
- named evidence items returned by the codebase search layer

The assistant supplies a bounded context containing relevant search results, a class catalog, dependency edges, and, when a class name is present in the question, its calculated risk and impact metrics.

## Gemini configuration

The backend reads these environment variables:

- `GEMINI_API_KEY` — required to enable the assistant
- `GEMINI_MODEL` — optional; defaults to `gemini-3.8-flash`
- `GEMINI_API_ENDPOINT` — optional; defaults to `https://generativelanguage.googleapis.com/v1beta/models`

Gemini's `generateContent` REST API remains supported, although Google's current Gemini documentation recommends the newer Interactions API for new applications. The implementation intentionally keeps the provider adapter isolated so it can be migrated later without changing the codebase-analysis layer.

Never put the API key in the React frontend or in source control.

## Safety and cost controls

- Questions are limited to 600 characters.
- Context sent to the model is bounded to 28,000 characters.
- Only a bounded number of classes and dependency edges are included.
- Each project is limited to 20 assistant requests per hour per backend instance.
- Provider failures are translated into explicit API errors rather than silently producing an answer.
- The request contains only the analyzed project context; the model is not treated as the source of truth.

## Architecture principle

```text
User question
     ↓
Codebase Search + existing analysis metrics
     ↓
Grounded evidence context
     ↓
Gemini explanation
     ↓
Answer + evidence references
```

The model must not be treated as the source of truth for the dependency graph.
