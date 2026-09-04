package com.codeintel.dependency;

import java.util.List;
import java.util.Map;

public record DependencyAnalysisResult(
        int dependencyOccurrences,
        int resolvedDependencyCount,
        int unresolvedReferenceCount,
        Map<DependencyType, Integer> dependencyTypeCounts,
        List<ParsedDependency> dependencies,
        List<String> unresolvedReferences
) {}
