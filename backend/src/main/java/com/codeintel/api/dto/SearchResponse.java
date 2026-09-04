package com.codeintel.api.dto;

import com.codeintel.search.CodebaseSearchService;
import java.util.List;
import java.util.Map;

public record SearchResponse(String projectId, String query, String type, int resultCount,
                             Map<String, Long> resultKinds, List<Result> results) {
    public static SearchResponse from(CodebaseSearchService.SearchReport report) {
        return new SearchResponse(report.projectId(), report.query(), report.type(), report.resultCount(),
                report.resultKinds(), report.results().stream().map(Result::from).toList());
    }

    public record Result(String kind, Long classId, Long methodId, Long dependencyId,
                         String name, String qualifiedName, String sourcePath, String signature,
                         String relationshipType, Integer sourceLine, String sourceMember, int score) {
        static Result from(CodebaseSearchService.Result r) {
            return new Result(r.kind(), r.classId(), r.methodId(), r.dependencyId(), r.name(), r.qualifiedName(),
                    r.sourcePath(), r.signature(), r.relationshipType(), r.sourceLine(), r.sourceMember(), r.score());
        }
    }
}
