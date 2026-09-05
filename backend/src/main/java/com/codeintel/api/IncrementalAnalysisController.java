package com.codeintel.api;

import com.codeintel.analysis.IncrementalAnalysisService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/analysis/incremental")
public class IncrementalAnalysisController {
    private final IncrementalAnalysisService service;

    public IncrementalAnalysisController(IncrementalAnalysisService service) {
        this.service = service;
    }

    @PostMapping("/refresh")
    public IncrementalAnalysisService.IncrementalReport refresh(@PathVariable String projectId) {
        return service.refresh(projectId);
    }
}
