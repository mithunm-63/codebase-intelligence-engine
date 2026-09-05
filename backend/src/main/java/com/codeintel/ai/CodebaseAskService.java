package com.codeintel.ai;

import com.codeintel.analysis.CodeClass;
import com.codeintel.analysis.CodeClassRepository;
import com.codeintel.analysis.CodeDependency;
import com.codeintel.analysis.CodeDependencyRepository;
import com.codeintel.impact.ImpactAnalysisService;
import com.codeintel.risk.RiskAnalysisService;
import com.codeintel.search.CodebaseSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CodebaseAskService {
    private static final int MAX_QUESTION_LENGTH = 600;
    private static final int MAX_CONTEXT_CHARS = 28000;
    private static final int MAX_DEPENDENCIES = 180;
    private static final int MAX_CLASSES = 80;
    private static final int MAX_REQUESTS_PER_PROJECT = 20;
    private static final long WINDOW_MILLIS = 60 * 60 * 1000L;

    private final CodeClassRepository classRepository;
    private final CodeDependencyRepository dependencyRepository;
    private final CodebaseSearchService searchService;
    private final RiskAnalysisService riskAnalysisService;
    private final ImpactAnalysisService impactAnalysisService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final String endpoint;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public CodebaseAskService(
            CodeClassRepository classRepository,
            CodeDependencyRepository dependencyRepository,
            CodebaseSearchService searchService,
            RiskAnalysisService riskAnalysisService,
            ImpactAnalysisService impactAnalysisService,
            ObjectMapper objectMapper,
            @Value("${OPENAI_API_KEY:${codeintel.ai.api-key:}}") String apiKey,
            @Value("${OPENAI_MODEL:${codeintel.ai.model:gpt-5.6-luna}}") String model,
            @Value("${OPENAI_RESPONSES_URL:${codeintel.ai.endpoint:https://api.openai.com/v1/responses}}") String endpoint) {
        this.classRepository = classRepository;
        this.dependencyRepository = dependencyRepository;
        this.searchService = searchService;
        this.riskAnalysisService = riskAnalysisService;
        this.impactAnalysisService = impactAnalysisService;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gpt-5.6-luna" : model.trim();
        this.endpoint = endpoint == null || endpoint.isBlank() ? "https://api.openai.com/v1/responses" : endpoint.trim();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public AskReport ask(String projectId, String question) {
        requireConfigured();
        String q = Optional.ofNullable(question).orElse("").trim();
        if (q.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question must not be blank.");
        if (q.length() > MAX_QUESTION_LENGTH) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question is too long; keep it under 600 characters.");
        enforceRateLimit(projectId);

        List<CodeClass> classes = classRepository.findAllByProject_IdOrderByQualifiedName(projectId);
        if (classes.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Analyze a repository before asking codebase questions.");

        CodebaseSearchService.SearchReport search = searchService.search(projectId, q, "ALL", 12);
        List<CodeDependency> dependencies = dependencyRepository.findAllBySourceClass_Project_Id(projectId);
        String context = buildContext(projectId, q, classes, dependencies, search);

        String answer = callModel(q, context);
        return new AskReport(projectId, model, answer,
                search.results().stream().limit(12).map(CodebaseSearchService.Result::qualifiedName).filter(Objects::nonNull).toList());
    }

    private String buildContext(String projectId, String question, List<CodeClass> classes,
                                List<CodeDependency> dependencies, CodebaseSearchService.SearchReport search) {
        StringBuilder out = new StringBuilder();
        out.append("PROJECT_ID: ").append(projectId).append('\n');
        out.append("USER_QUESTION: ").append(question).append("\n\nRELEVANT_SEARCH_RESULTS:\n");
        for (CodebaseSearchService.Result r : search.results()) {
            out.append("- ").append(r.kind()).append(" | ").append(r.name()).append(" | ")
                    .append(Optional.ofNullable(r.qualifiedName()).orElse(""))
                    .append(" | line=").append(Optional.ofNullable(r.sourceLine()).orElse(0))
                    .append(" | relation=").append(Optional.ofNullable(r.relationshipType()).orElse(""))
                    .append('\n');
        }
        out.append("\nCLASS_CATALOG:\n");
        classes.stream().limit(MAX_CLASSES).forEach(c -> out.append("- ")
                .append(c.getName()).append(" | ").append(c.getQualifiedName())
                .append(" | kind=").append(c.getKind()).append(" | lines=").append(c.getLineCount())
                .append(" | methods=").append(c.getMethodCount()).append(" | fields=").append(c.getFieldCount()).append('\n'));
        out.append("\nDEPENDENCY_EDGES:\n");
        dependencies.stream().limit(MAX_DEPENDENCIES).forEach(d -> out.append("- ")
                .append(d.getSourceClass().getQualifiedName()).append(" -> ").append(d.getTargetClass().getQualifiedName())
                .append(" | type=").append(d.getType().name()).append(" | line=").append(d.getSourceLine())
                .append(" | member=").append(Optional.ofNullable(d.getSourceMember()).orElse(""))
                .append(" | occurrences=").append(d.getOccurrenceCount()).append('\n'));

        String normalized = question.toLowerCase(Locale.ROOT);
        for (CodeClass c : classes) {
            if (normalized.contains(c.getName().toLowerCase(Locale.ROOT))) {
                try {
                    var risk = riskAnalysisService.analyzeClass(projectId, c.getId());
                    out.append("\nTARGET_RISK:\n").append(c.getQualifiedName())
                            .append(" | score=").append(risk.score()).append(" | level=").append(risk.level())
                            .append(" | fanIn=").append(risk.fanIn()).append(" | fanOut=").append(risk.fanOut())
                            .append(" | maxComplexity=").append(risk.maxComplexity()).append(" | loc=").append(risk.lineCount()).append('\n');
                    var impact = impactAnalysisService.analyze(projectId, String.valueOf(c.getId()));
                    out.append("TARGET_IMPACT:\n").append("directDependents=").append(impact.directDependents())
                            .append(" | affectedClasses=").append(impact.affectedClassCount()).append(" | maxDepth=").append(impact.maxDepth())
                            .append(" | risk=").append(impact.riskLevel()).append('\n');
                } catch (RuntimeException ignored) {
                    // Best effort enrichment; base graph/search facts remain authoritative.
                }
                break;
            }
        }
        return out.length() > MAX_CONTEXT_CHARS ? out.substring(0, MAX_CONTEXT_CHARS) : out.toString();
    }

    private String callModel(String question, String context) {
        String instructions = "You are Codebase Intelligence Engine, an explainable software architecture assistant. "
                + "Answer only from the supplied analyzed-codebase context. Never invent classes, dependencies, lines, endpoints, risks, or behavior. "
                + "When the evidence is insufficient, say so. Distinguish measured facts from interpretation. "
                + "Prefer concise developer-useful answers and cite concrete class, dependency, risk, or impact evidence when available. "
                + "The static-analysis engine is authoritative; you translate its evidence into natural language.";
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("store", false);
            payload.put("instructions", instructions);
            payload.put("input", "QUESTION:\n" + question + "\n\nANALYZED CODEBASE CONTEXT:\n" + context);
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI provider rejected the server API key.");
            if (response.statusCode() == 429) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI provider rate limit reached. Try again shortly.");
            if (response.statusCode() >= 400) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI provider returned HTTP " + response.statusCode() + ".");
            String text = extractOutputText(objectMapper.readTree(response.body()));
            if (text.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI provider returned no text output.");
            return text.trim();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach the AI provider.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI request was interrupted.");
        }
    }

    private String extractOutputText(JsonNode root) {
        StringBuilder text = new StringBuilder();
        collectText(root.get("output"), text);
        if (text.isEmpty() && root.hasNonNull("output_text")) text.append(root.get("output_text").asText());
        return text.toString();
    }

    private void collectText(JsonNode node, StringBuilder out) {
        if (node == null) return;
        if (node.isObject()) {
            if ("output_text".equals(node.path("type").asText()) && node.hasNonNull("text")) {
                if (!out.isEmpty()) out.append("\n");
                out.append(node.get("text").asText());
            }
            node.fields().forEachRemaining(e -> collectText(e.getValue(), out));
        } else if (node.isArray()) node.forEach(child -> collectText(child, out));
    }

    private void requireConfigured() {
        if (apiKey.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "AI assistant is not configured. Set OPENAI_API_KEY on the backend.");
    }

    private void enforceRateLimit(String projectId) {
        long now = System.currentTimeMillis();
        WindowCounter counter = counters.compute(projectId, (key, current) ->
                current == null || now - current.startedAt >= WINDOW_MILLIS
                        ? new WindowCounter(now, 1) : new WindowCounter(current.startedAt, current.count + 1));
        if (counter.count > MAX_REQUESTS_PER_PROJECT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI question limit reached for this project. Try again later.");
        }
    }

    private record WindowCounter(long startedAt, int count) {}

    public record AskReport(String projectId, String model, String answer, List<String> evidence) {}
}
