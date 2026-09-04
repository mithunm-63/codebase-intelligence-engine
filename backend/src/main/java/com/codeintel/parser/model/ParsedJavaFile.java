package com.codeintel.parser.model;

import java.util.List;

public record ParsedJavaFile(
        String sourcePath,
        String packageName,
        List<ParsedImport> imports,
        List<ParsedType> types
) {}
