package com.codeintel.api.dto;

import com.codeintel.analysis.CodeClass;
import com.codeintel.analysis.CodeField;
import com.codeintel.analysis.CodeMethod;

import java.util.List;

public record CodeClassDetailResponse(
        long id,
        String name,
        String qualifiedName,
        String kind,
        String sourcePath,
        String modifiers,
        String annotations,
        int startLine,
        int endLine,
        int lineCount,
        List<FieldSummary> fields,
        List<MethodSummary> methods
) {
    public record FieldSummary(long id, String name, String type, String modifiers, String annotations, int line) {
        static FieldSummary from(CodeField f) {
            return new FieldSummary(f.getId(), f.getName(), f.getType(), f.getModifiers(), f.getAnnotations(), f.getLine());
        }
    }
    public record MethodSummary(long id, String name, String kind, String returnType, String signature,
                                String modifiers, String annotations, String parameters, String thrownTypes,
                                int startLine, int endLine, int lineCount) {
        static MethodSummary from(CodeMethod m) {
            return new MethodSummary(m.getId(), m.getName(), m.getKind(), m.getReturnType(), m.getSignature(),
                    m.getModifiers(), m.getAnnotations(), m.getParameters(), m.getThrownTypes(),
                    m.getStartLine(), m.getEndLine(), m.getLineCount());
        }
    }

    public static CodeClassDetailResponse from(CodeClass c, List<CodeField> fields, List<CodeMethod> methods) {
        return new CodeClassDetailResponse(c.getId(), c.getName(), c.getQualifiedName(), c.getKind(), c.getSourcePath(),
                c.getModifiers(), c.getAnnotations(), c.getStartLine(), c.getEndLine(), c.getLineCount(),
                fields.stream().map(FieldSummary::from).toList(), methods.stream().map(MethodSummary::from).toList());
    }
}
