package com.codeintel.api.dto;

import com.codeintel.execution.ExecutionPathService;

import java.util.List;

public record ExecutionPathResponse(
        String projectId,
        int classCount,
        int dependencyCount,
        int pathCount,
        int repositoryPaths,
        int servicePaths,
        List<EntryPoint> entryPoints,
        List<Path> paths) {

    public static ExecutionPathResponse from(ExecutionPathService.ExecutionPathReport report) {
        return new ExecutionPathResponse(
                report.projectId(),
                report.classCount(),
                report.dependencyCount(),
                report.pathCount(),
                report.repositoryPaths(),
                report.servicePaths(),
                report.entryPoints().stream().map(EntryPoint::from).toList(),
                report.paths().stream().map(Path::from).toList());
    }

    public record EntryPoint(String classId, String name, String qualifiedName, String layer,
                             List<String> endpointMethods) {
        static EntryPoint from(ExecutionPathService.EntryPoint value) {
            return new EntryPoint(value.classId(), value.name(), value.qualifiedName(), value.layer(), value.endpointMethods());
        }
    }

    public record Path(String flow, List<Node> nodes, List<Edge> edges, List<String> layers,
                       String terminalLayer, int hopCount) {
        static Path from(ExecutionPathService.Path value) {
            return new Path(value.flow(), value.nodes().stream().map(Node::from).toList(),
                    value.edges().stream().map(Edge::from).toList(), value.layers(), value.terminalLayer(), value.hopCount());
        }
    }

    public record Node(String classId, String name, String qualifiedName, String kind,
                       String layer, String sourcePath) {
        static Node from(ExecutionPathService.Node value) {
            return new Node(value.classId(), value.name(), value.qualifiedName(), value.kind(), value.layer(), value.sourcePath());
        }
    }

    public record Edge(String sourceClassId, String targetClassId, String relationshipType,
                       int sourceLine, String sourceMember, int occurrenceCount) {
        static Edge from(ExecutionPathService.Edge value) {
            return new Edge(value.sourceClassId(), value.targetClassId(), value.relationshipType(),
                    value.sourceLine(), value.sourceMember(), value.occurrenceCount());
        }
    }
}
