package com.codeintel.api;

import com.codeintel.history.GitHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/history")
public class GitHistoryController {
    private final GitHistoryService historyService;

    public GitHistoryController(GitHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public GitHistoryService.HistoryReport history(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "25") Integer commits) {
        return historyService.analyze(projectId, commits);
    }
}
