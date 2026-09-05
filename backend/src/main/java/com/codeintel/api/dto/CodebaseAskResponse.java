package com.codeintel.api.dto;

import com.codeintel.ai.CodebaseAskService;

import java.util.List;

public record CodebaseAskResponse(
        String projectId,
        String model,
        String answer,
        List<String> evidence) {

    public static CodebaseAskResponse from(CodebaseAskService.AskReport report) {
        return new CodebaseAskResponse(report.projectId(), report.model(), report.answer(), report.evidence());
    }
}
