package com.codeintel.ai;

import com.codeintel.analysis.CodeClass;
import com.codeintel.analysis.CodeClassRepository;
import com.codeintel.analysis.CodeDependency;
import com.codeintel.analysis.CodeDependencyRepository;
import com.codeintel.risk.RiskAnalysisService;
import com.codeintel.search.CodebaseSearchService;
import com.codeintel.impact.ImpactAnalysisService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
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
            ImpactAnalysisService impactAnalysisService) {
        this.classRepository = classRepository;
        this.dependencyRepository = dependencyRepository;
        this.searchService = searchService;
        this.riskAnalysisService = riskAnalysisService;
        this.impactAnalysisService = impactAnalysisService;
        this.apiKey = env("GEMINI_API_KEY");
        this.model = envOrDefault("GEMINI_MODEL", "gemini-3.8-flash");
        this.endpoint = envOrDefault("GEMINI_API_ENDPOINT", "https://generativelanguage.googleapis.com/v1beta/models");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public AskReport ask(String projectId, String question) {
        requireConfigured();
        String q = Optional.ofNullable(question).orElse("").trim();
        if (q.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question must not be blank.");
        }
        if (q.length() > MAX_QUESTION_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question is too long; keep it under 600 characters.");
        }
        enforceRateLimit(projectId);

        List<CodeClass> classes = classRepository.findAllByProject_IdOrderByQualifiedName(projectId);
        if (classes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Analyze a repository before asking codebase questions.");
        }

        CodebaseSearchService.SearchReport search = searchService.search(projectId, q, "ALL", 12);
        List<CodeDependency> dependencies = dependencyRepository.findAllBySourceClass_Project_Id(projectId);
        String context = buildContext(projectId, q, classes, dependencies, search);

        String answer = callGemini(q, context);
        return new AskReport(projectId, model, answer,
                search.results().stream().limit(12)
                        .map(CodebaseSearchService.Result::qualifiedName)
                        .filter(Objects::nonNull)
                        .toList());
    }

    private String buildContext(String projectId, String question, List<CodeClass> classes,
                                List<CodeDependency> dependencies, CodebaseSearchService.SearchReport search) {
        StringBuilder out = new StringBuilder();
        out.append("PROJECT_ID: ").append(projectId).append('\n');
        out.append("USER_QUESTION: ").append(question).append("\n\n");
        out.append("RELEVANT_SEARCH_RESULTS:\n");
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
                .append(" | kind=").append(c.getKind())
                .append(" | lines=").append(c.getLineCount())
                .append(" | methods=").append(c.getMethodCount())
                .append(" | fields=").append(c.getFieldCount())
                .append('\n'));

        out.append("\nDEPENDENCY_EDGES:\n");
        dependencies.stream().limit(MAX_DEPENDENCIES).forEach(d -> out.append("- ")
                .append(d.getSourceClass().getQualifiedName()).append(" -> ")
                .append(d.getTargetClass().getQualifiedName())
                .append(" | type=").append(d.getType().name())
                .append(" | line=").append(d.getSourceLine())
                .append(" | member=").append(Optional.ofNullable(d.getSourceMember()).orElse(""))
                .append(" | occurrences=").append(d.getOccurrenceCount())
                .append('\n'));

        String normalized = question.toLowerCase(Locale.ROOT);
        for (CodeClass c : classes) {
            if (normalized.contains(c.getName().toLowerCase(Locale.ROOT))) {
                try {
                    RiskAnalysisService.Hotspot risk = riskAnalysisService.analyzeClass(projectId, c.getId());
                    out.append("\nTARGET_RISK:\n")
                            .append(c.getQualifiedName()).append(" | score=").append(risk.riskScore())
                            .append(" | level=").append(risk.riskLevel())
                            .append(" | fanIn=").append(risk.fanIn())
                            .append(" | fanOut=").append(risk.fanOut())
                            .append(" | maxComplexity=").append(risk.maxMethodComplexity())
                            .append(" | loc=").append(risk.lineCount())
                            .append('\n');

                    ImpactAnalysisService.ProjectImpactReport impact =
                            impactAnalysisService.analyze(projectId, String.valueOf(c.getId()));
                    out.append("TARGET_IMPACT:\n")
                            .append("directDependents=").append(impact.directDependents())
                            .append(" | affectedClasses=").append(impact.transitiveAffectedClasses())
                            .append(" | maxDepth=").append(impact.maxImpactDepth())
                            .append(" | risk=").append(impact.riskLevel())
                            .append('\n');
                } catch (RuntimeException ignored) {
                    // Best-effort enrichment; the grounded dependency context remains authoritative.
                }
                break;
            }
        }

        if (out.length() > MAX_CONTEXT_CHARS) return out.substring(0, MAX_CONTEXT_CHARS);
        return out.toString();
    }

    private String callGemini(String question, String context) {
        String instructions = "You are Codebase Intelligence Engine, an explainable software architecture assistant. "
                + "Answer only from the supplied analyzed-codebase context. Never invent classes, dependencies, lines, endpoints, risks, or behavior. "
                + "When the evidence is insufficient, say exactly that. Distinguish measured facts from interpretation. "
                + "Prefer concise developer-useful answers. When explaining risk, cite concrete graph and metric evidence from context. "
                + "The static-analysis engine is authoritative; you are only translating its evidence into natural language.";

        try {
            Map<String, Object> userPart = Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", "QUESTION:\n" + question + "\n\nANALYZED CODEBASE CONTEXT:\n" + context))
            );
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", instructions))
            ));
            payload.put("contents", List.of(userPart));
            payload.put("generationConfig", Map.of("temperature", 0.1));

            String body = objectMapper.writeValueAsString(payload);
            String requestUrl = endpoint.replaceAll("/$", "") + "/" +
                    URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent?key=" +
                    URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Gemini provider returned HTTP " + response.statusCode() + ".");
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = extractGeminiText(root);
            if (text.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Gemini provider returned no text output.");
            }
            return text.trim();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach the Gemini provider.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request was interrupted.");
        }
    }

    private String extractGeminiText(JsonNode root) {
        StringBuilder text = new StringBuilder();
        JsonNode candidates = root.get("candidates");
        if (candidates != null && candidates.isArray()) {
            for (JsonNode candidate : candidates) {
                JsonNode parts = candidate.path("content").path("parts");
                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        if (part.hasNonNull("text")) {
                            if (!text.isEmpty()) text.append('\n');
                            text.append(part.get("text").asText());
                        }
                    }
                }
            }
        }
        return text.toString();
    }

    private void requireConfigured() {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini assistant is not configured. Set GEMINI_API_KEY on the backend.");
        }
    }

    private void enforceRateLimit(String projectId) {
        long now = System.currentTimeMillis();
        WindowCounter counter = counters.compute(projectId, (key, current) -> {
            if (current == null || now - current.startedAt >= WINDOW_MILLIS) return new WindowCounter(now, 1);
            return new WindowCounter(current.startedAt, current.count + 1);
        });
        if (counter.count > MAX_REQUESTS_PER_PROJECT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "AI question limit reached for this project. Try again later.");
        }
    }

    private String env(String name) {
        return Optional.ofNullable(System.getenv(name)).orElse("").trim();
    }

    private String envOrDefault(String name, String defaultValue) {
        String value = env(name);
        return value.isBlank() ? defaultValue : value;
    }

    public record AskReport(String projectId, String model, String answer, List<String> evidence) {}

    private record WindowCounter(long startedAt, int count) {}
}
