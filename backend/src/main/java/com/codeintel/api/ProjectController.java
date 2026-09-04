package com.codeintel.api;

import com.codeintel.api.dto.CreateProjectRequest;
import com.codeintel.api.dto.IngestionResponse;
import com.codeintel.api.dto.ProjectResponse;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final RepositoryIngestionService ingestionService;

    public ProjectController(ProjectRepository projectRepository, RepositoryIngestionService ingestionService) {
        this.projectRepository = projectRepository;
        this.ingestionService = ingestionService;
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

    public record GitHubIngestionRequest(@NotBlank String repositoryUrl) {}
}
