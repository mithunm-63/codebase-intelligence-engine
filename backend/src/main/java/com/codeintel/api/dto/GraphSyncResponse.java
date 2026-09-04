package com.codeintel.api.dto;

import com.codeintel.graph.ArchitectureGraphService;

public record GraphSyncResponse(
        String projectId,
        int classNodes,
        int classEdges,
        int packageNodes,
        int packageEdges
) {
    public static GraphSyncResponse from(ArchitectureGraphService.GraphSyncSummary summary) {
        return new GraphSyncResponse(summary.projectId(), summary.classNodes(), summary.classEdges(),
                summary.packageNodes(), summary.packageEdges());
    }
}
