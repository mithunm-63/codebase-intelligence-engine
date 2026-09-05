package com.codeintel.api;

import com.codeintel.ai.CodebaseAskService;
import com.codeintel.api.dto.CodebaseAskResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/analysis/ask")
public class CodebaseAskController {
    private final CodebaseAskService askService;

    public CodebaseAskController(CodebaseAskService askService) {
        this.askService = askService;
    }

    @PostMapping
    public CodebaseAskResponse ask(
            @PathVariable String projectId,
            @Valid @RequestBody AskRequest request) {
        return CodebaseAskResponse.from(askService.ask(projectId, request.question()));
    }

    public record AskRequest(@NotBlank String question) {}
}
