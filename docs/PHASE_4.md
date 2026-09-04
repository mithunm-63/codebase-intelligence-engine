# Phase 4 — Symbol Resolution + Dependency Analysis

Phase 4 converts the structural AST index from Phase 3 into a resolved, project-aware dependency model.

## What is new

The backend now:

- builds a symbol index from all project classes and interfaces
- resolves simple names using direct imports, wildcard imports, same-package lookup, and unique project symbols
- records ambiguous project references instead of guessing
- discovers relationships for imports, field types, method parameters, return types, thrown types, inheritance, implementation, annotations, method calls, and object creation
- aggregates repeated occurrences into logical source → target → relationship-type edges
- stores resolved dependency edges in PostgreSQL
- marks an analysis `READY` only after AST analysis and dependency analysis both succeed
- exposes dependency APIs for whole-project, per-class dependency, and per-class dependent queries

## Dependency model

```text
CodeClass ──(DependencyType)──> CodeClass
```

Supported relationship types:

```text
IMPORT
FIELD_TYPE
METHOD_PARAMETER
METHOD_RETURN_TYPE
THROWS_TYPE
EXTENDS
IMPLEMENTS
ANNOTATION
METHOD_CALL
OBJECT_CREATION
```

Each logical edge stores:

- source class
- target class
- relationship type
- first evidence line
- source member when available
- occurrence count
- short evidence description

## Resolution strategy

For a reference such as `PaymentService`, the analyzer attempts:

1. exact fully-qualified project symbol
2. direct imported type
3. wildcard-imported project type
4. same-package project type
5. unique project type by simple name
6. otherwise treat it as external/unresolved rather than inventing a dependency

If multiple project classes share the same simple name and the context cannot disambiguate them, the reference is recorded as ambiguous.

## Example

```java
public class PaymentService {
    private final PaymentRepository repository;

    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    public void process(UserService userService) {
        userService.load();
        repository.save();
    }
}
```

Produces edges similar to:

```text
PaymentService ──FIELD_TYPE──────> PaymentRepository
PaymentService ──METHOD_PARAMETER> PaymentRepository
PaymentService ──METHOD_PARAMETER> UserService
PaymentService ──METHOD_CALL─────> UserService
```

## API

Whole project:

```http
GET /api/projects/{projectId}/analysis/dependencies
```

Direct dependencies of a class:

```http
GET /api/projects/{projectId}/analysis/classes/{classId}/dependencies
```

Classes that depend on a class:

```http
GET /api/projects/{projectId}/analysis/classes/{classId}/dependents
```

Class detail now also includes `dependencies` and `dependents` arrays.

## Storage

Phase 4 deliberately keeps the authoritative dependency model in PostgreSQL. Neo4j is reserved for Phase 5, where these rows will be projected into the architecture graph and queried with graph-native traversals.

## Deployment

No new hosting service is required for Phase 4. The existing flow remains:

```text
GitHub
  ├── Vercel → React
  └── Render → Spring Boot
                ├── PostgreSQL
                ├── Neo4j
                └── Redis
```

The current public-demo limits remain unchanged.
