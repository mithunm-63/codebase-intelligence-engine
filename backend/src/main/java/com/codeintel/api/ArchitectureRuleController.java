package com.codeintel.api;

import com.codeintel.api.dto.ArchitectureRulesResponse;
import com.codeintel.architecture.ArchitectureRule;
import com.codeintel.architecture.ArchitectureRuleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/architecture-rules")
public class ArchitectureRuleController {
    private final ArchitectureRuleService service;

    public ArchitectureRuleController(ArchitectureRuleService service) {
        this.service = service;
    }

    @GetMapping
    public ArchitectureRulesResponse get(@PathVariable String projectId) {
        List<ArchitectureRule> rules = service.listRules(projectId);
        return ArchitectureRulesResponse.from(projectId, rules, service.analyze(projectId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArchitectureRulesResponse upsert(@PathVariable String projectId,
                                            @Valid @RequestBody RuleRequest request) {
        service.upsert(projectId, request.sourceLayer(), request.targetLayer(), request.allowed(),
                request.severity(), request.description());
        List<ArchitectureRule> rules = service.listRules(projectId);
        return ArchitectureRulesResponse.from(projectId, rules, service.analyze(projectId));
    }

    @DeleteMapping("/{ruleId}")
    public ArchitectureRulesResponse delete(@PathVariable String projectId, @PathVariable Long ruleId) {
        service.delete(projectId, ruleId);
        List<ArchitectureRule> rules = service.listRules(projectId);
        return ArchitectureRulesResponse.from(projectId, rules, service.analyze(projectId));
    }

    @GetMapping("/analysis")
    public ArchitectureRulesResponse analysis(@PathVariable String projectId) {
        List<ArchitectureRule> rules = service.listRules(projectId);
        return ArchitectureRulesResponse.from(projectId, rules, service.analyze(projectId));
    }

    public record RuleRequest(@NotBlank String sourceLayer, @NotBlank String targetLayer,
                              boolean allowed, String severity, String description) {}
}
