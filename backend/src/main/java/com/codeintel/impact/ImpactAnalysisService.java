package com.codeintel.impact;

import com.codeintel.project.ProjectRepository;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class ImpactAnalysisService {

    private static final int MAX_GRAPH_EDGES = 20_000;
    private static final int MAX_IMPACT_DEPTH = 12;
    private static final int MAX_AFFECTED_CLASSES = 200;
    private static final int MAX_CYCLES = 100;

    private final Driver driver;
    private final ProjectRepository projectRepository;

    public ImpactAnalysisService(Driver driver, ProjectRepository projectRepository) {
        this.driver = driver;
        this.projectRepository = projectRepository;
    }

    public ImpactReport analyze(String projectId, String classId) {
        requireProject(projectId);
        GraphSnapshot graph = loadGraph(projectId);
        Node target = graph.nodes.get(classId);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found in the Neo4j graph.");
        }

        BfsResult impact = breadthFirst(target.id(), graph.reverseAdjacency);
        List<ImpactClass> affected = impact.visited().stream()
                .filter(id -> !id.equals(target.id()))
                .limit(MAX_AFFECTED_CLASSES)
                .map(id -> new ImpactClass(
                        id,
                        graph.nodes.get(id).name(),
                        graph.nodes.get(id).qualifiedName(),
                        impact.depths().getOrDefault(id, 0),
                        toQualifiedPath(impact.paths().getOrDefault(id, List.of(target.id(), id)), graph.nodes)))
                .toList();

        CycleIndex cycles = detectCycles(graph);
        List<List<String>> targetCycles = cycles.cyclesByNode().getOrDefault(target.id(), List.of()).stream()
                .map(cycle -> cycle.stream().map(id -> graph.nodes.get(id).qualifiedName()).toList())
                .toList();

        int directDependents = (int) graph.reverseAdjacency.getOrDefault(target.id(), Set.of()).size();
        int transitiveDependents = Math.max(0, impact.visited().size() - 1);
        int score = riskScore(directDependents, transitiveDependents, impact.maxDepth(), targetCycles.size());
        String level = score >= 70 ? "HIGH" : score >= 35 ? "MEDIUM" : "LOW";

        List<String> factors = new ArrayList<>();
        if (directDependents > 0) factors.add(directDependents + " direct dependent class(es)");
        if (transitiveDependents > directDependents) factors.add(transitiveDependents + " classes in the transitive blast radius");
        if (impact.maxDepth() > 3) factors.add("impact propagates across " + impact.maxDepth() + " dependency levels");
        if (!targetCycles.isEmpty()) factors.add(targetCycles.size() + " circular dependency cycle(s) include this class");
        if (factors.isEmpty()) factors.add("no other project classes currently depend on this class");

        return new ProjectImpactReport(
                projectId,
                target.id(),
                target.name(),
                target.qualifiedName(),
                level,
                score,
                directDependents,
                transitiveDependents,
                impact.maxDepth(),
                graph.nodes.size(),
                graph.edges.size(),
                factors,
                affected,
                targetCycles
        );
    }

    public CycleReport cycles(String projectId) {
        requireProject(projectId);
        GraphSnapshot graph = loadGraph(projectId);
        CycleIndex index = detectCycles(graph);

        List<Cycle> cycles = index.cycles().stream()
                .limit(MAX_CYCLES)
                .map(ids -> new Cycle(ids.stream()
                        .map(id -> graph.nodes.get(id).qualifiedName())
                        .toList(), ids.size() > 2 ? "HIGH" : "MEDIUM"))
                .toList();

        return new CycleReport(projectId, cycles.size(), graph.nodes.size(), graph.edges.size(), cycles);
    }

    private void requireProject(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found.");
        }
    }

    private GraphSnapshot loadGraph(String projectId) {
        try (Session session = driver.session()) {
            var nodeResult = session.run("""
                    MATCH (c:CodeClass {projectId: $projectId})
                    RETURN c.classId AS id, c.name AS name, c.qualifiedName AS qualifiedName,
                           c.kind AS kind, c.packageName AS packageName
                    ORDER BY c.qualifiedName
                    """, Values.parameters("projectId", projectId));

            Map<String, Node> nodes = new LinkedHashMap<>();
            nodeResult.list(record -> {
                Node node = new Node(
                        record.get("id").asString(),
                        record.get("name").asString(),
                        record.get("qualifiedName").asString(),
                        record.get("kind").asString(),
                        record.get("packageName").asString());
                nodes.put(node.id(), node);
                return node;
            });

            var edgeResult = session.run("""
                    MATCH (s:CodeClass {projectId: $projectId})-[r:DEPENDS_ON]->(t:CodeClass {projectId: $projectId})
                    RETURN s.classId AS sourceId, t.classId AS targetId,
                           r.dependencyType AS dependencyType,
                           r.occurrenceCount AS occurrenceCount
                    ORDER BY sourceId, targetId
                    LIMIT $limit
                    """, Values.parameters("projectId", projectId, "limit", MAX_GRAPH_EDGES));

            List<Edge> edges = edgeResult.list(record -> new Edge(
                    record.get("sourceId").asString(),
                    record.get("targetId").asString(),
                    record.get("dependencyType").asString(),
                    record.get("occurrenceCount").isNull() ? 0 : record.get("occurrenceCount").asInt()));

            Map<String, Set<String>> adjacency = new LinkedHashMap<>();
            Map<String, Set<String>> reverse = new LinkedHashMap<>();
            for (String id : nodes.keySet()) {
                adjacency.put(id, new LinkedHashSet<>());
                reverse.put(id, new LinkedHashSet<>());
            }
            for (Edge edge : edges) {
                if (nodes.containsKey(edge.sourceId()) && nodes.containsKey(edge.targetId())) {
                    adjacency.get(edge.sourceId()).add(edge.targetId());
                    reverse.get(edge.targetId()).add(edge.sourceId());
                }
            }
            return new GraphSnapshot(nodes, edges, adjacency, reverse);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Neo4j graph store is unavailable while running impact analysis.",
                    ex);
        }
    }

    private BfsResult breadthFirst(String start, Map<String, Set<String>> adjacency) {
        Map<String, Integer> depths = new LinkedHashMap<>();
        Map<String, String> parent = new HashMap<>();
        Map<String, List<String>> paths = new LinkedHashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        depths.put(start, 0);
        paths.put(start, List.of(start));
        int maxDepth = 0;

        while (!queue.isEmpty() && depths.size() < MAX_AFFECTED_CLASSES + 1) {
            String current = queue.removeFirst();
            int nextDepth = depths.get(current) + 1;
            if (nextDepth > MAX_IMPACT_DEPTH) continue;

            for (String next : adjacency.getOrDefault(current, Set.of())) {
                if (depths.containsKey(next)) continue;
                depths.put(next, nextDepth);
                parent.put(next, current);
                List<String> path = new ArrayList<>(paths.get(current));
                path.add(next);
                paths.put(next, List.copyOf(path));
                maxDepth = Math.max(maxDepth, nextDepth);
                queue.addLast(next);
                if (depths.size() >= MAX_AFFECTED_CLASSES + 1) break;
            }
        }
        return new BfsResult(depths.keySet(), depths, paths, maxDepth);
    }

    private List<String> toQualifiedPath(List<String> ids, Map<String, Node> nodes) {
        return ids.stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .map(Node::qualifiedName)
                .toList();
    }

    private int riskScore(int direct, int transitive, int depth, int cycles) {
        return Math.min(100, direct * 7 + transitive * 3 + Math.max(0, depth - 1) * 4 + cycles * 15);
    }

    private CycleIndex detectCycles(GraphSnapshot graph) {
        Tarjan tarjan = new Tarjan(graph.adjacency);
        List<Set<String>> components = tarjan.run(graph.nodes.keySet());

        List<List<String>> cycles = new ArrayList<>();
        Map<String, List<List<String>>> byNode = new HashMap<>();
        for (Set<String> component : components) {
            String only = component.size() == 1 ? component.iterator().next() : null;
            boolean selfLoop = only != null && graph.adjacency.getOrDefault(only, Set.of()).contains(only);
            if (component.size() <= 1 && !selfLoop) continue;

            List<String> cyclePath = findCyclePath(component, graph.adjacency);
            if (cyclePath.isEmpty()) continue;
            cycles.add(cyclePath);
            for (String nodeId : component) {
                byNode.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(cyclePath);
            }
        }
        cycles.sort(Comparator.comparing((List<String> c) -> c.size()).reversed());
        return new CycleIndex(cycles, byNode);
    }

    private record GraphSnapshot(
            Map<String, Node> nodes,
            List<Edge> edges,
            Map<String, Set<String>> adjacency,
            Map<String, Set<String>> reverseAdjacency) {}

    private record Node(String id, String name, String qualifiedName, String kind, String packageName) {}

    private record Edge(String sourceId, String targetId, String dependencyType, int occurrenceCount) {}

    private record BfsResult(Set<String> visited, Map<String, Integer> depths,
                             Map<String, List<String>> paths, int maxDepth) {}

    private record CycleIndex(List<List<String>> cycles, Map<String, List<List<String>>> cyclesByNode) {}

    private List<String> findCyclePath(Set<String> component, Map<String, Set<String>> adjacency) {
        String start = component.stream().sorted().findFirst().orElse(null);
        if (start == null) return List.of();
        List<String> path = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        path.add(start);
        visiting.add(start);
        if (findCycleFrom(start, start, component, adjacency, path, visiting)) {
            if (!path.isEmpty() && path.get(path.size() - 1).equals(start)) {
                path.remove(path.size() - 1);
            }
            return List.copyOf(path);
        }
        return List.of();
    }

    private boolean findCycleFrom(String current, String start, Set<String> component,
                                  Map<String, Set<String>> adjacency, List<String> path, Set<String> visiting) {
        for (String next : adjacency.getOrDefault(current, Set.of())) {
            if (!component.contains(next)) continue;
            if (next.equals(start) && path.size() > 1) {
                path.add(start);
                return true;
            }
            if (visiting.contains(next)) continue;
            visiting.add(next);
            path.add(next);
            if (findCycleFrom(next, start, component, adjacency, path, visiting)) return true;
            path.remove(path.size() - 1);
            visiting.remove(next);
        }
        return false;
    }

    private static final class Tarjan {
        private final Map<String, Set<String>> graph;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> lowLink = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final List<Set<String>> components = new ArrayList<>();
        private int counter;

        private Tarjan(Map<String, Set<String>> graph) {
            this.graph = graph;
        }

        private List<Set<String>> run(Collection<String> nodes) {
            for (String node : nodes) {
                if (!index.containsKey(node)) strongConnect(node);
            }
            return components;
        }

        private void strongConnect(String node) {
            index.put(node, counter);
            lowLink.put(node, counter++);
            stack.push(node);
            onStack.add(node);

            for (String next : graph.getOrDefault(node, Set.of())) {
                if (!index.containsKey(next)) {
                    strongConnect(next);
                    lowLink.put(node, Math.min(lowLink.get(node), lowLink.get(next)));
                } else if (onStack.contains(next)) {
                    lowLink.put(node, Math.min(lowLink.get(node), index.get(next)));
                }
            }

            if (Objects.equals(lowLink.get(node), index.get(node))) {
                Set<String> component = new LinkedHashSet<>();
                String current;
                do {
                    current = stack.pop();
                    onStack.remove(current);
                    component.add(current);
                } while (!node.equals(current));
                components.add(component);
            }
        }
    }

    public record ProjectImpactReport(
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
            List<ImpactClass> affectedClasses,
            List<List<String>> cyclesInvolvingTarget) {}

    public record ImpactClass(
            String classId,
            String name,
            String qualifiedName,
            int depth,
            List<String> impactPath) {}

    public record CycleReport(
            String projectId,
            int cycleCount,
            int graphNodes,
            int graphEdges,
            List<Cycle> cycles) {}

    public record Cycle(List<String> classes, String severity) {}
}
