package com.codeintel.api;

import com.codeintel.history.GitHistoryService;
import com.codeintel.history.PersistentGitHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/history")
public class GitHistoryController {
    private final GitHistoryService historyService;
    private final PersistentGitHistoryService persistentHistoryService;

    public GitHistoryController(GitHistoryService historyService,
                                PersistentGitHistoryService persistentHistoryService) {
        this.historyService = historyService;
        this.persistentHistoryService = persistentHistoryService;
    }

    @GetMapping
    public GitHistoryService.HistoryReport history(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "25") Integer commits) {
        return historyService.analyze(projectId, commits);
    }

    @PostMapping("/sync")
    public PersistentGitHistoryService.PersistentHistoryReport sync(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "25") Integer commits) {
        return persistentHistoryService.sync(projectId, commits);
    }

    @GetMapping("/persistent")
    public PersistentGitHistoryService.PersistentHistoryReport persistent(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "25") Integer commits) {
        return persistentHistoryService.getStored(projectId, commits);
    }
}
