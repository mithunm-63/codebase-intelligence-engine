package com.codeintel.graph;

import com.codeintel.analysis.CodeClass;
import com.codeintel.analysis.CodeClassRepository;
import com.codeintel.analysis.CodeDependency;
import com.codeintel.analysis.CodeDependencyRepository;
import com.codeintel.project.ProjectRepository;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ArchitectureGraphService {

    private final Driver driver;
    private final ProjectRepository projectRepository;
    private final CodeClassRepository classRepository;
    private final CodeDependencyRepository dependencyRepository;

    public ArchitectureGraphService(Driver driver,
                                     ProjectRepository projectRepository,
                                     CodeClassRepository classRepository,
                                     CodeDependencyRepository dependencyRepository) {
        this.driver = driver;
        this.projectRepository = projectRepository;
        this.classRepository = classRepository;
        this.dependencyRepository = dependencyRepository;
    }

    @Transactional(readOnly = true)
    public GraphSyncSummary syncProject(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found.");
        }

        List<CodeClass> classes = classRepository.findAllByProject_IdOrderByQualifiedName(projectId);
        List<CodeDependency> dependencies = dependencyRepository.findAllBySourceClass_Project_Id(projectId);

        Map<String, Object> projectNode = Map.of(
                "projectId", projectId,
                "name", projectRepository.findById(projectId).map(p -> p.getName()).orElse(projectId)
        );

        List<Map<String, Object>> classNodes = classes.stream()
                .map(codeClass -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("projectId", projectId);
                    row.put("classId", String.valueOf(codeClass.getId()));
                    row.put("name", codeClass.getName());
                    row.put("qualifiedName", codeClass.getQualifiedName());
                    row.put("kind", codeClass.getKind());
                    String packageName = packageName(codeClass.getQualifiedName());
                    row.put("packageName", packageName);
                    row.put("packageId", packageId(projectId, packageName));
                    return row;
                })
                .toList();

        List<Map<String, Object>> packageNodes = classes.stream()
                .map(c -> packageName(c.getQualifiedName()))
                .distinct()
                .map(packageName -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("projectId", projectId);
                    row.put("packageId", packageId(projectId, packageName));
                    row.put("name", packageName);
                    row.put("qualifiedName", packageName);
                    return row;
                })
                .toList();

        List<Map<String, Object>> classRelationships = dependencies.stream()
                .map(dependency -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("sourceId", String.valueOf(dependency.getSourceClass().getId()));
                    row.put("targetId", String.valueOf(dependency.getTargetClass().getId()));
                    row.put("dependencyType", dependency.getType().name());
                    row.put("sourceLine", dependency.getSourceLine());
                    row.put("sourceMember", safe(dependency.getSourceMember()));
                    row.put("occurrenceCount", dependency.getOccurrenceCount());
                    row.put("evidence", safe(dependency.getEvidence()));
                    return row;
                }).toList();

        Map<String, PackageRelationship> packageRelationshipMap = new LinkedHashMap<>();
        for (CodeDependency dependency : dependencies) {
            String sourcePackage = packageName(dependency.getSourceClass().getQualifiedName());
            String targetPackage = packageName(dependency.getTargetClass().getQualifiedName());
            if (sourcePackage.equals(targetPackage)) continue;
            String key = sourcePackage + "\n" + targetPackage;
            PackageRelationship relation = packageRelationshipMap.computeIfAbsent(key,
                    ignored -> new PackageRelationship(sourcePackage, targetPackage));
            relation.increment(dependency.getType().name());
        }
        List<Map<String, Object>> packageRelationships = packageRelationshipMap.values().stream()
                .map(relation -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("sourceId", packageId(projectId, relation.sourcePackage()));
                    row.put("targetId", packageId(projectId, relation.targetPackage()));
                    row.put("dependencyCount", relation.dependencyCount());
                    row.put("dependencyTypes", relation.dependencyTypes());
                    return row;
                }).toList();

        try (Session session = driver.session()) {
            session.run("MATCH (n) WHERE n.projectId = $projectId DETACH DELETE n",
                    Values.parameters("projectId", projectId)).consume();

            session.run("""
                    CREATE (p:Project {projectId: $projectId, name: $name})
                    """, Values.parameters("projectId", projectNode.get("projectId"), "name", projectNode.get("name"))).consume();

            session.run("""
                    UNWIND $packages AS row
                    CREATE (p:Package {
                        projectId: row.projectId,
                        packageId: row.packageId,
                        name: row.name,
                        qualifiedName: row.qualifiedName
                    })
                    """, Values.parameters("packages", packageNodes)).consume();

            session.run("""
                    UNWIND $classes AS row
                    CREATE (c:CodeClass {
                        projectId: row.projectId,
                        classId: row.classId,
                        name: row.name,
                        qualifiedName: row.qualifiedName,
                        kind: row.kind,
                        packageName: row.packageName
                    })
                    """, Values.parameters("classes", classNodes)).consume();

            session.run("""
                    MATCH (p:Project {projectId: $projectId})
                    MATCH (c:CodeClass {projectId: $projectId})
                    MERGE (p)-[:CONTAINS]->(c)
                    """, Values.parameters("projectId", projectId)).consume();

            session.run("""
                    UNWIND $classes AS row
                    MATCH (c:CodeClass {projectId: $projectId, classId: row.classId})
                    MATCH (p:Package {projectId: $projectId, packageId: row.packageId})
                    MERGE (p)-[:CONTAINS]->(c)
                    """, Values.parameters("projectId", projectId, "classes", classNodes)).consume();

            session.run("""
                    UNWIND $relationships AS row
                    MATCH (s:CodeClass {projectId: $projectId, classId: row.sourceId})
                    MATCH (t:CodeClass {projectId: $projectId, classId: row.targetId})
                    CREATE (s)-[:DEPENDS_ON {
                        dependencyType: row.dependencyType,
                        sourceLine: row.sourceLine,
                        sourceMember: row.sourceMember,
                        occurrenceCount: row.occurrenceCount,
                        evidence: row.evidence
                    }]->(t)
                    """, Values.parameters("projectId", projectId, "relationships", classRelationships)).consume();

            session.run("""
                    UNWIND $relationships AS row
                    MATCH (s:Package {projectId: $projectId, packageId: row.sourceId})
                    MATCH (t:Package {projectId: $projectId, packageId: row.targetId})
                    CREATE (s)-[:DEPENDS_ON {
                        dependencyCount: row.dependencyCount,
                        dependencyTypes: row.dependencyTypes
                    }]->(t)
                    """, Values.parameters("projectId", projectId, "relationships", packageRelationships)).consume();
        }

        return new GraphSyncSummary(projectId, classes.size(), dependencies.size(), packageNodes.size(), packageRelationships.size());
    }

    public GraphData getGraph(String projectId, View view, int nodeLimit, int edgeLimit) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found.");
        }
        int safeNodeLimit = Math.min(Math.max(nodeLimit, 1), 500);
        int safeEdgeLimit = Math.min(Math.max(edgeLimit, 1), 2000);

        try (Session session = driver.session()) {
            if (view == View.PACKAGE) {
                return getPackageGraph(session, projectId, safeNodeLimit, safeEdgeLimit);
            }
            return getClassGraph(session, projectId, safeNodeLimit, safeEdgeLimit);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Neo4j graph store is unavailable. Check NEO4J_URI, NEO4J_USERNAME and NEO4J_PASSWORD.",
                    ex);
        }
    }

    private GraphData getClassGraph(Session session, String projectId, int nodeLimit, int edgeLimit) {
        var result = session.run("""
                MATCH (c:CodeClass {projectId: $projectId})
                RETURN c.classId AS id, c.name AS name, c.qualifiedName AS qualifiedName,
                       c.kind AS kind, c.packageName AS packageName
                ORDER BY c.qualifiedName
                LIMIT $limit
                """, Values.parameters("projectId", projectId, "limit", nodeLimit));
        List<GraphNode> nodes = result.list(record -> new GraphNode(
                record.get("id").asString(), "class", record.get("name").asString(),
                record.get("qualifiedName").asString(), record.get("kind").asString(), record.get("packageName").asString()));
        List<String> ids = nodes.stream().map(GraphNode::id).toList();
        if (ids.isEmpty()) return new GraphData(projectId, "class", List.of(), List.of());

        var edgeResult = session.run("""
                MATCH (s:CodeClass {projectId: $projectId})-[r:DEPENDS_ON]->(t:CodeClass {projectId: $projectId})
                WHERE s.classId IN $ids AND t.classId IN $ids
                RETURN s.classId AS sourceId, t.classId AS targetId,
                       r.dependencyType AS dependencyType, r.sourceLine AS sourceLine,
                       r.sourceMember AS sourceMember, r.occurrenceCount AS occurrenceCount,
                       r.evidence AS evidence
                ORDER BY sourceId, targetId, dependencyType
                LIMIT $limit
                """, Values.parameters("projectId", projectId, "ids", ids, "limit", edgeLimit));
        List<GraphEdge> edges = edgeResult.list(record -> new GraphEdge(
                record.get("sourceId").asString(), record.get("targetId").asString(),
                record.get("dependencyType").asString(),
                record.get("sourceLine").isNull() ? 0 : record.get("sourceLine").asInt(),
                record.get("sourceMember").isNull() ? "" : record.get("sourceMember").asString(),
                record.get("occurrenceCount").isNull() ? 0 : record.get("occurrenceCount").asInt(),
                record.get("evidence").isNull() ? "" : record.get("evidence").asString()));
        return new GraphData(projectId, "class", nodes, edges);
    }

    private GraphData getPackageGraph(Session session, String projectId, int nodeLimit, int edgeLimit) {
        var result = session.run("""
                MATCH (p:Package {projectId: $projectId})
                RETURN p.packageId AS id, p.name AS name, p.qualifiedName AS qualifiedName
                ORDER BY p.name
                LIMIT $limit
                """, Values.parameters("projectId", projectId, "limit", nodeLimit));
        List<GraphNode> nodes = result.list(record -> new GraphNode(
                record.get("id").asString(), "package", record.get("name").asString(),
                record.get("qualifiedName").asString(), "PACKAGE", record.get("name").asString()));
        List<String> ids = nodes.stream().map(GraphNode::id).toList();
        if (ids.isEmpty()) return new GraphData(projectId, "package", List.of(), List.of());

        var edgeResult = session.run("""
                MATCH (s:Package {projectId: $projectId})-[r:DEPENDS_ON]->(t:Package {projectId: $projectId})
                WHERE s.packageId IN $ids AND t.packageId IN $ids
                RETURN s.packageId AS sourceId, t.packageId AS targetId,
                       r.dependencyCount AS dependencyCount, r.dependencyTypes AS dependencyTypes
                ORDER BY sourceId, targetId
                LIMIT $limit
                """, Values.parameters("projectId", projectId, "ids", ids, "limit", edgeLimit));
        List<GraphEdge> edges = edgeResult.list(record -> new GraphEdge(
                record.get("sourceId").asString(), record.get("targetId").asString(), "PACKAGE_DEPENDS_ON",
                0, "", record.get("dependencyCount").isNull() ? 0 : record.get("dependencyCount").asInt(),
                record.get("dependencyTypes").isNull() ? "" : String.join(",", record.get("dependencyTypes").asList(v -> v.asString()))));
        return new GraphData(projectId, "package", nodes, edges);
    }

    private static String packageName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot > 0 ? qualifiedName.substring(0, lastDot) : "<default>";
    }

    private static String packageId(String projectId, String packageName) {
        return projectId + "::" + packageName;
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public enum View { CLASS, PACKAGE }

    public record GraphSyncSummary(String projectId, int classNodes, int classEdges, int packageNodes, int packageEdges) {}

    public record GraphData(String projectId, String view, List<GraphNode> nodes, List<GraphEdge> edges) {}

    public record GraphNode(String id, String nodeType, String name, String qualifiedName, String kind, String packageName) {}

    public record GraphEdge(String sourceId, String targetId, String relationshipType,
                            int sourceLine, String sourceMember, int occurrenceCount, String evidence) {}

    private static final class PackageRelationship {
        private final String sourcePackage;
        private final String targetPackage;
        private int dependencyCount;
        private final Set<String> dependencyTypes = new TreeSet<>();

        private PackageRelationship(String sourcePackage, String targetPackage) {
            this.sourcePackage = sourcePackage;
            this.targetPackage = targetPackage;
        }

        private void increment(String type) {
            dependencyCount++;
            dependencyTypes.add(type);
        }

        private String sourcePackage() { return sourcePackage; }
        private String targetPackage() { return targetPackage; }
        private int dependencyCount() { return dependencyCount; }
        private List<String> dependencyTypes() { return dependencyTypes.stream().toList(); }
    }
}
