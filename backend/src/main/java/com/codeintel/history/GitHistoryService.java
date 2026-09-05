package com.codeintel.history;

import com.codeintel.project.Project;
import com.codeintel.project.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitHistoryService {
    private static final Pattern GITHUB_REPO = Pattern.compile(
            "^https://(?:www\\.)?github\\.com/([^/]+/[^/]+?)(?:/)?(?:\\.git)?$",
            Pattern.CASE_INSENSITIVE);
    private static final int DEFAULT_COMMITS = 25;
    private static final int MAX_COMMITS = 40;
    private static final long CACHE_MILLIS = 2 * 60 * 1000L;

    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String githubToken;
    private final Map<String, CachedReport> cache = new ConcurrentHashMap<>();

    public GitHistoryService(ProjectRepository projectRepository,
                             @org.springframework.beans.factory.annotation.Value("${app.github.token:}") String githubToken) {
        this.projectRepository = projectRepository;
        this.githubToken = githubToken == null ? "" : githubToken.trim();
    }

    public HistoryReport analyze(String projectId, Integer requestedCommits) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
        String repo = extractOwnerRepo(project.getSourceUrl());
        int count = Math.max(5, Math.min(Optional.ofNullable(requestedCommits).orElse(DEFAULT_COMMITS), MAX_COMMITS));
        String cacheKey = projectId + ":" + count;
        long now = System.currentTimeMillis();
        CachedReport cached = cache.get(cacheKey);
        if (cached != null && now - cached.createdAt() < CACHE_MILLIS) return cached.report();

        HistoryReport report = fetchHistory(projectId, project.getName(), repo, count);
        cache.put(cacheKey, new CachedReport(now, report));
        return report;
    }

    private HistoryReport fetchHistory(String projectId, String projectName, String ownerRepo, int count) {
        JsonNode commits = githubGet("https://api.github.com/repos/" + ownerRepo + "/commits?per_page=" + count);
        List<CommitItem> commitItems = new ArrayList<>();
        Map<String, FileAggregate> files = new HashMap<>();
        Map<String, Integer> daily = new TreeMap<>(Comparator.reverseOrder());
        Set<String> authors = new HashSet<>();
        int additions = 0;
        int deletions = 0;

        for (JsonNode commit : commits) {
            if (commitItems.size() >= count) break;
            String sha = text(commit, "sha", "");
            String detailUrl = "https://api.github.com/repos/" + ownerRepo + "/commits/" + sha;
            JsonNode detail = githubGet(detailUrl);
            JsonNode commitNode = detail.path("commit");
            String message = firstLine(text(commitNode, "message", ""));
            String author = commitNode.path("author").path("name").asText(
                    commit.path("author").path("login").asText("Unknown"));
            String dateText = commitNode.path("author").path("date").asText("");
            Instant date = parseInstant(dateText);
            String htmlUrl = text(detail, "html_url", "https://github.com/" + ownerRepo + "/commit/" + sha);
            int commitAdd = detail.path("stats").path("additions").asInt(0);
            int commitDelete = detail.path("stats").path("deletions").asInt(0);
            int changedFiles = detail.path("files").isArray() ? detail.path("files").size() : 0;
            additions += commitAdd;
            deletions += commitDelete;
            authors.add(author);
            if (!date.equals(Instant.EPOCH)) {
                String day = LocalDate.ofInstant(date, ZoneOffset.UTC).toString();
                daily.merge(day, 1, Integer::sum);
            }

            if (detail.path("files").isArray()) {
                for (JsonNode file : detail.path("files")) {
                    String filename = text(file, "filename", "unknown");
                    FileAggregate aggregate = files.computeIfAbsent(filename, FileAggregate::new);
                    aggregate.commits++;
                    aggregate.additions += file.path("additions").asInt(0);
                    aggregate.deletions += file.path("deletions").asInt(0);
                    aggregate.lastChanged = date;
                    aggregate.lastAuthor = author;
                }
            }

            commitItems.add(new CommitItem(sha.substring(0, Math.min(8, sha.length())), message, author,
                    date, commitAdd, commitDelete, changedFiles, htmlUrl));
        }

        List<FileHistory> hotspots = files.values().stream()
                .map(FileAggregate::toHistory)
                .sorted(Comparator.comparingInt(FileHistory::churn).reversed()
                        .thenComparingInt(FileHistory::commits).reversed())
                .limit(12)
                .toList();

        List<ActivityPoint> activity = daily.entrySet().stream()
                .limit(14)
                .map(e -> new ActivityPoint(e.getKey(), e.getValue()))
                .toList();

        return new HistoryReport(projectId, projectName, ownerRepo, commitItems.size(), authors.size(), files.size(),
                additions, deletions, additions + deletions, commitItems, hotspots, activity);
    }

    private JsonNode githubGet(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Codebase-Intelligence-Engine")
                .header("X-GitHub-Api-Version", "2026-03-10")
                .GET();
        if (!githubToken.isBlank()) builder.header("Authorization", "Bearer " + githubToken);
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                if (response.statusCode() == 404) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub history could not be found for this repository.");
                }
                if (response.statusCode() == 403 || response.statusCode() == 429) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub rate limit or access restriction blocked history analysis.");
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub history request failed with HTTP " + response.statusCode() + ".");
            }
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not read GitHub history.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub history request was interrupted.");
        }
    }

    private static String extractOwnerRepo(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Git history requires a GitHub repository source.");
        }
        String normalized = sourceUrl.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.endsWith(".git")) normalized = normalized.substring(0, normalized.length() - 4);
        Matcher matcher = GITHUB_REPO.matcher(normalized);
        if (!matcher.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Git history is currently supported for GitHub repositories only.");
        }
        return matcher.group(1);
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }

    private static String firstLine(String message) {
        int newline = message.indexOf('\n');
        return newline >= 0 ? message.substring(0, newline).trim() : message.trim();
    }

    private static Instant parseInstant(String value) {
        try { return value.isBlank() ? Instant.EPOCH : Instant.parse(value); }
        catch (Exception ignored) { return Instant.EPOCH; }
    }

    public record HistoryReport(String projectId, String projectName, String repository,
                                int commitsAnalyzed, int authorCount, int filesChanged,
                                int additions, int deletions, int churn,
                                List<CommitItem> recentCommits, List<FileHistory> hotspots,
                                List<ActivityPoint> activity) {}

    public record CommitItem(String sha, String message, String author, Instant date,
                             int additions, int deletions, int filesChanged, String url) {}

    public record FileHistory(String path, int commits, int additions, int deletions,
                              int churn, String lastAuthor, Instant lastChanged) {}

    public record ActivityPoint(String date, int commits) {}

    private record CachedReport(long createdAt, HistoryReport report) {}

    private static final class FileAggregate {
        private final String path;
        private int commits;
        private int additions;
        private int deletions;
        private String lastAuthor = "Unknown";
        private Instant lastChanged = Instant.EPOCH;

        private FileAggregate(String path) { this.path = path; }

        private FileHistory toHistory() {
            return new FileHistory(path, commits, additions, deletions, additions + deletions, lastAuthor, lastChanged);
        }
    }
}
