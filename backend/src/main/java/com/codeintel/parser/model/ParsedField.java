package com.codeintel.parser.model;

public record ParsedField(
        String name,
        String type,
        String modifiers,
        String annotations,
        int line
) {}
