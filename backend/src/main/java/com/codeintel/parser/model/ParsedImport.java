package com.codeintel.parser.model;

public record ParsedImport(String name, boolean staticImport, boolean wildcard, String sourcePath, int line) {}
