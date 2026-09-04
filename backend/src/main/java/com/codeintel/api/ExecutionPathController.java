package com.codeintel.api;

import com.codeintel.api.dto.ExecutionPathResponse;
import com.codeintel.execution.ExecutionPathService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/analysis/execution-paths")
public class ExecutionPathController {
    private final ExecutionPathService service;

    public ExecutionPathController(ExecutionPathService service) {
        this.service = service;
    }

    @GetMapping
    public ExecutionPathResponse analyze(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "25") Integer maxPaths) {
        return ExecutionPathResponse.from(service.analyze(projectId, maxPaths));
    }
}
