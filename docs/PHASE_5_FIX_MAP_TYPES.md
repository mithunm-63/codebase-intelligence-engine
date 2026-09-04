# Phase 5 Fix — Graph Map Generic Types

Fixed `ArchitectureGraphService` so the Neo4j parameter payloads are explicitly constructed as `Map<String, Object>` for class and package nodes.

This avoids Java generic invariance errors such as:

`List<Map<String, String>> cannot be converted to List<Map<String, Object>>`

No API contract changes are required.
