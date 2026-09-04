package com.codeintel.api.dto;

import com.codeintel.graph.ArchitectureGraphService;

import java.util.List;

public record ArchitectureGraphResponse(
        String projectId,
        String view,
        int nodeCount,
        int edgeCount,
        List<Node> nodes,
        List<Edge> edges
) {
    public static ArchitectureGraphResponse from(ArchitectureGraphService.GraphData graph) {
        return new ArchitectureGraphResponse(
                graph.projectId(), graph.view(), graph.nodes().size(), graph.edges().size(),
                graph.nodes().stream().map(n -> new Node(n.id(), n.nodeType(), n.name(), n.qualifiedName(), n.kind(), n.packageName())).toList(),
                graph.edges().stream().map(e -> new Edge(e.sourceId(), e.targetId(), e.relationshipType(), e.sourceLine(),
                        e.sourceMember(), e.occurrenceCount(), e.evidence())).toList()
        );
    }

    public record Node(String id, String nodeType, String name, String qualifiedName, String kind, String packageName) {}

    public record Edge(String sourceId, String targetId, String relationshipType, int sourceLine,
                       String sourceMember, int occurrenceCount, String evidence) {}
}
