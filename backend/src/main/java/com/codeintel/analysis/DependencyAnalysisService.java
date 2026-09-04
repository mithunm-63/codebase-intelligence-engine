package com.codeintel.analysis;

import com.codeintel.dependency.DependencyAnalysisResult;
import com.codeintel.dependency.DependencyType;
import com.codeintel.dependency.JavaDependencyAnalyzer;
import com.codeintel.dependency.ParsedDependency;
import com.codeintel.project.Project;
import com.codeintel.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DependencyAnalysisService {

    private final JavaDependencyAnalyzer analyzer;
    private final CodeDependencyRepository dependencyRepository;
    private final CodeClassRepository classRepository;
    private final ProjectRepository projectRepository;

    public DependencyAnalysisService(JavaDependencyAnalyzer analyzer,
                                     CodeDependencyRepository dependencyRepository,
                                     CodeClassRepository classRepository,
                                     ProjectRepository projectRepository) {
        this.analyzer = analyzer;
        this.dependencyRepository = dependencyRepository;
        this.classRepository = classRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public DependencyAnalysisResult analyze(Project project, Path sourceRoot) throws IOException {
        DependencyAnalysisResult result = analyzer.analyze(project, sourceRoot);
        dependencyRepository.deleteAllBySourceClass_Project_Id(project.getId());

        Map<String, CodeClass> classes = new LinkedHashMap<>();
        for (CodeClass codeClass : classRepository.findAllByProject_IdOrderByQualifiedName(project.getId())) {
            classes.put(codeClass.getQualifiedName(), codeClass);
        }

        java.util.Set<String> persisted = new java.util.HashSet<>();
        for (ParsedDependency dependency : result.dependencies()) {
            String key = dependency.sourceQualifiedName() + "|" + dependency.targetQualifiedName() + "|" + dependency.type();
            if (!persisted.add(key)) continue;

            CodeClass source = classes.get(dependency.sourceQualifiedName());
            CodeClass target = classes.get(dependency.targetQualifiedName());
            if (source == null || target == null) continue;

            CodeDependency entity = new CodeDependency();
            entity.setSourceClass(source);
            entity.setTargetClass(target);
            entity.setType(dependency.type());
            entity.setSourceLine(dependency.line());
            entity.setSourceMember(dependency.sourceMember());
            entity.setOccurrenceCount(dependency.occurrenceCount());
            entity.setEvidence(dependency.evidence());
            dependencyRepository.save(entity);
        }

        project.setDependencyCount(result.resolvedDependencyCount());
        project.setDependencyOccurrenceCount(result.dependencyOccurrences());
        project.setUnresolvedReferenceCount(result.unresolvedReferenceCount());
        project.setUnresolvedReferences(String.join("\n", result.unresolvedReferences()));
        projectRepository.save(project);
        return result;
    }
}
