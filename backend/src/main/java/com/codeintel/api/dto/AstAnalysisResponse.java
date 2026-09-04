package com.codeintel.api.dto;

import com.codeintel.analysis.CodeClass;
import com.codeintel.parser.model.AstAnalysisResult;

import java.time.Instant;
import java.util.List;

public record AstAnalysisResponse(
        String projectId,
        String status,
        Instant analyzedAt,
        int classCount,
        int interfaceCount,
        int enumCount,
        int recordCount,
        int annotationCount,
        int methodCount,
        int constructorCount,
        int fieldCount,
        int importCount,
        int parseErrorCount,
        List<String> parseErrors,
        List<ClassSummary> classes
) {
    public record ClassSummary(
            long id,
            String name,
            String qualifiedName,
            String kind,
            String sourcePath,
            String annotations,
            String modifiers,
            int lineCount,
            int methodCount,
            int constructorCount,
            int fieldCount
    ) {
        public static ClassSummary from(CodeClass c) {
            return new ClassSummary(c.getId(), c.getName(), c.getQualifiedName(), c.getKind(),
                    c.getSourcePath(), c.getAnnotations(), c.getModifiers(), c.getLineCount(),
                    c.getMethodCount(), c.getConstructorCount(), c.getFieldCount());
        }
    }
}
