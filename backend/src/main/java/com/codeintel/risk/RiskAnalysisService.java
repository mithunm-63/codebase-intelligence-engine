package com.codeintel.risk;

import com.codeintel.analysis.CodeClass;
import com.codeintel.analysis.CodeClassRepository;
import com.codeintel.analysis.CodeDependency;
import com.codeintel.analysis.CodeDependencyRepository;
import com.codeintel.analysis.CodeMethod;
import com.codeintel.analysis.CodeMethodRepository;
import com.codeintel.project.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class RiskAnalysisService {
    private static final int MAX_HOTSPOTS = 100;

    private final ProjectRepository projectRepository;
    private final CodeClassRepository classRepository;
    private final CodeDependencyRepository dependencyRepository;
    private final CodeMethodRepository methodRepository;

    public RiskAnalysisService(ProjectRepository projectRepository,
                               CodeClassRepository classRepository,
                               CodeDependencyRepository dependencyRepository,
                               CodeMethodRepository methodRepository) {
        this.projectRepository = projectRepository;
        this.classRepository = classRepository;
        this.dependencyRepository = dependencyRepository;
        this.methodRepository = methodRepository;
    }

    public ProjectRiskReport analyzeProject(String projectId) {
        requireProject(projectId);
        List<CodeClass> classes = classRepository.findAllByProject_IdOrderByQualifiedName(projectId);
        List<CodeDependency> dependencies = dependencyRepository.findAllBySourceClass_Project_Id(projectId);
        if (classes.isEmpty()) {
            return new ProjectRiskReport(projectId, 0, 0, 0, 0, 0, 0, List.of());
        }

        Map<Long, Metrics> metrics = new LinkedHashMap<>();
        Map<Long, Set<Long>> outgoing = new HashMap<>();
        Map<Long, Set<Long>> incoming = new HashMap<>();
        for (CodeClass codeClass : classes) {
            outgoing.put(codeClass.getId(), new LinkedHashSet<>());
            incoming.put(codeClass.getId(), new LinkedHashSet<>());
            List<CodeMethod> methods = methodRepository.findAllByCodeClass_IdOrderByStartLine(codeClass.getId());
            List<CodeMethod> executableMethods = methods.stream().filter(m -> "METHOD".equals(m.getKind())).toList();
            int totalMethodLines = executableMethods.stream().mapToInt(CodeMethod::getLineCount).sum();
            int totalComplexity = executableMethods.stream().mapToInt(CodeMethod::getCyclomaticComplexity).sum();
            int maxComplexity = executableMethods.stream().mapToInt(CodeMethod::getCyclomaticComplexity).max().orElse(1);
            metrics.put(codeClass.getId(), new Metrics(codeClass, executableMethods.size(), totalMethodLines, totalComplexity, maxComplexity));
        }
        for (CodeDependency dependency : dependencies) {
            Long source = dependency.getSourceClass().getId();
            Long target = dependency.getTargetClass().getId();
            if (source.equals(target)) continue;
            outgoing.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(target);
            incoming.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(source);
        }

        List<Hotspot> hotspots = new ArrayList<>();
        int totalCycles = countCyclicComponents(classes, outgoing);
        for (CodeClass codeClass : classes) {
            Metrics m = metrics.get(codeClass.getId());
            int fanOut = outgoing.getOrDefault(codeClass.getId(), Set.of()).size();
            int fanIn = incoming.getOrDefault(codeClass.getId(), Set.of()).size();
            int methodLines = m.totalMethodLines();
            int avgMethodLines = m.methodCount() == 0 ? 0 : Math.round((float) methodLines / m.methodCount());
            int weightedCoupling = fanIn * 2 + fanOut * 2;
            int sizeScore = Math.min(25, (int) Math.round(Math.log1p(Math.max(1, codeClass.getLineCount())) * 5));
            int methodScore = Math.min(20, m.methodCount() * 2);
            int couplingScore = Math.min(30, weightedCoupling * 3);
            int complexityScore = Math.min(25, m.totalComplexity() + Math.max(0, avgMethodLines - 20));
            int centralityScore = Math.min(20, fanIn * 2 + fanOut);
            int score = Math.min(100, sizeScore + methodScore + couplingScore + complexityScore + centralityScore);
            String level = score >= 70 ? "HIGH" : score >= 40 ? "MEDIUM" : "LOW";
            List<String> factors = new ArrayList<>();
            if (fanIn >= 5) factors.add(fanIn + " incoming dependencies (high fan-in)");
            if (fanOut >= 5) factors.add(fanOut + " outgoing dependencies (high fan-out)");
            if (codeClass.getLineCount() >= 400) factors.add(codeClass.getLineCount() + " lines in the class");
            if (m.methodCount() >= 20) factors.add(m.methodCount() + " methods in the class");
            if (m.maxComplexity() >= 10) factors.add("method complexity reaches " + m.maxComplexity());
            else if (m.totalComplexity() >= 20) factors.add("combined cyclomatic complexity is " + m.totalComplexity());
            if (avgMethodLines >= 40) factors.add("average method size is " + avgMethodLines + " lines");
            if (factors.isEmpty()) factors.add("no dominant hotspot signal; score is driven by structural metrics");

            hotspots.add(new Hotspot(
                    String.valueOf(codeClass.getId()),
                    codeClass.getName(),
                    codeClass.getQualifiedName(),
                    level,
                    score,
                    codeClass.getLineCount(),
                    m.methodCount(),
                    codeClass.getFieldCount(),
                    fanIn,
                    fanOut,
                    m.totalComplexity(),
                    m.maxComplexity(),
                    avgMethodLines,
                    factors
            ));
        }
        hotspots.sort(Comparator.comparingInt(Hotspot::riskScore).reversed().thenComparing(Hotspot::qualifiedName));
        List<Hotspot> limited = hotspots.stream().limit(MAX_HOTSPOTS).toList();
        long high = hotspots.stream().filter(h -> "HIGH".equals(h.riskLevel())).count();
        long medium = hotspots.stream().filter(h -> "MEDIUM".equals(h.riskLevel())).count();
        int average = (int) Math.round(hotspots.stream().mapToInt(Hotspot::riskScore).average().orElse(0));
        return new ProjectRiskReport(projectId, classes.size(), (int) high, (int) medium, (int) (hotspots.size() - high - medium), average, totalCycles, limited);
    }

    public Hotspot analyzeClass(String projectId, Long classId) {
        ProjectRiskReport report = analyzeProject(projectId);
        return report.hotspots().stream()
                .filter(h -> h.classId().equals(String.valueOf(classId)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found in project analysis."));
    }

    private void requireProject(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found.");
        }
    }

    private int countCyclicComponents(List<CodeClass> classes, Map<Long, Set<Long>> graph) {
        Map<Long, Integer> index = new HashMap<>();
        Map<Long, Integer> low = new HashMap<>();
        Deque<Long> stack = new ArrayDeque<>();
        Set<Long> onStack = new HashSet<>();
        int[] next = {0};
        int[] cycles = {0};
        for (CodeClass c : classes) {
            if (!index.containsKey(c.getId())) tarjan(c.getId(), graph, index, low, stack, onStack, next, cycles);
        }
        return cycles[0];
    }

    private void tarjan(Long v, Map<Long, Set<Long>> graph, Map<Long, Integer> index, Map<Long, Integer> low,
                         Deque<Long> stack, Set<Long> onStack, int[] next, int[] cycles) {
        index.put(v, next[0]); low.put(v, next[0]++); stack.push(v); onStack.add(v);
        for (Long w : graph.getOrDefault(v, Set.of())) {
            if (!index.containsKey(w)) {
                tarjan(w, graph, index, low, stack, onStack, next, cycles);
                low.put(v, Math.min(low.get(v), low.get(w)));
            } else if (onStack.contains(w)) {
                low.put(v, Math.min(low.get(v), index.get(w)));
            }
        }
        if (Objects.equals(low.get(v), index.get(v))) {
            int size = 0; Long n;
            do { n = stack.pop(); onStack.remove(n); size++; } while (!v.equals(n));
            if (size > 1 || graph.getOrDefault(v, Set.of()).contains(v)) cycles[0]++;
        }
    }

    private record Metrics(CodeClass codeClass, int methodCount, int totalMethodLines, int totalComplexity, int maxComplexity) {}

    public record ProjectRiskReport(String projectId, int totalClasses, int highRiskClasses, int mediumRiskClasses,
                                    int lowRiskClasses, int averageRiskScore, int circularComponents,
                                    List<Hotspot> hotspots) {}

    public record Hotspot(String classId, String name, String qualifiedName, String riskLevel, int riskScore,
                          int lineCount, int methodCount, int fieldCount, int fanIn, int fanOut,
                          int totalCyclomaticComplexity, int maxMethodComplexity, int averageMethodLines,
                          List<String> riskFactors) {}
}
