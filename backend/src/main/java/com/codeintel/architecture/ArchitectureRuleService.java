package com.codeintel.architecture;

import com.codeintel.analysis.CodeClass;
import com.codeintel.analysis.CodeClassRepository;
import com.codeintel.analysis.CodeDependency;
import com.codeintel.analysis.CodeDependencyRepository;
import com.codeintel.project.Project;
import com.codeintel.project.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class ArchitectureRuleService {
    private final ProjectRepository projectRepository;
    private final CodeClassRepository classRepository;
    private final CodeDependencyRepository dependencyRepository;
    private final ArchitectureRuleRepository ruleRepository;

    public ArchitectureRuleService(ProjectRepository projectRepository,
                                   CodeClassRepository classRepository,
                                   CodeDependencyRepository dependencyRepository,
                                   ArchitectureRuleRepository ruleRepository) {
        this.projectRepository = projectRepository;
        this.classRepository = classRepository;
        this.dependencyRepository = dependencyRepository;
        this.ruleRepository = ruleRepository;
    }

    public List<ArchitectureRule> listRules(String projectId) {
        Project project = requireProjectEntity(projectId);
        seedDefaults(project);
        return ruleRepository.findAllByProject_IdOrderBySourceLayerAscTargetLayerAsc(projectId);
    }

    public ArchitectureRule upsert(String projectId, String sourceLayer, String targetLayer,
                                    boolean allowed, String severity, String description) {
        Project project = requireProjectEntity(projectId);
        String source = normalize(sourceLayer);
        String target = normalize(targetLayer);
        ArchitectureRule rule = ruleRepository.findAllByProject_IdOrderBySourceLayerAscTargetLayerAsc(projectId)
                .stream().filter(r -> r.getSourceLayer().equals(source) && r.getTargetLayer().equals(target))
                .findFirst().orElseGet(ArchitectureRule::new);
        rule.setProject(project);
        rule.setSourceLayer(source);
        rule.setTargetLayer(target);
        rule.setAllowed(allowed);
        rule.setSeverity(normalizeSeverity(severity));
        rule.setDescription(description == null || description.isBlank() ? source + " → " + target : description.trim());
        return ruleRepository.save(rule);
    }

    public void delete(String projectId, Long ruleId) {
        requireProject(projectId);
        ArchitectureRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Architecture rule not found."));
        if (!rule.getProject().getId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Architecture rule not found.");
        }
        ruleRepository.delete(rule);
    }

    public ArchitectureReport analyze(String projectId) {
        requireProjectEntity(projectId);
        List<ArchitectureRule> rules = listRules(projectId);
        List<CodeClass> classes = classRepository.findAllByProject_IdOrderByQualifiedName(projectId);
        List<CodeDependency> dependencies = dependencyRepository.findAllBySourceClass_Project_Id(projectId);
        if (rules.isEmpty()) {
            return new ArchitectureReport(projectId, 0, dependencies.size(), 0, 100, List.of(), 0, classes.size());
        }

        List<Violation> violations = new ArrayList<>();
        for (CodeDependency dependency : dependencies) {
            String sourceLayer = layerOf(dependency.getSourceClass());
            String targetLayer = layerOf(dependency.getTargetClass());
            if (sourceLayer.equals(targetLayer)) continue;
            for (ArchitectureRule rule : rules) {
                if (rule.getSourceLayer().equals(sourceLayer) && rule.getTargetLayer().equals(targetLayer) && !rule.isAllowed()) {
                    violations.add(new Violation(
                            String.valueOf(dependency.getId()), sourceLayer, targetLayer,
                            dependency.getSourceClass().getQualifiedName(), dependency.getTargetClass().getQualifiedName(),
                            dependency.getType().name(), dependency.getSourceLine(), rule.getSeverity(),
                            rule.getDescription()));
                    break;
                }
            }
        }
        int checked = dependencies.size();
        int score = checked == 0 ? 100 : Math.max(0, 100 - (int) Math.round(violations.size() * 100.0 / checked));
        long high = violations.stream().filter(v -> "HIGH".equals(v.severity())).count();
        return new ArchitectureReport(projectId, rules.size(), dependencies.size(), violations.size(), score, violations,
                (int) high, classes.size());
    }

    private void seedDefaults(Project project) {
        if (ruleRepository.countByProject_Id(project.getId()) > 0) return;
        createDefault(project, "API", "SERVICE", true, "HIGH", "API controllers may call application services.");
        createDefault(project, "SERVICE", "REPOSITORY", true, "HIGH", "Services may access repositories for persistence.");
        createDefault(project, "SERVICE", "SERVICE", true, "LOW", "Services may collaborate with other services.");
        createDefault(project, "API", "REPOSITORY", false, "HIGH", "Controllers should not bypass the service layer.");
        createDefault(project, "REPOSITORY", "SERVICE", false, "HIGH", "Repositories should not depend upward on services.");
        createDefault(project, "REPOSITORY", "API", false, "HIGH", "Repositories should not depend on API/controllers.");
    }

    private void createDefault(Project project, String source, String target, boolean allowed, String severity, String description) {
        ArchitectureRule rule = new ArchitectureRule();
        rule.setProject(project);
        rule.setSourceLayer(source);
        rule.setTargetLayer(target);
        rule.setAllowed(allowed);
        rule.setSeverity(severity);
        rule.setDescription(description);
        ruleRepository.save(rule);
    }

    private String layerOf(CodeClass codeClass) {
        String pkg = codeClass.getQualifiedName();
        int dot = pkg.lastIndexOf('.');
        String packageName = dot < 0 ? "" : pkg.substring(0, dot).toLowerCase(Locale.ROOT);
        if (packageName.contains("controller") || packageName.contains("api")) return "API";
        if (packageName.contains("service")) return "SERVICE";
        if (packageName.contains("repository") || packageName.contains("dao")) return "REPOSITORY";
        if (packageName.contains("config")) return "CONFIG";
        if (packageName.contains("entity") || packageName.contains("model")) return "MODEL";
        return "OTHER";
    }

    private void requireProject(String id) {
        if (!projectRepository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found.");
    }

    private Project requireProjectEntity(String id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Layer values are required.");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSeverity(String value) {
        String v = value == null ? "HIGH" : value.trim().toUpperCase(Locale.ROOT);
        return Set.of("LOW", "MEDIUM", "HIGH").contains(v) ? v : "HIGH";
    }

    public record Violation(String dependencyId, String sourceLayer, String targetLayer, String sourceClass,
                            String targetClass, String relationshipType, int sourceLine, String severity,
                            String ruleDescription) {}

    public record ArchitectureReport(String projectId, int ruleCount, int dependencyCount, int violationCount,
                                      int complianceScore, List<Violation> violations, int highSeverityViolations,
                                      int classCount) {
        public ArchitectureReport(String projectId, int ruleCount, int dependencyCount, int violationCount,
                                  int complianceScore, List<Violation> violations) {
            this(projectId, ruleCount, dependencyCount, violationCount, complianceScore, violations,
                    (int) violations.stream().filter(v -> "HIGH".equals(v.severity())).count(), 0);
        }
    }
}
