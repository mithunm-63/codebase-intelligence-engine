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
- configured model
- grounded natural-language answer
- named evidence items returned by the codebase search layer

The assistant supplies a bounded context containing relevant search results, a class catalog, dependency edges, and, when a class name is present in the question, its calculated risk and impact metrics.

## Provider configuration

The backend reads these environment variables:

- `OPENAI_API_KEY` — required to enable the assistant
- `OPENAI_MODEL` — optional; defaults to `gpt-5.6-luna`
- `OPENAI_RESPONSES_URL` — optional; defaults to `https://api.openai.com/v1/responses`

Never put the API key in the React frontend or in source control.

## Safety and cost controls

- Questions are limited to 600 characters.
- Context sent to the model is bounded to 28,000 characters.
- Only a bounded number of classes and dependency edges are included.
- Each project is limited to 20 assistant requests per hour per backend instance.
- Provider failures are translated into explicit API errors rather than silently producing an answer.
- Responses are requested with `store: false`.

## Architecture principle

```text
User question
     ↓
Codebase Search + existing analysis metrics
     ↓
Grounded evidence context
     ↓
LLM explanation
     ↓
Answer + evidence references
```

The model must not be treated as the source of truth for the dependency graph.
