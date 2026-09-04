package com.codeintel.api.dto;

import com.codeintel.analysis.CodeDependency;
import com.codeintel.dependency.DependencyType;

import java.util.List;
import java.util.Map;

public record DependencyAnalysisResponse(
        String projectId,
        int dependencyCount,
        int dependencyOccurrenceCount,
        int unresolvedReferenceCount,
        Map<String, Integer> relationshipTypes,
        List<DependencyEdge> edges,
        List<String> unresolvedReferences
) {
    public record DependencyEdge(
            long id,
            long sourceClassId,
            String sourceClass,
            long targetClassId,
            String targetClass,
            DependencyType type,
            int sourceLine,
            String sourceMember,
            int occurrenceCount,
            String evidence
    ) {
        public static DependencyEdge from(CodeDependency dependency) {
            return new DependencyEdge(
                    dependency.getId(),
                    dependency.getSourceClass().getId(),
                    dependency.getSourceClass().getQualifiedName(),
                    dependency.getTargetClass().getId(),
                    dependency.getTargetClass().getQualifiedName(),
                    dependency.getType(),
                    dependency.getSourceLine(),
                    dependency.getSourceMember(),
                    dependency.getOccurrenceCount(),
                    dependency.getEvidence());
        }
    }
}
