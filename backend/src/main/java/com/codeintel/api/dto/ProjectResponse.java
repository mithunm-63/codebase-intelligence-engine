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
        Instant createdAt,
        Instant updatedAt,
        String errorMessage
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.getId(), p.getName(), p.getSourceType(), p.getSourceUrl(), p.getStatus(),
                p.getRepositorySizeBytes(), p.getTotalFiles(), p.getJavaFiles(),
                p.getMainJavaFiles(), p.getTestJavaFiles(), p.getCreatedAt(), p.getUpdatedAt(), p.getErrorMessage()
        );
    }
}
