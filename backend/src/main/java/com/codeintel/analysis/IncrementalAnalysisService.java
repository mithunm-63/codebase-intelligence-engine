package com.codeintel.analysis;

import com.codeintel.api.dto.IngestionResponse;
import com.codeintel.history.GitCommitRecord;
import com.codeintel.history.GitCommitRecordRepository;
import com.codeintel.history.GitFileChangeRecord;
import com.codeintel.history.GitFileChangeRecordRepository;
import com.codeintel.history.GitHistoryState;
import com.codeintel.history.GitHistoryStateRepository;
import com.codeintel.history.PersistentGitHistoryService;
import com.codeintel.ingestion.RepositoryIngestionService;
import com.codeintel.project.Project;
import com.codeintel.project.ProjectRepository;
import com.codeintel.project.SourceType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class IncrementalAnalysisService {
    private final ProjectRepository projectRepository;
    private final CodeAnalysisStateRepository analysisStateRepository;
    private final GitHistoryStateRepository historyStateRepository;
    private final GitCommitRecordRepository commitRepository;
    private final GitFileChangeRecordRepository fileChangeRepository;
    private final PersistentGitHistoryService persistentHistoryService;
    private final RepositoryIngestionService ingestionService;

    public IncrementalAnalysisService(ProjectRepository projectRepository,
                                      CodeAnalysisStateRepository analysisStateRepository,
                                      GitHistoryStateRepository historyStateRepository,
                                      GitCommitRecordRepository commitRepository,
                                      GitFileChangeRecordRepository fileChangeRepository,
                                      PersistentGitHistoryService persistentHistoryService,
                                      RepositoryIngestionService ingestionService) {
        this.projectRepository = projectRepository;
        this.analysisStateRepository = analysisStateRepository;
        this.historyStateRepository = historyStateRepository;
        this.commitRepository = commitRepository;
        this.fileChangeRepository = fileChangeRepository;
        this.persistentHistoryService = persistentHistoryService;
        this.ingestionService = ingestionService;
    }

    @Transactional
    public IncrementalReport refresh(String projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
        if (project.getSourceType() != SourceType.GITHUB_PUBLIC || project.getSourceUrl() == null || project.getSourceUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Incremental analysis currently requires a public GitHub repository source.");
        }

        PersistentGitHistoryService.PersistentHistoryReport history = persistentHistoryService.sync(projectId, 25);
        GitHistoryState historyState = historyStateRepository.findByProject_Id(projectId).orElse(null);
        if (historyState == null || historyState.getLatestCommitSha() == null || historyState.getLatestCommitSha().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub history did not provide a current commit revision.");
        }

        CodeAnalysisState state = analysisStateRepository.findByProject_Id(projectId).orElse(null);
        if (state == null || state.getAnalyzedCommitSha() == null || state.getAnalyzedCommitSha().isBlank()) {
            state = state == null ? new CodeAnalysisState() : state;
            state.setProject(project);
            state.setAnalyzedCommitSha(historyState.getLatestCommitSha());
            state.setAnalyzedAt(Instant.now());
            analysisStateRepository.save(state);
            return report(project, "BASELINE_INITIALIZED", historyState.getLatestCommitSha(),
                    history.newCommits(), List.of(), false,
                    "Baseline recorded for the current analyzed repository revision. No code re-analysis was needed.");
        }

        String analyzedSha = state.getAnalyzedCommitSha();
        String latestSha = historyState.getLatestCommitSha();
        if (analyzedSha.equals(latestSha)) {
            return report(project, "NO_CHANGES", latestSha, 0, List.of(), false,
                    "Repository head matches the last analyzed revision. Existing code intelligence is already current.");
        }

        List<GitCommitRecord> commits = new ArrayList<>();
        boolean baselineFound = false;
        for (GitCommitRecord commit : commitRepository.findTop40ByProject_IdOrderByCommittedAtDesc(projectId)) {
            if (analyzedSha.equals(commit.getSha())) {
                baselineFound = true;
                break;
            }
            commits.add(commit);
        }

        if (!baselineFound) {
            IngestionResponse response = ingestionService.ingestGitHub(projectId, project.getSourceUrl());
            state.setAnalyzedCommitSha(latestSha);
            state.setAnalyzedAt(Instant.now());
            analysisStateRepository.save(state);
            return report(project, "FULL_REBUILD_REQUIRED", latestSha, commits.size(), List.of(), true,
                    "The previous analyzed revision is outside the retained incremental window, so a safe full re-analysis was performed."
                            + " Classes: " + response.classCount() + ".");
        }

        Set<String> changedPaths = new LinkedHashSet<>();
        Set<String> commitIds = commits.stream().map(GitCommitRecord::getId).collect(java.util.stream.Collectors.toSet());
        if (!commitIds.isEmpty()) {
            for (GitFileChangeRecord change : fileChangeRepository.findByCommit_IdIn(commitIds)) {
                changedPaths.add(change.getPath());
            }
        }

        List<String> changedJavaFiles = changedPaths.stream()
                .filter(path -> path.toLowerCase(java.util.Locale.ROOT).endsWith(".java"))
                .sorted()
                .limit(50)
                .toList();

        if (changedJavaFiles.isEmpty()) {
            state.setAnalyzedCommitSha(latestSha);
            state.setAnalyzedAt(Instant.now());
            analysisStateRepository.save(state);
            return report(project, "NO_JAVA_CHANGES", latestSha, commits.size(), changedPaths.stream().limit(50).toList(), false,
                    "Only non-Java files changed. The Java code intelligence model was kept intact.");
        }

        // The current AST/dependency pipeline rebuilds its persisted symbol model from a complete source tree.
        // We therefore take the safe full-rebuild path when Java files changed, while retaining the incremental
        // revision detection so unchanged revisions and non-Java-only changes avoid a rebuild entirely.
        ingestionService.ingestGitHub(projectId, project.getSourceUrl());
        state.setAnalyzedCommitSha(latestSha);
        state.setAnalyzedAt(Instant.now());
        analysisStateRepository.save(state);
        return report(project, "JAVA_CHANGES_REANALYZED", latestSha, commits.size(), changedJavaFiles, true,
                "Java source changes were detected and a safe full analysis was run. The next iteration can replace this fallback with file-scoped persistence.");
    }

    private IncrementalReport report(Project project, String status, String latestSha, int newCommits,
                                     List<String> changedFiles, boolean reanalyzed, String message) {
        return new IncrementalReport(project.getId(), project.getName(), status,
                latestSha == null ? "" : latestSha.substring(0, Math.min(8, latestSha.length())),
                newCommits, changedFiles, reanalyzed, message);
    }

    public record IncrementalReport(String projectId, String projectName, String status,
                                    String latestCommit, int commitsSinceLastAnalysis,
                                    List<String> changedFiles, boolean reanalyzed, String message) {}
}
