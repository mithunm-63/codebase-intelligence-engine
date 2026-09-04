package com.codeintel.parser.model;

import java.util.List;

public record AstAnalysisResult(
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
        List<ParsedImport> imports,
        List<ParsedType> types
) {}
