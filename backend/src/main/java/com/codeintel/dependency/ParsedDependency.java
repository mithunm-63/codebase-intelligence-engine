package com.codeintel.dependency;

public record ParsedDependency(
        String sourceQualifiedName,
        String targetQualifiedName,
        DependencyType type,
        int line,
        String sourceMember,
        String evidence,
        int occurrenceCount
) {}
