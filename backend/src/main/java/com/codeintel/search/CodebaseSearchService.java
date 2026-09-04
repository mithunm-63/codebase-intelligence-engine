package com.codeintel.search;

import com.codeintel.analysis.CodeClass;
import com.codeintel.analysis.CodeClassRepository;
import com.codeintel.analysis.CodeDependency;
import com.codeintel.analysis.CodeDependencyRepository;
import com.codeintel.analysis.CodeMethod;
import com.codeintel.analysis.CodeMethodRepository;
import com.codeintel.project.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class CodebaseSearchService {
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;

    private final ProjectRepository projectRepository;
    private final CodeClassRepository classRepository;
    private final CodeMethodRepository methodRepository;
    private final CodeDependencyRepository dependencyRepository;

    public CodebaseSearchService(ProjectRepository projectRepository,
                                 CodeClassRepository classRepository,
                                 CodeMethodRepository methodRepository,
                                 CodeDependencyRepository dependencyRepository) {
        this.projectRepository = projectRepository;
        this.classRepository = classRepository;
        this.methodRepository = methodRepository;
        this.dependencyRepository = dependencyRepository;
    }

    public SearchReport search(String projectId, String query, String type, Integer requestedLimit) {
        requireProject(projectId);
        String normalized = Optional.ofNullable(query).orElse("").trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank.");
        }
        int limit = Math.min(Math.max(requestedLimit == null ? DEFAULT_LIMIT : requestedLimit, 1), MAX_LIMIT);
        SearchType searchType = parseType(type);

        List<CodeClass> classes = classRepository.findAllByProject_IdOrderByQualifiedName(projectId);
        List<Result> results = new ArrayList<>();

        for (CodeClass codeClass : classes) {
            if (searchType == SearchType.ALL || searchType == SearchType.CLASS || searchType == SearchType.PACKAGE || searchType == SearchType.ENDPOINT) {
                if (matchesClass(codeClass, normalized, searchType)) {
                    int score = score(codeClass.getName(), codeClass.getQualifiedName(), normalized);
                    results.add(Result.forClass(codeClass, score, searchType == SearchType.ENDPOINT ? "ENDPOINT" : "CLASS"));
                }
            }

            if (searchType == SearchType.ALL || searchType == SearchType.METHOD || searchType == SearchType.ENDPOINT) {
                List<CodeMethod> methods = methodRepository.findAllByCodeClass_IdOrderByStartLine(codeClass.getId());
                for (CodeMethod method : methods) {
                    if (matchesMethod(method, codeClass, normalized, searchType)) {
                        int score = score(method.getName(), method.getSignature(), normalized);
                        results.add(Result.forMethod(method, codeClass, score, searchType == SearchType.ENDPOINT ? "ENDPOINT" : "METHOD"));
                    }
                }
            }
        }

        if (searchType == SearchType.ALL || searchType == SearchType.DEPENDENCY) {
            List<CodeDependency> dependencies = dependencyRepository.findAllBySourceClass_Project_Id(projectId);
            for (CodeDependency dependency : dependencies) {
                String source = dependency.getSourceClass().getQualifiedName();
                String target = dependency.getTargetClass().getQualifiedName();
                String haystack = (source + " " + target + " " + dependency.getType().name() + " " + Optional.ofNullable(dependency.getSourceMember()).orElse(""))
                        .toLowerCase(Locale.ROOT);
                if (haystack.contains(normalized)) {
                    int score = score(source, target, normalized);
                    results.add(Result.forDependency(dependency, score));
                }
            }
        }

        results.sort(Comparator.comparingInt(Result::score).reversed()
                .thenComparing(Result::kind)
                .thenComparing(Result::qualifiedName, Comparator.nullsLast(String::compareToIgnoreCase)));

        List<Result> limited = results.stream().limit(limit).toList();
        Map<String, Long> counts = limited.stream().collect(java.util.stream.Collectors.groupingBy(Result::kind, LinkedHashMap::new, java.util.stream.Collectors.counting()));
        return new SearchReport(projectId, normalized, searchType.name(), limited.size(), counts, limited);
    }

    private boolean matchesClass(CodeClass c, String q, SearchType type) {
        String fqn = c.getQualifiedName().toLowerCase(Locale.ROOT);
        String name = c.getName().toLowerCase(Locale.ROOT);
        String pkg = fqn.substring(0, Math.max(0, fqn.lastIndexOf('.')));
        if (type == SearchType.PACKAGE) return pkg.contains(q);
        if (type == SearchType.ENDPOINT) return c.getName().endsWith("Controller") || Optional.ofNullable(c.getAnnotations()).orElse("").toLowerCase(Locale.ROOT).contains("controller");
        return name.contains(q) || fqn.contains(q);
    }

    private boolean matchesMethod(CodeMethod m, CodeClass c, String q, SearchType type) {
        String annotations = Optional.ofNullable(m.getAnnotations()).orElse("").toLowerCase(Locale.ROOT);
        boolean endpoint = annotations.contains("getmapping") || annotations.contains("postmapping") || annotations.contains("putmapping")
                || annotations.contains("deletemapping") || annotations.contains("patchmapping") || annotations.contains("requestmapping");
        if (type == SearchType.ENDPOINT && !endpoint) return false;
        String name = Optional.ofNullable(m.getName()).orElse("").toLowerCase(Locale.ROOT);
        String signature = Optional.ofNullable(m.getSignature()).orElse("").toLowerCase(Locale.ROOT);
        return name.contains(q) || signature.contains(q) || c.getQualifiedName().toLowerCase(Locale.ROOT).contains(q);
    }

    private int score(String first, String second, String q) {
        String a = Optional.ofNullable(first).orElse("").toLowerCase(Locale.ROOT);
        String b = Optional.ofNullable(second).orElse("").toLowerCase(Locale.ROOT);
        if (a.equals(q) || b.equals(q)) return 100;
        if (a.startsWith(q) || b.startsWith(q)) return 80;
        if (a.contains(q) || b.contains(q)) return 60;
        return 20;
    }

    private SearchType parseType(String type) {
        try {
            return SearchType.valueOf(Optional.ofNullable(type).orElse("ALL").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be one of ALL, CLASS, METHOD, ENDPOINT, PACKAGE, DEPENDENCY.");
        }
    }

    private void requireProject(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found.");
        }
    }

    public enum SearchType { ALL, CLASS, METHOD, ENDPOINT, PACKAGE, DEPENDENCY }

    public record SearchReport(String projectId, String query, String type, int resultCount,
                               Map<String, Long> resultKinds, List<Result> results) {}

    public record Result(String kind, Long classId, Long methodId, Long dependencyId,
                         String name, String qualifiedName, String sourcePath, String signature,
                         String relationshipType, Integer sourceLine, String sourceMember,
                         int score) {
        static Result forClass(CodeClass c, int score, String kind) {
            return new Result(kind, c.getId(), null, null, c.getName(), c.getQualifiedName(), c.getSourcePath(), null, null, null, null, score);
        }
        static Result forMethod(CodeMethod m, CodeClass c, int score, String kind) {
            return new Result(kind, c.getId(), m.getId(), null, m.getName(), c.getQualifiedName(), c.getSourcePath(), m.getSignature(), null, null, null, score);
        }
        static Result forDependency(CodeDependency d, int score) {
            return new Result("DEPENDENCY", d.getSourceClass().getId(), null, d.getId(),
                    d.getSourceClass().getName() + " → " + d.getTargetClass().getName(),
                    d.getTargetClass().getQualifiedName(), d.getSourceClass().getSourcePath(), null,
                    d.getType().name(), d.getSourceLine(), d.getSourceMember(), score);
        }
    }
}
