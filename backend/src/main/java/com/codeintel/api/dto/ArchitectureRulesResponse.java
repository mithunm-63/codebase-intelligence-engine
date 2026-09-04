package com.codeintel.api.dto;

import com.codeintel.architecture.ArchitectureRule;
import com.codeintel.architecture.ArchitectureRuleService;
import java.util.List;

public record ArchitectureRulesResponse(
        String projectId,
        int ruleCount,
        int dependencyCount,
        int violationCount,
        int complianceScore,
        int highSeverityViolations,
        int classCount,
        List<Rule> rules,
        List<Violation> violations) {

    public static ArchitectureRulesResponse from(String projectId, List<ArchitectureRule> rules,
                                                  ArchitectureRuleService.ArchitectureReport report) {
        return new ArchitectureRulesResponse(projectId, report.ruleCount(), report.dependencyCount(),
                report.violationCount(), report.complianceScore(), report.highSeverityViolations(),
                report.classCount(), rules.stream().map(Rule::from).toList(),
                report.violations().stream().map(Violation::from).toList());
    }

    public record Rule(Long id, String sourceLayer, String targetLayer, boolean allowed,
                       String severity, String description) {
        static Rule from(ArchitectureRule r) {
            return new Rule(r.getId(), r.getSourceLayer(), r.getTargetLayer(), r.isAllowed(), r.getSeverity(), r.getDescription());
        }
    }

    public record Violation(String dependencyId, String sourceLayer, String targetLayer, String sourceClass,
                            String targetClass, String relationshipType, int sourceLine, String severity,
                            String ruleDescription) {
        static Violation from(ArchitectureRuleService.Violation v) {
            return new Violation(v.dependencyId(), v.sourceLayer(), v.targetLayer(), v.sourceClass(), v.targetClass(),
                    v.relationshipType(), v.sourceLine(), v.severity(), v.ruleDescription());
        }
    }
}
