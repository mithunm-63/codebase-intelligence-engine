package com.codeintel.api.dto;

import com.codeintel.risk.RiskAnalysisService;

import java.util.List;

public record RiskAnalysisResponse(
        String projectId,
        int totalClasses,
        int highRiskClasses,
        int mediumRiskClasses,
        int lowRiskClasses,
        int averageRiskScore,
        int circularComponents,
        List<Hotspot> hotspots) {

    public static RiskAnalysisResponse from(RiskAnalysisService.ProjectRiskReport report) {
        return new RiskAnalysisResponse(
                report.projectId(), report.totalClasses(), report.highRiskClasses(), report.mediumRiskClasses(),
                report.lowRiskClasses(), report.averageRiskScore(), report.circularComponents(),
                report.hotspots().stream().map(Hotspot::from).toList());
    }

    public record Hotspot(
            String classId,
            String name,
            String qualifiedName,
            String riskLevel,
            int riskScore,
            int lineCount,
            int methodCount,
            int fieldCount,
            int fanIn,
            int fanOut,
            int totalCyclomaticComplexity,
            int maxMethodComplexity,
            int averageMethodLines,
            List<String> riskFactors) {
        public static Hotspot from(RiskAnalysisService.Hotspot h) {
            return new Hotspot(h.classId(), h.name(), h.qualifiedName(), h.riskLevel(), h.riskScore(),
                    h.lineCount(), h.methodCount(), h.fieldCount(), h.fanIn(), h.fanOut(),
                    h.totalCyclomaticComplexity(), h.maxMethodComplexity(), h.averageMethodLines(), h.riskFactors());
        }
    }
}
