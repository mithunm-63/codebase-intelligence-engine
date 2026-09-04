package com.codeintel.api;

import com.codeintel.api.dto.CreateProjectRequest;
import com.codeintel.api.dto.IngestionResponse;
import com.codeintel.api.dto.ProjectResponse;
import com.codeintel.api.dto.AstAnalysisResponse;
import com.codeintel.api.dto.CodeClassDetailResponse;
import com.codeintel.analysis.CodeClassRepository;
import com.codeintel.analysis.CodeFieldRepository;
import com.codeintel.analysis.CodeMethodRepository;
import com.codeintel.analysis.CodeDependencyRepository;
import com.codeintel.api.dto.DependencyAnalysisResponse;
import com.codeintel.ingestion.RepositoryIngestionService;
import com.codeintel.project.Project;
import com.codeintel.project.ProjectRepository;
import com.codeintel.project.ProjectStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final RepositoryIngestionService ingestionService;
    private final CodeClassRepository codeClassRepository;
    private final CodeMethodRepository codeMethodRepository;
    private final CodeFieldRepository codeFieldRepository;
    private final CodeDependencyRepository codeDependencyRepository;

    public ProjectController(ProjectRepository projectRepository, RepositoryIngestionService ingestionService,
                             CodeClassRepository codeClassRepository, CodeMethodRepository codeMethodRepository,
                             CodeFieldRepository codeFieldRepository, CodeDependencyRepository codeDependencyRepository) {
        this.projectRepository = projectRepository;
        this.ingestionService = ingestionService;
        this.codeClassRepository = codeClassRepository;
        this.codeMethodRepository = codeMethodRepository;
        this.codeFieldRepository = codeFieldRepository;
        this.codeDependencyRepository = codeDependencyRepository;
    }

    @PostMapping
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request) {
        if (request.sourceType().name().equals("GITHUB_PUBLIC") &&
                (request.sourceUrl() == null || request.sourceUrl().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceUrl is required for a GitHub project.");
        }
        Project project = new Project();
        project.setName(request.name().trim());
        project.setSourceType(request.sourceType());
        project.setSourceUrl(request.sourceUrl() == null ? null : request.sourceUrl().trim());
        project.setStatus(ProjectStatus.CREATED);
        return ProjectResponse.from(projectRepository.save(project));
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectRepository.findAll().stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable String projectId) {
        return projectRepository.findById(projectId)
                .map(ProjectResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    @PostMapping(value = "/{projectId}/ingest/zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestionResponse ingestZip(@PathVariable String projectId,
                                       @RequestPart("file") MultipartFile file) {
        return ingestionService.ingestZip(projectId, file);
    }

    @PostMapping("/{projectId}/ingest/github")
    public IngestionResponse ingestGitHub(@PathVariable String projectId,
                                          @Valid @RequestBody GitHubIngestionRequest request) {
        return ingestionService.ingestGitHub(projectId, request.repositoryUrl());
    }

    @GetMapping("/{projectId}/analysis/ast")
    public AstAnalysisResponse astSummary(@PathVariable String projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
        List<AstAnalysisResponse.ClassSummary> classes = codeClassRepository
                .findAllByProject_IdOrderByQualifiedName(projectId)
                .stream().limit(200).map(AstAnalysisResponse.ClassSummary::from).toList();
        return new AstAnalysisResponse(projectId, project.getStatus().name(), project.getAstAnalyzedAt(),
                nz(project.getClassCount()), nz(project.getInterfaceCount()), nz(project.getEnumCount()),
                nz(project.getRecordCount()), nz(project.getAnnotationCount()), nz(project.getMethodCount()),
                nz(project.getConstructorCount()), nz(project.getFieldCount()), nz(project.getImportCount()), nz(project.getParseErrorCount()),
                project.getParseErrors() == null || project.getParseErrors().isBlank()
                        ? List.of() : project.getParseErrors().lines().toList(), classes);
    }

    @GetMapping("/{projectId}/analysis/classes")
    public List<AstAnalysisResponse.ClassSummary> classes(@PathVariable String projectId,
                                                           @org.springframework.web.bind.annotation.RequestParam(defaultValue = "200") int limit,
                                                           @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int offset) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found.");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        int safeOffset = Math.max(offset, 0);
        return codeClassRepository.findAllByProject_IdOrderByQualifiedName(projectId)
                .stream().skip(safeOffset).limit(safeLimit).map(AstAnalysisResponse.ClassSummary::from).toList();
    }

    @GetMapping("/{projectId}/analysis/classes/{classId}")
    public CodeClassDetailResponse classDetail(@PathVariable String projectId, @PathVariable Long classId) {
        var codeClass = codeClassRepository.findByIdAndProject_Id(classId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found."));
        return CodeClassDetailResponse.from(codeClass,
                codeFieldRepository.findAllByCodeClass_IdOrderByLine(classId),
                codeMethodRepository.findAllByCodeClass_IdOrderByStartLine(classId),
                codeDependencyRepository.findAllBySourceClass_Id(classId),
                codeDependencyRepository.findAllByTargetClass_Id(classId));
    }


    @GetMapping("/{projectId}/analysis/dependencies")
    public DependencyAnalysisResponse dependencies(@PathVariable String projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
        var edges = codeDependencyRepository.findAllBySourceClass_Project_Id(projectId);
        Map<String, Integer> relationshipTypes = edges.stream()
                .collect(java.util.stream.Collectors.groupingBy(d -> d.getType().name(),
                        java.util.LinkedHashMap::new, java.util.stream.Collectors.summingInt(ignored -> 1)));
        return new DependencyAnalysisResponse(
                project.getId(),
                nz(project.getDependencyCount()),
                nz(project.getDependencyOccurrenceCount()),
                nz(project.getUnresolvedReferenceCount()),
                relationshipTypes,
                edges.stream().limit(500).map(DependencyAnalysisResponse.DependencyEdge::from).toList(),
                project.getUnresolvedReferences() == null || project.getUnresolvedReferences().isBlank()
                        ? java.util.List.of() : project.getUnresolvedReferences().lines().toList());
    }

    @GetMapping("/{projectId}/analysis/classes/{classId}/dependencies")
    public List<DependencyAnalysisResponse.DependencyEdge> classDependencies(@PathVariable String projectId, @PathVariable Long classId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found.");
        }
        var codeClass = codeClassRepository.findByIdAndProject_Id(classId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found."));
        return codeDependencyRepository.findAllBySourceClass_Id(codeClass.getId()).stream()
                .map(DependencyAnalysisResponse.DependencyEdge::from).toList();
    }

    @GetMapping("/{projectId}/analysis/classes/{classId}/dependents")
    public List<DependencyAnalysisResponse.DependencyEdge> classDependents(@PathVariable String projectId, @PathVariable Long classId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found.");
        }
        var codeClass = codeClassRepository.findByIdAndProject_Id(classId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found."));
        return codeDependencyRepository.findAllByTargetClass_Id(codeClass.getId()).stream()
                .map(DependencyAnalysisResponse.DependencyEdge::from).toList();
    }

    private int nz(Integer value) { return value == null ? 0 : value; }

    public record GitHubIngestionRequest(@NotBlank String repositoryUrl) {}
}
