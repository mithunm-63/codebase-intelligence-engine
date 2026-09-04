package com.codeintel.api.dto;

import com.codeintel.impact.ImpactAnalysisService;

import java.util.List;

public record CycleAnalysisResponse(
        String projectId,
        int cycleCount,
        int graphNodes,
        int graphEdges,
        List<Cycle> cycles) {

    public static CycleAnalysisResponse from(ImpactAnalysisService.CycleReport report) {
        return new CycleAnalysisResponse(
                report.projectId(),
                report.cycleCount(),
                report.graphNodes(),
                report.graphEdges(),
                report.cycles().stream()
                        .map(c -> new Cycle(c.classes(), c.severity()))
                        .toList());
    }

    public record Cycle(List<String> classes, String severity) {}
}
