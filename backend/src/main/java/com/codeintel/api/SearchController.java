package com.codeintel.api;

import com.codeintel.api.dto.SearchResponse;
import com.codeintel.search.CodebaseSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/analysis/search")
public class SearchController {
    private final CodebaseSearchService searchService;

    public SearchController(CodebaseSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResponse search(@PathVariable String projectId,
                                 @RequestParam String q,
                                 @RequestParam(defaultValue = "ALL") String type,
                                 @RequestParam(defaultValue = "30") Integer limit) {
        return SearchResponse.from(searchService.search(projectId, q, type, limit));
    }
}
