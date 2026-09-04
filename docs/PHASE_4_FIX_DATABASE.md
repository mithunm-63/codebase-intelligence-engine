# Phase 4 database compatibility fix

This patch fixes two production issues discovered on Render:

1. Older PostgreSQL databases had a `projects_status_check` constraint that rejected `ANALYZED`, while the Phase 4 ingestion flow ultimately uses `READY`.
2. `parse_errors`, `unresolved_references`, and `error_message` could be limited to VARCHAR(1000) in an existing database.

The application now:
- removes `ANALYZED` from the project status enum;
- keeps the final `READY` transition in repository ingestion;
- explicitly maps large project text columns to PostgreSQL `TEXT`;
- runs a small compatibility migration at startup so an existing deployed database is repaired automatically.

No data reset is required.

### What this deployment fix does

On application startup, the compatibility runner:

1. Converts any legacy `ANALYZED` project rows to `READY`.
2. Recreates `projects_status_check` using only the current lifecycle states.
3. Converts `parse_errors` and `unresolved_references` to PostgreSQL `TEXT`.
4. Converts `error_message` to `TEXT` as a defensive compatibility measure.

This is idempotent and lets the existing Render/Neon database be reused without manually deleting data.

## Phase 4 deployment build fix — Spring JDBC dependency

The Render Docker build reported:

`'dependencies.dependency.version' for org.springframework.boot:spring-jdbc:jar is missing`

The project used the raw `spring-jdbc` artifact while relying on Spring Boot dependency management. The deployment build now uses the supported `spring-boot-starter-jdbc` starter, which brings JDBC support into the application with Spring Boot-managed dependency versions.

No manual version should be added for `spring-jdbc`.
