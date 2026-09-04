package com.codeintel.dependency;

import com.codeintel.analysis.CodeClass;
import com.codeintel.analysis.CodeClassRepository;
import com.codeintel.project.Project;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.ReferenceType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JavaDependencyAnalyzer {

    private final JavaParser parser;
    private final CodeClassRepository classRepository;

    public JavaDependencyAnalyzer(CodeClassRepository classRepository) {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.parser = new JavaParser(configuration);
        this.classRepository = classRepository;
    }

    public DependencyAnalysisResult analyze(Project project, Path sourceRoot) throws IOException {
        Map<String, CodeClass> byQualifiedName = new LinkedHashMap<>();
        Map<String, List<CodeClass>> bySimpleName = new HashMap<>();

        for (CodeClass codeClass : classRepository.findAllByProject_IdOrderByQualifiedName(project.getId())) {
            byQualifiedName.put(codeClass.getQualifiedName(), codeClass);
            bySimpleName.computeIfAbsent(codeClass.getName(), ignored -> new ArrayList<>()).add(codeClass);
        }

        List<ParsedDependency> dependencies = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();

        List<Path> files;
        try (var stream = Files.walk(sourceRoot)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .sorted()
                    .toList();
        }

        for (Path file : files) {
            CompilationUnit unit;
            try {
                unit = parser.parse(file).getResult().orElseThrow();
            } catch (Exception ignored) {
                continue;
            }

            String packageName = unit.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            List<ImportInfo> imports = unit.getImports().stream()
                    .map(i -> new ImportInfo(i.getNameAsString(), i.isAsterisk(), i.isStatic()))
                    .toList();

            for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
                String sourceFqn = qualifiedTypeName(type, packageName);
                if (!byQualifiedName.containsKey(sourceFqn)) continue;

                Map<String, String> fieldTypes = collectFieldTypes(type, packageName, imports, byQualifiedName, bySimpleName, sourceFqn, unresolved);

                collectAnnotations(type, sourceFqn, type.getAnnotations(), type.getNameAsString(), packageName, imports,
                        byQualifiedName, bySimpleName, dependencies, unresolved);

                if (type instanceof ClassOrInterfaceDeclaration declaration) {
                    for (ClassOrInterfaceType parent : declaration.getExtendedTypes()) {
                        addTypeDependencies(sourceFqn, DependencyType.EXTENDS, parent, declaration,
                                declaration.getNameAsString(), packageName, imports, byQualifiedName, bySimpleName, dependencies, unresolved);
                    }
                    for (ClassOrInterfaceType implemented : declaration.getImplementedTypes()) {
                        addTypeDependencies(sourceFqn, DependencyType.IMPLEMENTS, implemented, declaration,
                                declaration.getNameAsString(), packageName, imports, byQualifiedName, bySimpleName, dependencies, unresolved);
                    }
                } else if (type instanceof EnumDeclaration declaration) {
                    for (ClassOrInterfaceType implemented : declaration.getImplementedTypes()) {
                        addTypeDependencies(sourceFqn, DependencyType.IMPLEMENTS, implemented, declaration,
                                declaration.getNameAsString(), packageName, imports, byQualifiedName, bySimpleName, dependencies, unresolved);
                    }
                } else if (type instanceof RecordDeclaration declaration) {
                    for (ClassOrInterfaceType implemented : declaration.getImplementedTypes()) {
                        addTypeDependencies(sourceFqn, DependencyType.IMPLEMENTS, implemented, declaration,
                                declaration.getNameAsString(), packageName, imports, byQualifiedName, bySimpleName, dependencies, unresolved);
                    }
                }

                for (ImportDeclaration importDeclaration : unit.getImports()) {
                    if (importDeclaration.isStatic()) continue;
                    String resolved = resolveImportedType(importDeclaration.getNameAsString(), importDeclaration.isAsterisk(), byQualifiedName);
                    if (resolved != null) {
                        addDependency(dependencies, sourceFqn, resolved, DependencyType.IMPORT,
                                lineOf(importDeclaration), "", "import " + importDeclaration.getNameAsString()
                                        + (importDeclaration.isAsterisk() ? ".*" : ""));
                    }
                }

                for (BodyDeclaration<?> member : type.getMembers()) {
                    if (member instanceof FieldDeclaration field) {
                        collectAnnotations(field, sourceFqn, field.getAnnotations(), "field", packageName, imports,
                                byQualifiedName, bySimpleName, dependencies, unresolved);
                        for (ClassOrInterfaceType referenced : field.findAll(ClassOrInterfaceType.class)) {
                            addTypeDependencies(sourceFqn, DependencyType.FIELD_TYPE, referenced, field,
                                    firstFieldName(field), packageName, imports, byQualifiedName, bySimpleName,
                                    dependencies, unresolved);
                        }
                    } else if (member instanceof MethodDeclaration method) {
                        collectMethodDependencies(method, sourceFqn, packageName, imports, fieldTypes,
                                byQualifiedName, bySimpleName, dependencies, unresolved);
                    } else if (member instanceof ConstructorDeclaration constructor) {
                        collectConstructorDependencies(constructor, sourceFqn, packageName, imports, fieldTypes,
                                byQualifiedName, bySimpleName, dependencies, unresolved);
                    }
                }
            }
        }

        Map<String, ParsedDependency> aggregated = new LinkedHashMap<>();
        for (ParsedDependency dependency : dependencies) {
            String key = dependency.sourceQualifiedName() + "|" + dependency.targetQualifiedName() + "|" + dependency.type();
            ParsedDependency current = aggregated.get(key);
            if (current == null) {
                aggregated.put(key, new ParsedDependency(
                        dependency.sourceQualifiedName(), dependency.targetQualifiedName(), dependency.type(),
                        dependency.line(), dependency.sourceMember(), dependency.evidence(), 1));
            } else {
                String evidence = current.evidence();
                if (!evidence.contains(dependency.evidence())) {
                    evidence = evidence + " | " + dependency.evidence();
                }
                aggregated.put(key, new ParsedDependency(
                        current.sourceQualifiedName(), current.targetQualifiedName(), current.type(),
                        current.line(), current.sourceMember(), evidence, current.occurrenceCount() + 1));
            }
        }

        Map<DependencyType, Integer> counts = aggregated.values().stream()
                .collect(Collectors.groupingBy(ParsedDependency::type, LinkedHashMap::new, Collectors.summingInt(ignored -> 1)));

        return new DependencyAnalysisResult(
                dependencies.size(),
                aggregated.size(),
                unresolved.size(),
                counts,
                new ArrayList<>(aggregated.values()),
                unresolved.stream().distinct().limit(100).toList()
        );
    }

    private void collectMethodDependencies(
            MethodDeclaration method,
            String sourceFqn,
            String packageName,
            List<ImportInfo> imports,
            Map<String, String> fieldTypes,
            Map<String, CodeClass> byQualifiedName,
            Map<String, List<CodeClass>> bySimpleName,
            List<ParsedDependency> dependencies,
            List<String> unresolved) {

        collectAnnotations(method, sourceFqn, method.getAnnotations(), method.getNameAsString(), packageName, imports,
                byQualifiedName, bySimpleName, dependencies, unresolved);

        for (ClassOrInterfaceType referenced : method.getType().findAll(ClassOrInterfaceType.class)) {
            addTypeDependencies(sourceFqn, DependencyType.METHOD_RETURN_TYPE, referenced, method,
                    method.getNameAsString(), packageName, imports, byQualifiedName, bySimpleName, dependencies, unresolved);
        }

        for (Parameter parameter : method.getParameters()) {
            for (ClassOrInterfaceType referenced : parameter.getType().findAll(ClassOrInterfaceType.class)) {
                addTypeDependencies(sourceFqn, DependencyType.METHOD_PARAMETER, referenced, parameter,
                        method.getNameAsString(), packageName, imports, byQualifiedName, bySimpleName, dependencies, unresolved);
            }
            collectAnnotations(parameter, sourceFqn, parameter.getAnnotations(), method.getNameAsString(), packageName, imports,
                    byQualifiedName, bySimpleName, dependencies, unresolved);
        }

        for (ReferenceType thrownType : method.getThrownExceptions()) {
            for (ClassOrInterfaceType referenced : thrownType.findAll(ClassOrInterfaceType.class)) {
                addTypeDependencies(sourceFqn, DependencyType.THROWS_TYPE, referenced, thrownType,
                        method.getNameAsString(), packageName, imports, byQualifiedName, bySimpleName, dependencies, unresolved);
            }
        }

        Map<String, String> variables = new HashMap<>(fieldTypes);
        for (Parameter parameter : method.getParameters()) {
            addResolvedTypeVariable(parameter.getNameAsString(), parameter.getType(), variables, packageName, imports, byQualifiedName, bySimpleName);
        }
        for (VariableDeclarator variable : method.findAll(VariableDeclarator.class)) {
            addResolvedVariable(variable, variables, packageName, imports, byQualifiedName, bySimpleName);
        }

        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            if (call.getScope().isEmpty()) continue;
            Optional<String> scopeName = simpleScopeName(call.getScope().get());
            if (scopeName.isEmpty()) continue;

            String target = variables.get(scopeName.get());
            if (target == null) {
                Resolution resolution = resolveTypeNameDetailed(scopeName.get(), packageName, imports, byQualifiedName, bySimpleName);
                target = resolution.fqn();
                noteUnresolved(resolution, sourceFqn, lineOf(call), scopeName.get(), unresolved);
            }
            if (target != null) {
                addDependency(dependencies, sourceFqn, target, DependencyType.METHOD_CALL,
                        lineOf(call), method.getNameAsString(), "call " + call.getNameAsString() + "(...)");
            }
        }

        for (ObjectCreationExpr creation : method.findAll(ObjectCreationExpr.class)) {
            for (ClassOrInterfaceType referenced : creation.getType().findAll(ClassOrInterfaceType.class)) {
                addTypeDependencies(sourceFqn, DependencyType.OBJECT_CREATION, referenced, creation,
                        method.getNameAsString(), packageName, imports, byQualifiedName, bySimpleName, dependencies, unresolved);
            }
        }

        for (MethodReferenceExpr reference : method.findAll(MethodReferenceExpr.class)) {
            Resolution resolution = resolveTypeNameDetailed(reference.getScope().toString(), packageName, imports,
                    byQualifiedName, bySimpleName);
            noteUnresolved(resolution, sourceFqn, lineOf(reference), reference.getScope().toString(), unresolved);
            if (resolution.fqn() != null) {
                addDependency(dependencies, sourceFqn, resolution.fqn(), DependencyType.METHOD_CALL,
                        lineOf(reference), method.getNameAsString(), "method-reference " + reference);
            }
        }
    }

    private void collectConstructorDependencies(
            ConstructorDeclaration constructor,
            String sourceFqn,
            String packageName,
            List<ImportInfo> imports,
            Map<String, String> fieldTypes,
            Map<String, CodeClass> byQualifiedName,
            Map<String, List<CodeClass>> bySimpleName,
            List<ParsedDependency> dependencies,
            List<String> unresolved) {

        collectAnnotations(constructor, sourceFqn, constructor.getAnnotations(), constructor.getNameAsString(), packageName, imports,
                byQualifiedName, bySimpleName, dependencies, unresolved);

        for (Parameter parameter : constructor.getParameters()) {
            for (ClassOrInterfaceType referenced : parameter.getType().findAll(ClassOrInterfaceType.class)) {
                addTypeDependencies(sourceFqn, DependencyType.METHOD_PARAMETER, referenced, parameter,
                        constructor.getNameAsString(), packageName, imports, byQualifiedName, bySimpleName, dependencies, unresolved);
            }
        }

        Map<String, String> variables = new HashMap<>(fieldTypes);
        for (Parameter parameter : constructor.getParameters()) {
            addResolvedTypeVariable(parameter.getNameAsString(), parameter.getType(), variables, packageName, imports, byQualifiedName, bySimpleName);
        }
        for (VariableDeclarator variable : constructor.findAll(VariableDeclarator.class)) {
            addResolvedVariable(variable, variables, packageName, imports, byQualifiedName, bySimpleName);
        }

        for (MethodCallExpr call : constructor.findAll(MethodCallExpr.class)) {
            if (call.getScope().isEmpty()) continue;
            Optional<String> scopeName = simpleScopeName(call.getScope().get());
            if (scopeName.isEmpty()) continue;
            String target = variables.get(scopeName.get());
            if (target == null) {
                Resolution resolution = resolveTypeNameDetailed(scopeName.get(), packageName, imports, byQualifiedName, bySimpleName);
                target = resolution.fqn();
                noteUnresolved(resolution, sourceFqn, lineOf(call), scopeName.get(), unresolved);
            }
            if (target != null) {
                addDependency(dependencies, sourceFqn, target, DependencyType.METHOD_CALL,
                        lineOf(call), constructor.getNameAsString(), "call " + call.getNameAsString() + "(...)");
            }
        }

        for (ObjectCreationExpr creation : constructor.findAll(ObjectCreationExpr.class)) {
            for (ClassOrInterfaceType referenced : creation.getType().findAll(ClassOrInterfaceType.class)) {
                addTypeDependencies(sourceFqn, DependencyType.OBJECT_CREATION, referenced, creation,
                        constructor.getNameAsString(), packageName, imports, byQualifiedName, bySimpleName, dependencies, unresolved);
            }
        }
    }

    private Map<String, String> collectFieldTypes(
            TypeDeclaration<?> type,
            String packageName,
            List<ImportInfo> imports,
            Map<String, CodeClass> byQualifiedName,
            Map<String, List<CodeClass>> bySimpleName,
            String sourceFqn,
            List<String> unresolved) {
        Map<String, String> result = new HashMap<>();
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (!(member instanceof FieldDeclaration field)) continue;
            for (VariableDeclarator variable : field.getVariables()) {
                for (ClassOrInterfaceType referenced : variable.getType().findAll(ClassOrInterfaceType.class)) {
                    Resolution resolution = resolveTypeDetailed(referenced, packageName, imports, byQualifiedName, bySimpleName);
                    noteUnresolved(resolution, sourceFqn, lineOf(variable), referenced.asString(), unresolved);
                    if (resolution.fqn() != null) {
                        result.put(variable.getNameAsString(), resolution.fqn());
                        break;
                    }
                }
            }
        }
        return result;
    }

    private void addResolvedVariable(
            VariableDeclarator variable,
            Map<String, String> variables,
            String packageName,
            List<ImportInfo> imports,
            Map<String, CodeClass> byQualifiedName,
            Map<String, List<CodeClass>> bySimpleName) {
        for (ClassOrInterfaceType referenced : variable.getType().findAll(ClassOrInterfaceType.class)) {
            Resolution resolution = resolveTypeDetailed(referenced, packageName, imports, byQualifiedName, bySimpleName);
            if (resolution.fqn() != null) {
                variables.put(variable.getNameAsString(), resolution.fqn());
                return;
            }
        }
    }

    private void addResolvedTypeVariable(
            String variableName, Type declaredType, Map<String, String> variables,
            String packageName, List<ImportInfo> imports,
            Map<String, CodeClass> byQualifiedName, Map<String, List<CodeClass>> bySimpleName) {
        for (ClassOrInterfaceType referenced : declaredType.findAll(ClassOrInterfaceType.class)) {
            Resolution resolution = resolveTypeDetailed(referenced, packageName, imports, byQualifiedName, bySimpleName);
            if (resolution.fqn() != null) {
                variables.put(variableName, resolution.fqn());
                return;
            }
        }
    }

    private void collectAnnotations(
            Node sourceNode,
            String sourceFqn,
            List<com.github.javaparser.ast.expr.AnnotationExpr> annotations,
            String member,
            String packageName,
            List<ImportInfo> imports,
            Map<String, CodeClass> byQualifiedName,
            Map<String, List<CodeClass>> bySimpleName,
            List<ParsedDependency> dependencies,
            List<String> unresolved) {
        for (AnnotationExpr annotation : annotations) {
            Resolution resolution = resolveTypeNameDetailed(annotation.getNameAsString(), packageName, imports,
                    byQualifiedName, bySimpleName);
            noteUnresolved(resolution, sourceFqn, lineOf(annotation), annotation.getNameAsString(), unresolved);
            if (resolution.fqn() != null) {
                addDependency(dependencies, sourceFqn, resolution.fqn(), DependencyType.ANNOTATION,
                        lineOf(annotation), member, "@" + annotation.getNameAsString());
            }
        }
    }

    private void addTypeDependencies(
            String sourceFqn,
            DependencyType type,
            Type sourceType,
            Node evidenceNode,
            String sourceMember,
            String packageName,
            List<ImportInfo> imports,
            Map<String, CodeClass> byQualifiedName,
            Map<String, List<CodeClass>> bySimpleName,
            List<ParsedDependency> dependencies,
            List<String> unresolved) {
        for (ClassOrInterfaceType referenced : sourceType.findAll(ClassOrInterfaceType.class)) {
            Resolution resolution = resolveTypeDetailed(referenced, packageName, imports, byQualifiedName, bySimpleName);
            noteUnresolved(resolution, sourceFqn, lineOf(evidenceNode), referenced.asString(), unresolved);
            if (resolution.fqn() != null) {
                addDependency(dependencies, sourceFqn, resolution.fqn(), type, lineOf(evidenceNode), sourceMember,
                        referenced.asString());
            }
        }
    }

    private Resolution resolveTypeDetailed(
            ClassOrInterfaceType type,
            String packageName,
            List<ImportInfo> imports,
            Map<String, CodeClass> byQualifiedName,
            Map<String, List<CodeClass>> bySimpleName) {
        Resolution direct = resolveTypeNameDetailed(type.getNameWithScope(), packageName, imports, byQualifiedName, bySimpleName);
        if (direct.status() == ResolutionStatus.RESOLVED) return direct;
        return resolveTypeNameDetailed(type.getNameAsString(), packageName, imports, byQualifiedName, bySimpleName);
    }

    private Resolution resolveTypeNameDetailed(
            String rawName,
            String packageName,
            List<ImportInfo> imports,
            Map<String, CodeClass> byQualifiedName,
            Map<String, List<CodeClass>> bySimpleName) {
        if (rawName == null || rawName.isBlank()) return Resolution.notProject();
        String name = rawName.trim();
        if (byQualifiedName.containsKey(name)) return Resolution.resolved(name);

        int genericStart = name.indexOf('<');
        if (genericStart >= 0) name = name.substring(0, genericStart);
        int arrayStart = name.indexOf('[');
        if (arrayStart >= 0) name = name.substring(0, arrayStart);

        for (ImportInfo imp : imports) {
            if (!imp.staticImport && !imp.wildcard) {
                int dot = imp.name.lastIndexOf('.');
                if (dot >= 0 && imp.name.substring(dot + 1).equals(name) && byQualifiedName.containsKey(imp.name)) {
                    return Resolution.resolved(imp.name);
                }
            }
        }

        for (ImportInfo imp : imports) {
            if (!imp.staticImport && imp.wildcard) {
                String candidate = imp.name + "." + name;
                if (byQualifiedName.containsKey(candidate)) return Resolution.resolved(candidate);
            }
        }

        if (!packageName.isBlank()) {
            String samePackage = packageName + "." + name;
            if (byQualifiedName.containsKey(samePackage)) return Resolution.resolved(samePackage);
        }

        List<CodeClass> candidates = bySimpleName.getOrDefault(name, List.of());
        if (candidates.size() == 1) return Resolution.resolved(candidates.get(0).getQualifiedName());
        if (candidates.size() > 1) return Resolution.ambiguous();
        return Resolution.notProject();
    }

    private String resolveImportedType(String importName, boolean wildcard, Map<String, CodeClass> byQualifiedName) {
        if (wildcard) return null;
        return byQualifiedName.containsKey(importName) ? importName : null;
    }

    private Optional<String> simpleScopeName(Expression expression) {
        if (expression instanceof NameExpr nameExpr) return Optional.of(nameExpr.getNameAsString());
        if (expression instanceof FieldAccessExpr fieldAccess) {
            if (fieldAccess.getScope() instanceof ThisExpr) return Optional.of(fieldAccess.getNameAsString());
            return simpleScopeName(fieldAccess.getScope());
        }
        if (expression instanceof ThisExpr) return Optional.of("this");
        return Optional.empty();
    }

    private String qualifiedTypeName(TypeDeclaration<?> type, String packageName) {
        List<String> parts = new ArrayList<>();
        Node current = type;
        while (current != null) {
            if (current instanceof TypeDeclaration<?> declaration) parts.add(declaration.getNameAsString());
            current = current.getParentNode().orElse(null);
        }
        Collections.reverse(parts);
        String nested = String.join(".", parts);
        return packageName.isBlank() ? nested : packageName + "." + nested;
    }

    private String firstFieldName(FieldDeclaration field) {
        return field.getVariables().isEmpty() ? "field" : field.getVariables().get(0).getNameAsString();
    }

    private void noteUnresolved(Resolution resolution, String sourceFqn, int line, String reference, List<String> unresolved) {
        if (resolution.status() == ResolutionStatus.AMBIGUOUS) {
            unresolved.add(sourceFqn + ":" + line + " ambiguous project type " + reference);
        }
    }

    private void addDependency(List<ParsedDependency> dependencies, String source, String target, DependencyType type,
                               int line, String member, String evidence) {
        if (source.equals(target)) return;
        dependencies.add(new ParsedDependency(source, target, type, line, member, evidence, 1));
    }

    private int lineOf(Node node) {
        return node.getRange().map(range -> range.begin.line).orElse(0);
    }

    private enum ResolutionStatus { RESOLVED, AMBIGUOUS, NOT_PROJECT }

    private record Resolution(String fqn, ResolutionStatus status) {
        static Resolution resolved(String fqn) { return new Resolution(fqn, ResolutionStatus.RESOLVED); }
        static Resolution ambiguous() { return new Resolution(null, ResolutionStatus.AMBIGUOUS); }
        static Resolution notProject() { return new Resolution(null, ResolutionStatus.NOT_PROJECT); }
    }

    private record ImportInfo(String name, boolean wildcard, boolean staticImport) {}
}
