package com.codeintel.api.dto;

import com.codeintel.project.ProjectStatus;

import java.util.List;

public record IngestionResponse(
        String projectId,
        ProjectStatus status,
        long repositorySizeBytes,
        int totalFiles,
        int javaFiles,
        int mainJavaFiles,
        int testJavaFiles,
        List<String> sampleFiles,
        int classCount,
        int interfaceCount,
        int enumCount,
        int recordCount,
        int annotationCount,
        int methodCount,
        int constructorCount,
        int fieldCount,
        int importCount,
        int dependencyCount,
        int dependencyOccurrenceCount,
        int unresolvedReferenceCount,
        int parseErrorCount,
        List<String> parseErrors,
        List<String> discoveredTypes,
        List<Long> classIds,
        String graphStatus,
        String graphError
) {}
