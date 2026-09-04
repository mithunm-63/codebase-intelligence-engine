package com.codeintel.api.dto;

import java.util.List;

import com.codeintel.project.ProjectStatus;

public record IngestionResponse(
        String projectId,
        ProjectStatus status,
        long repositorySizeBytes,
        int totalFiles,
        int javaFiles,
        int mainJavaFiles,
        int testJavaFiles,
        List<String> sampleFiles
) {}
