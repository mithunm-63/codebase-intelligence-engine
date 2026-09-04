package com.codeintel.analysis;

import com.codeintel.parser.JavaAstParser;
import com.codeintel.parser.model.AstAnalysisResult;
import com.codeintel.parser.model.ParsedField;
import com.codeintel.parser.model.ParsedImport;
import com.codeintel.parser.model.ParsedMethod;
import com.codeintel.parser.model.ParsedType;
import com.codeintel.project.Project;
import com.codeintel.project.ProjectRepository;
import com.codeintel.project.ProjectStatus;

import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;

@Service
public class AstAnalysisService {
    private final JavaAstParser parser;
    private final ProjectRepository projectRepository;
    private final CodeClassRepository classRepository;
    private final CodeMethodRepository methodRepository;
    private final CodeFieldRepository fieldRepository;
    private final CodeImportRepository importRepository;

    public AstAnalysisService(JavaAstParser parser,
                              ProjectRepository projectRepository,
                              CodeClassRepository classRepository,
                              CodeMethodRepository methodRepository,
                              CodeFieldRepository fieldRepository,
                              CodeImportRepository importRepository) {
        this.parser = parser;
        this.projectRepository = projectRepository;
        this.classRepository = classRepository;
        this.methodRepository = methodRepository;
        this.fieldRepository = fieldRepository;
        this.importRepository = importRepository;
    }

    @Transactional
    public AstAnalysisResult analyze(Project project, Path sourceRoot) throws IOException {
        project.setStatus(ProjectStatus.ANALYZING);
        projectRepository.save(project);

        AstAnalysisResult result = parser.analyze(sourceRoot);
        clearPrevious(project.getId());
        persist(project, result);

        project.setClassCount(result.classCount());
        project.setInterfaceCount(result.interfaceCount());
        project.setEnumCount(result.enumCount());
        project.setRecordCount(result.recordCount());
        project.setAnnotationCount(result.annotationCount());
        project.setMethodCount(result.methodCount());
        project.setConstructorCount(result.constructorCount());
        project.setFieldCount(result.fieldCount());
        project.setImportCount(result.importCount());
        project.setParseErrorCount(result.parseErrorCount());
        String parseErrors = String.join("\n", result.parseErrors());
        project.setParseErrors(parseErrors.substring(0, Math.min(20000, parseErrors.length())));
        project.setAstAnalyzedAt(Instant.now());
        project.setStatus(ProjectStatus.ANALYZED);
        projectRepository.save(project);
        return result;
    }

    private void clearPrevious(String projectId) {
        importRepository.deleteAllByProject_Id(projectId);
        fieldRepository.deleteAllByCodeClass_Project_Id(projectId);
        methodRepository.deleteAllByCodeClass_Project_Id(projectId);
        classRepository.deleteAllByProject_Id(projectId);
    }

    private void persist(Project project, AstAnalysisResult result) {
        for (ParsedType type : result.types()) {
            CodeClass codeClass = new CodeClass();
            codeClass.setProject(project);
            codeClass.setName(simpleName(type.name()));
            codeClass.setQualifiedName(type.qualifiedName());
            codeClass.setKind(type.kind());
            codeClass.setSourcePath(type.sourcePath());
            codeClass.setModifiers(type.modifiers());
            codeClass.setAnnotations(type.annotations());
            codeClass.setStartLine(type.startLine());
            codeClass.setEndLine(type.endLine());
            codeClass.setLineCount(type.lineCount());
            codeClass.setMethodCount((int) type.methods().stream().filter(m -> "METHOD".equals(m.kind())).count());
            codeClass.setConstructorCount((int) type.methods().stream().filter(m -> "CONSTRUCTOR".equals(m.kind())).count());
            codeClass.setFieldCount(type.fields().size());
            CodeClass saved = classRepository.save(codeClass);

            for (ParsedField field : type.fields()) {
                CodeField entity = new CodeField();
                entity.setCodeClass(saved);
                entity.setName(field.name());
                entity.setType(field.type());
                entity.setModifiers(field.modifiers());
                entity.setAnnotations(field.annotations());
                entity.setLine(field.line());
                fieldRepository.save(entity);
            }
            for (ParsedMethod method : type.methods()) {
                CodeMethod entity = new CodeMethod();
                entity.setCodeClass(saved);
                entity.setName(method.name());
                entity.setKind(method.kind());
                entity.setReturnType(method.returnType());
                entity.setSignature(method.signature());
                entity.setModifiers(method.modifiers());
                entity.setAnnotations(method.annotations());
                entity.setParameters(method.parameters());
                entity.setThrownTypes(method.thrownTypes());
                entity.setStartLine(method.startLine());
                entity.setEndLine(method.endLine());
                entity.setLineCount(method.lineCount());
                methodRepository.save(entity);
            }
        }
        persistImports(project, result.imports());
    }

    private void persistImports(Project project, java.util.List<ParsedImport> imports) {
        for (ParsedImport parsed : imports) {
            CodeImport entity = new CodeImport();
            entity.setProject(project);
            entity.setSourcePath(parsed.sourcePath());
            entity.setImportName(parsed.name());
            entity.setStaticImport(parsed.staticImport());
            entity.setWildcard(parsed.wildcard());
            entity.setLine(parsed.line());
            importRepository.save(entity);
        }
    }

    private String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
