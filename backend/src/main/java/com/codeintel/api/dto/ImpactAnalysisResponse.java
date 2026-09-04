package com.codeintel.api.dto;

import com.codeintel.impact.ImpactAnalysisService;

import java.util.List;

public record ImpactAnalysisResponse(
        String projectId,
        String targetClassId,
        String targetClassName,
        String targetQualifiedName,
        String riskLevel,
        int riskScore,
        int directDependents,
        int transitiveAffectedClasses,
        int maxImpactDepth,
        int graphNodes,
        int graphEdges,
        List<String> riskFactors,
        List<AffectedClass> affectedClasses,
        List<List<String>> cyclesInvolvingTarget) {

    public static ImpactAnalysisResponse from(ImpactAnalysisService.ProjectImpactReport report) {
        return new ImpactAnalysisResponse(
                report.projectId(),
                report.targetClassId(),
                report.targetClassName(),
                report.targetQualifiedName(),
                report.riskLevel(),
                report.riskScore(),
                report.directDependents(),
                report.transitiveAffectedClasses(),
                report.maxImpactDepth(),
                report.graphNodes(),
                report.graphEdges(),
                report.riskFactors(),
                report.affectedClasses().stream()
                        .map(a -> new AffectedClass(a.classId(), a.name(), a.qualifiedName(), a.depth(), a.impactPath()))
                        .toList(),
                report.cyclesInvolvingTarget());
    }

    public record AffectedClass(
            String classId,
            String name,
            String qualifiedName,
            int depth,
            List<String> impactPath) {}
}
