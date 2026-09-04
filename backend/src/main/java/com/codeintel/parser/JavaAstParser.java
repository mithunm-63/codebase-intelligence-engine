package com.codeintel.parser;

import com.codeintel.parser.model.AstAnalysisResult;
import com.codeintel.parser.model.ParsedField;
import com.codeintel.parser.model.ParsedImport;
import com.codeintel.parser.model.ParsedJavaFile;
import com.codeintel.parser.model.ParsedMethod;
import com.codeintel.parser.model.ParsedType;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.modifiers.NodeWithModifiers;
import com.github.javaparser.ast.type.Type;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class JavaAstParser {
    private final JavaParser parser;

    public JavaAstParser() {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.parser = new JavaParser(configuration);
    }

    public AstAnalysisResult analyze(Path sourceRoot) throws IOException {
        List<Path> files;
        try (var stream = Files.walk(sourceRoot)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .sorted()
                    .toList();
        }

        List<ParsedType> types = new ArrayList<>();
        List<ParsedImport> imports = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Path file : files) {
            try {
                ParsedJavaFile parsed = parseFile(sourceRoot, file);
                types.addAll(parsed.types());
                imports.addAll(parsed.imports());
            } catch (Exception ex) {
                String relative = sourceRoot.relativize(file).toString().replace('\\', '/');
                errors.add(relative + ": " + safeMessage(ex));
            }
        }

        int classCount = (int) types.stream().filter(t -> "CLASS".equals(t.kind())).count();
        int interfaceCount = (int) types.stream().filter(t -> "INTERFACE".equals(t.kind())).count();
        int enumCount = (int) types.stream().filter(t -> "ENUM".equals(t.kind())).count();
        int recordCount = (int) types.stream().filter(t -> "RECORD".equals(t.kind())).count();
        int annotationCount = (int) types.stream().filter(t -> "ANNOTATION".equals(t.kind())).count();
        int methodCount = types.stream()
                .mapToInt(t -> (int) t.methods().stream().filter(m -> "METHOD".equals(m.kind())).count()).sum();
        int constructorCount = types.stream()
                .mapToInt(t -> (int) t.methods().stream().filter(m -> "CONSTRUCTOR".equals(m.kind())).count()).sum();
        int fieldCount = types.stream().mapToInt(t -> t.fields().size()).sum();

        return new AstAnalysisResult(classCount, interfaceCount, enumCount, recordCount, annotationCount,
                methodCount, constructorCount, fieldCount, imports.size(), errors.size(), errors, imports, types);
    }

    private ParsedJavaFile parseFile(Path root, Path file) throws IOException {
        ParseResult<CompilationUnit> result = parser.parse(file);
        CompilationUnit unit = result.getResult().orElseThrow(() -> new IllegalArgumentException(
                result.getProblems().isEmpty()
                        ? "JavaParser returned no compilation unit."
                        : result.getProblems().get(0).getMessage()));

        String packageName = unit.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        String relativePath = root.relativize(file).toString().replace('\\', '/');
        List<ParsedImport> parsedImports = unit.getImports().stream()
                .map(i -> new ParsedImport(i.getNameAsString(), i.isStatic(), i.isAsterisk(), relativePath, lineStart(i)))
                .toList();
        List<ParsedType> parsedTypes = new ArrayList<>();

        unit.findAll(ClassOrInterfaceDeclaration.class)
                .forEach(t -> parsedTypes.add(parseClassLike(t, packageName, relativePath)));
        unit.findAll(EnumDeclaration.class)
                .forEach(t -> parsedTypes.add(parseEnum(t, packageName, relativePath)));
        unit.findAll(RecordDeclaration.class)
                .forEach(t -> parsedTypes.add(parseRecord(t, packageName, relativePath)));
        unit.findAll(AnnotationDeclaration.class)
                .forEach(t -> parsedTypes.add(parseAnnotation(t, packageName, relativePath)));

        parsedTypes.sort(Comparator.comparing(ParsedType::qualifiedName));
        return new ParsedJavaFile(relativePath, packageName, parsedImports, parsedTypes);
    }

    private ParsedType parseClassLike(ClassOrInterfaceDeclaration type, String packageName, String sourcePath) {
        return buildType(type, packageName, sourcePath, type.isInterface() ? "INTERFACE" : "CLASS");
    }

    private ParsedType parseEnum(EnumDeclaration type, String packageName, String sourcePath) {
        return buildType(type, packageName, sourcePath, "ENUM");
    }

    private ParsedType parseRecord(RecordDeclaration type, String packageName, String sourcePath) {
        ParsedType base = buildType(type, packageName, sourcePath, "RECORD");
        List<ParsedField> fields = new ArrayList<>(base.fields());
        for (Parameter component : type.getParameters()) {
            fields.add(new ParsedField(component.getNameAsString(), component.getType().asString(),
                    "record-component", annotations(component), lineStart(component)));
        }
        return new ParsedType(base.name(), base.qualifiedName(), base.kind(), base.sourcePath(), base.modifiers(),
                base.annotations(), base.startLine(), base.endLine(), base.lineCount(), fields, base.methods());
    }

    private ParsedType parseAnnotation(AnnotationDeclaration type, String packageName, String sourcePath) {
        return buildType(type, packageName, sourcePath, "ANNOTATION");
    }

    private ParsedType buildType(TypeDeclaration<?> type, String packageName, String sourcePath, String kind) {
        String nestedName = enclosingTypePath(type);
        String qualified = packageName.isBlank() ? nestedName : packageName + "." + nestedName;
        int start = lineStart(type);
        int end = lineEnd(type);

        List<ParsedField> fields = new ArrayList<>();
        List<ParsedMethod> methods = new ArrayList<>();

        NodeList<BodyDeclaration<?>> members = type.getMembers();
        for (BodyDeclaration<?> member : members) {
            if (member instanceof FieldDeclaration field) {
                fields.addAll(parseFields(field));
            } else if (member instanceof MethodDeclaration method) {
                methods.add(parseMethod(method));
            } else if (member instanceof ConstructorDeclaration constructor) {
                methods.add(parseConstructor(constructor));
            }
        }

        return new ParsedType(nestedName, qualified, kind, sourcePath, modifiers(type), annotations(type),
                start, end, rangeLineCount(type), fields, methods);
    }

    private List<ParsedField> parseFields(FieldDeclaration field) {
        List<ParsedField> fields = new ArrayList<>();
        for (VariableDeclarator variable : field.getVariables()) {
            fields.add(new ParsedField(variable.getNameAsString(), field.getElementType().asString(),
                    modifiers(field), annotations(field), lineStart(field)));
        }
        return fields;
    }

    private ParsedMethod parseMethod(MethodDeclaration method) {
        return new ParsedMethod(method.getNameAsString(), "METHOD", method.getType().asString(),
                method.getSignature().asString(), modifiers(method), annotations(method),
                join(method.getParameters().stream().map(Object::toString).toList()),
                join(method.getThrownExceptions().stream().map(Type::asString).toList()),
                lineStart(method), lineEnd(method), rangeLineCount(method));
    }

    private ParsedMethod parseConstructor(ConstructorDeclaration constructor) {
        return new ParsedMethod(constructor.getNameAsString(), "CONSTRUCTOR", null,
                constructor.getSignature().asString(), modifiers(constructor), annotations(constructor),
                join(constructor.getParameters().stream().map(Object::toString).toList()),
                join(constructor.getThrownExceptions().stream().map(Type::asString).toList()),
                lineStart(constructor), lineEnd(constructor), rangeLineCount(constructor));
    }

    private String enclosingTypePath(TypeDeclaration<?> type) {
        List<String> names = new ArrayList<>();
        Node current = type;
        while (current != null) {
            if (current instanceof TypeDeclaration<?> td) names.add(td.getNameAsString());
            current = current.getParentNode().orElse(null);
        }
        java.util.Collections.reverse(names);
        return String.join(".", names);
    }

    private String annotations(NodeWithAnnotations<?> node) {
        return join(node.getAnnotations().stream().map(AnnotationExpr::getNameAsString).toList());
    }

    private String modifiers(NodeWithModifiers<?> node) {
        return join(node.getModifiers().stream().map(m -> m.getKeyword().asString()).toList(), " ");
    }

    private String join(List<String> values) { return join(values, ", "); }

    private String join(List<String> values, String delimiter) { return String.join(delimiter, values); }

    private int lineStart(Node node) { return node.getRange().map(r -> r.begin.line).orElse(0); }
    private int lineEnd(Node node) { return node.getRange().map(r -> r.end.line).orElse(0); }
    private int rangeLineCount(Node node) {
        int start = lineStart(node), end = lineEnd(node);
        return start == 0 || end == 0 ? 0 : Math.max(1, end - start + 1);
    }

    private String safeMessage(Exception ex) {
        String msg = ex.getMessage();
        return msg == null || msg.isBlank() ? ex.getClass().getSimpleName() : msg;
    }
}
