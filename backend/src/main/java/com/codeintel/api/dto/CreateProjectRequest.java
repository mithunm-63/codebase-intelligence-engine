package com.codeintel.api.dto;

import com.codeintel.project.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull SourceType sourceType,
        @Size(max = 500) String sourceUrl
) {}
