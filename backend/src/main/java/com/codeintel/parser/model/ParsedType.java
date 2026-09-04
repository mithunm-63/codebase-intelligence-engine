package com.codeintel.parser.model;

import java.util.List;

public record ParsedType(
        String name,
        String qualifiedName,
        String kind,
        String sourcePath,
        String modifiers,
        String annotations,
        int startLine,
        int endLine,
        int lineCount,
        List<ParsedField> fields,
        List<ParsedMethod> methods
) {}
