package com.codeintel.api.dto;

import java.time.Instant;

import com.codeintel.project.Project;
import com.codeintel.project.ProjectStatus;
import com.codeintel.project.SourceType;

public record ProjectResponse(
        String id,
        String name,
        SourceType sourceType,
        String sourceUrl,
        ProjectStatus status,
        Long repositorySizeBytes,
        Integer totalFiles,
        Integer javaFiles,
        Integer mainJavaFiles,
        Integer testJavaFiles,
        Integer classCount,
        Integer interfaceCount,
        Integer enumCount,
        Integer recordCount,
        Integer annotationCount,
        Integer methodCount,
        Integer constructorCount,
        Integer fieldCount,
        Integer importCount,
        Integer dependencyCount,
        Integer dependencyOccurrenceCount,
        Integer unresolvedReferenceCount,
        String unresolvedReferences,
        Integer parseErrorCount,
        String parseErrors,
        Instant astAnalyzedAt,
        Instant createdAt,
        Instant updatedAt,
        String errorMessage
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.getId(), p.getName(), p.getSourceType(), p.getSourceUrl(), p.getStatus(),
                p.getRepositorySizeBytes(), p.getTotalFiles(), p.getJavaFiles(),
                p.getMainJavaFiles(), p.getTestJavaFiles(), p.getClassCount(), p.getInterfaceCount(),
                p.getEnumCount(), p.getRecordCount(), p.getAnnotationCount(), p.getMethodCount(),
                p.getConstructorCount(), p.getFieldCount(), p.getImportCount(), p.getDependencyCount(),
                p.getDependencyOccurrenceCount(), p.getUnresolvedReferenceCount(), p.getUnresolvedReferences(),
                p.getParseErrorCount(), p.getParseErrors(), p.getAstAnalyzedAt(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getErrorMessage()
        );
    }
}
