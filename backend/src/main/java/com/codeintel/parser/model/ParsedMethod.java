package com.codeintel.parser.model;

public record ParsedMethod(
        String name,
        String kind,
        String returnType,
        String signature,
        String modifiers,
        String annotations,
        String parameters,
        String thrownTypes,
        int startLine,
        int endLine,
        int lineCount
) {}
