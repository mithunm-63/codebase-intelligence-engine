package com.codeintel.api;

import com.codeintel.history.HistoricalRiskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/analysis/historical-risk")
public class HistoricalRiskController {
    private final HistoricalRiskService historicalRiskService;

    public HistoricalRiskController(HistoricalRiskService historicalRiskService) {
        this.historicalRiskService = historicalRiskService;
    }

    @GetMapping
    public HistoricalRiskService.HistoricalRiskReport analyze(@PathVariable String projectId) {
        return historicalRiskService.analyze(projectId);
    }
}
