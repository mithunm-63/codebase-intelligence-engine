package com.codeintel.execution;

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
import java.util.regex.Pattern;

@Service
public class ExecutionPathService {
    private static final int DEFAULT_MAX_PATHS = 25;
    private static final int MAX_DEPTH = 10;
    private static final Pattern HTTP_MAPPING = Pattern.compile("@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)", Pattern.CASE_INSENSITIVE);

    private final ProjectRepository projectRepository;
    private final CodeClassRepository classRepository;
    private final CodeDependencyRepository dependencyRepository;
    private final CodeMethodRepository methodRepository;

    public ExecutionPathService(ProjectRepository projectRepository,
                                CodeClassRepository classRepository,
                                CodeDependencyRepository dependencyRepository,
                                CodeMethodRepository methodRepository) {
        this.projectRepository = projectRepository;
        this.classRepository = classRepository;
        this.dependencyRepository = dependencyRepository;
        this.methodRepository = methodRepository;
    }

    public ExecutionPathReport analyze(String projectId, Integer requestedMaxPaths) {
        requireProject(projectId);
        int maxPaths = Math.min(Math.max(requestedMaxPaths == null ? DEFAULT_MAX_PATHS : requestedMaxPaths, 1), 100);
        List<CodeClass> classes = classRepository.findAllByProject_IdOrderByQualifiedName(projectId);
        List<CodeDependency> dependencies = dependencyRepository.findAllBySourceClass_Project_Id(projectId);

        Map<Long, CodeClass> byId = new HashMap<>();
        for (CodeClass codeClass : classes) byId.put(codeClass.getId(), codeClass);

        Map<Long, List<CodeDependency>> adjacency = new HashMap<>();
        for (CodeDependency dependency : dependencies) {
            adjacency.computeIfAbsent(dependency.getSourceClass().getId(), ignored -> new ArrayList<>()).add(dependency);
        }
        adjacency.values().forEach(list -> list.sort(Comparator
                .comparingInt((CodeDependency d) -> layerRank(layerOf(d.getTargetClass())))
                .thenComparing(d -> d.getTargetClass().getQualifiedName())));

        List<EntryPoint> entryPoints = classes.stream()
                .filter(this::isEntryPoint)
                .map(codeClass -> toEntryPoint(codeClass))
                .sorted(Comparator.comparing(EntryPoint::qualifiedName))
                .toList();

        List<Path> paths = new ArrayList<>();
        for (EntryPoint entryPoint : entryPoints) {
            CodeClass start = byId.get(Long.valueOf(entryPoint.classId()));
            if (start == null) continue;
            Deque<CodeClass> nodePath = new ArrayDeque<>();
            Deque<CodeDependency> edgePath = new ArrayDeque<>();
            Set<Long> visiting = new HashSet<>();
            nodePath.addLast(start);
            visiting.add(start.getId());
            dfs(start, adjacency, nodePath, edgePath, visiting, paths, maxPaths);
            if (paths.size() >= maxPaths) break;
        }

        int repositoryPaths = (int) paths.stream().filter(p -> "REPOSITORY".equals(p.terminalLayer()) || "DAO".equals(p.terminalLayer())).count();
        int servicePaths = (int) paths.stream().filter(p -> p.layers().contains("SERVICE")).count();
        return new ExecutionPathReport(projectId, entryPoints, paths, paths.size(), repositoryPaths, servicePaths, classes.size(), dependencies.size());
    }

    private void dfs(CodeClass current,
                     Map<Long, List<CodeDependency>> adjacency,
                     Deque<CodeClass> nodePath,
                     Deque<CodeDependency> edgePath,
                     Set<Long> visiting,
                     List<Path> paths,
                     int maxPaths) {
        if (paths.size() >= maxPaths || nodePath.size() > MAX_DEPTH) return;
        boolean terminal = isRepository(current) && nodePath.size() > 1;
        if (terminal) {
            paths.add(toPath(nodePath, edgePath));
            return;
        }
        for (CodeDependency dependency : adjacency.getOrDefault(current.getId(), List.of())) {
            CodeClass target = dependency.getTargetClass();
            if (visiting.contains(target.getId())) continue;
            nodePath.addLast(target);
            edgePath.addLast(dependency);
            visiting.add(target.getId());
            dfs(target, adjacency, nodePath, edgePath, visiting, paths, maxPaths);
            visiting.remove(target.getId());
            edgePath.removeLast();
            nodePath.removeLast();
            if (paths.size() >= maxPaths) return;
        }
    }

    private EntryPoint toEntryPoint(CodeClass codeClass) {
        List<CodeMethod> methods = methodRepository.findAllByCodeClass_IdOrderByStartLine(codeClass.getId());
        List<String> endpoints = methods.stream()
                .filter(m -> HTTP_MAPPING.matcher(Optional.ofNullable(m.getAnnotations()).orElse("")).find())
                .map(CodeMethod::getSignature)
                .filter(Objects::nonNull)
                .limit(20)
                .toList();
        return new EntryPoint(String.valueOf(codeClass.getId()), codeClass.getName(), codeClass.getQualifiedName(), layerOf(codeClass), endpoints);
    }

    private Path toPath(Deque<CodeClass> nodes, Deque<CodeDependency> edges) {
        List<Node> pathNodes = nodes.stream().map(c -> new Node(
                String.valueOf(c.getId()), c.getName(), c.getQualifiedName(), c.getKind(), layerOf(c), c.getSourcePath())).toList();
        List<Edge> pathEdges = edges.stream().map(d -> new Edge(
                String.valueOf(d.getSourceClass().getId()), String.valueOf(d.getTargetClass().getId()),
                d.getType().name(), d.getSourceLine(), d.getSourceMember(), d.getOccurrenceCount())).toList();
        List<String> layers = pathNodes.stream().map(Node::layer).toList();
        String flow = pathNodes.stream().map(Node::name).reduce((a, b) -> a + " → " + b).orElse("");
        return new Path(flow, pathNodes, pathEdges, layers, layers.isEmpty() ? "" : layers.get(layers.size() - 1), pathNodes.size() - 1);
    }

    private boolean isEntryPoint(CodeClass c) {
        String q = c.getQualifiedName().toLowerCase(Locale.ROOT);
        String annotations = Optional.ofNullable(c.getAnnotations()).orElse("").toLowerCase(Locale.ROOT);
        return c.getName().endsWith("Controller") || q.contains(".api.") || annotations.contains("restcontroller") || annotations.contains("controller");
    }

    private boolean isRepository(CodeClass c) {
        String q = c.getQualifiedName().toLowerCase(Locale.ROOT);
        return q.contains(".repository.") || q.contains(".dao.") || c.getName().endsWith("Repository") || c.getName().endsWith("Dao");
    }

    private String layerOf(CodeClass c) {
        String q = c.getQualifiedName().toLowerCase(Locale.ROOT);
        int dot = q.lastIndexOf('.');
        String pkg = dot < 0 ? q : q.substring(0, dot);
        if (pkg.contains("controller") || pkg.contains(".api") || c.getName().endsWith("Controller")) return "API";
        if (pkg.contains("service")) return "SERVICE";
        if (pkg.contains("repository") || pkg.contains("dao") || c.getName().endsWith("Repository") || c.getName().endsWith("Dao")) return "REPOSITORY";
        if (pkg.contains("config")) return "CONFIG";
        if (pkg.contains("model") || pkg.contains("entity")) return "MODEL";
        return "OTHER";
    }

    private int layerRank(String layer) {
        return switch (layer) {
            case "API" -> 1;
            case "SERVICE" -> 2;
            case "REPOSITORY" -> 3;
            default -> 4;
        };
    }

    private void requireProject(String id) {
        if (!projectRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found.");
        }
    }

    public record ExecutionPathReport(String projectId, List<EntryPoint> entryPoints, List<Path> paths,
                                      int pathCount, int repositoryPaths, int servicePaths,
                                      int classCount, int dependencyCount) {}

    public record EntryPoint(String classId, String name, String qualifiedName, String layer,
                             List<String> endpointMethods) {}

    public record Path(String flow, List<Node> nodes, List<Edge> edges, List<String> layers,
                       String terminalLayer, int hopCount) {}

    public record Node(String classId, String name, String qualifiedName, String kind,
                       String layer, String sourcePath) {}

    public record Edge(String sourceClassId, String targetClassId, String relationshipType,
                       int sourceLine, String sourceMember, int occurrenceCount) {}
}
