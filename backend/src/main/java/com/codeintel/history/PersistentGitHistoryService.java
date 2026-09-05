package com.codeintel.history;

import com.codeintel.project.Project;
import com.codeintel.project.ProjectRepository;
import com.codeintel.project.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PersistentGitHistoryService {
    private static final Pattern GITHUB_REPO = Pattern.compile(
            "^https://(?:www\\.)?github\\.com/([^/]+/[^/]+?)(?:/)?(?:\\.git)?$",
            Pattern.CASE_INSENSITIVE);
    private static final int DEFAULT_COMMITS = 25;
    private static final int MAX_COMMITS = 40;
    private static final int MAX_NEW_COMMITS_PER_SYNC = 40;

    private final ProjectRepository projectRepository;
    private final GitCommitRecordRepository commitRepository;
    private final GitFileChangeRecordRepository fileChangeRepository;
    private final GitHistoryStateRepository stateRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String githubToken;

    public PersistentGitHistoryService(
            ProjectRepository projectRepository,
            GitCommitRecordRepository commitRepository,
            GitFileChangeRecordRepository fileChangeRepository,
            GitHistoryStateRepository stateRepository,
            @org.springframework.beans.factory.annotation.Value("${app.github.token:}") String githubToken) {
        this.projectRepository = projectRepository;
        this.commitRepository = commitRepository;
        this.fileChangeRepository = fileChangeRepository;
        this.stateRepository = stateRepository;
        this.githubToken = githubToken == null ? "" : githubToken.trim();
    }

    @Transactional
    public PersistentHistoryReport sync(String projectId, Integer requestedCommits) {
        Project project = getProject(projectId);
        if (project.getSourceType() != SourceType.GITHUB_PUBLIC || project.getSourceUrl() == null || project.getSourceUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Persistent Git history requires a public GitHub repository source.");
        }

        String ownerRepo = extractOwnerRepo(project.getSourceUrl());
        int count = normalizeCount(requestedCommits);
        GitHistoryState state = stateRepository.findByProject_Id(projectId).orElse(null);
        String knownHead = state == null ? null : state.getLatestCommitSha();

        JsonNode commits = githubGet("https://api.github.com/repos/" + ownerRepo + "/commits?per_page=" + MAX_NEW_COMMITS_PER_SYNC);
        List<String> newShas = new ArrayList<>();
        String headSha = commits.isArray() && commits.size() > 0 ? commits.get(0).path("sha").asText("") : knownHead;

        if (commits.isArray()) {
            for (JsonNode summary : commits) {
                String sha = summary.path("sha").asText("");
                if (sha.isBlank()) continue;
                if (knownHead != null && knownHead.equals(sha)) break;
                newShas.add(sha);
                if (newShas.size() >= MAX_NEW_COMMITS_PER_SYNC) break;
            }
        }

        for (String sha : newShas) {
            if (commitRepository.findByProject_IdAndSha(projectId, sha).isPresent()) continue;
            persistCommit(project, ownerRepo, sha);
        }

        if (state == null) {
            state = new GitHistoryState();
            state.setProject(project);
        }
        state.setLatestCommitSha(headSha);
        state.setBranchName("default");
        state.setSyncedAt(Instant.now());
        state.setCommitsStored(commitRepository.countByProject_Id(projectId));
        stateRepository.save(state);

        return buildReport(project, ownerRepo, count, newShas.size(), state);
    }

    public PersistentHistoryReport getStored(String projectId, Integer requestedCommits) {
        Project project = getProject(projectId);
        String ownerRepo = extractOwnerRepo(project.getSourceUrl());
        GitHistoryState state = stateRepository.findByProject_Id(projectId).orElse(null);
        return buildReport(project, ownerRepo, normalizeCount(requestedCommits), 0, state);
    }

    private void persistCommit(Project project, String ownerRepo, String sha) {
        JsonNode detail = githubGet("https://api.github.com/repos/" + ownerRepo + "/commits/" + sha);
        JsonNode commitNode = detail.path("commit");
        GitCommitRecord record = new GitCommitRecord();
        record.setProject(project);
        record.setSha(sha);
        record.setMessage(firstLine(text(commitNode, "message", "")));
        record.setAuthor(commitNode.path("author").path("name").asText(
                detail.path("author").path("login").asText("Unknown")));
        record.setCommittedAt(parseInstant(commitNode.path("author").path("date").asText("")));
        record.setAdditions(detail.path("stats").path("additions").asInt(0));
        record.setDeletions(detail.path("stats").path("deletions").asInt(0));
        record.setChangedFiles(detail.path("files").isArray() ? detail.path("files").size() : 0);
        record.setUrl(text(detail, "html_url", "https://github.com/" + ownerRepo + "/commit/" + sha));
        record = commitRepository.save(record);

        if (detail.path("files").isArray()) {
            for (JsonNode file : detail.path("files")) {
                String path = text(file, "filename", "unknown");
                GitFileChangeRecord change = new GitFileChangeRecord();
                change.setCommit(record);
                change.setPath(path);
                change.setStatus(text(file, "status", "modified"));
                change.setAdditions(file.path("additions").asInt(0));
                change.setDeletions(file.path("deletions").asInt(0));
                fileChangeRepository.save(change);
            }
        }
    }

    private PersistentHistoryReport buildReport(Project project, String ownerRepo, int count, int newCommits,
                                                GitHistoryState state) {
        List<GitCommitRecord> records = commitRepository.findTop40ByProject_IdOrderByCommittedAtDesc(project.getId())
                .stream().limit(count).toList();
        Set<String> commitIds = records.stream().map(GitCommitRecord::getId).collect(java.util.stream.Collectors.toSet());
        List<GitFileChangeRecord> changes = commitIds.isEmpty() ? List.of() : fileChangeRepository.findByCommit_IdIn(commitIds);

        Map<String, FileAggregate> files = new HashMap<>();
        Map<String, Integer> daily = new TreeMap<>(Comparator.reverseOrder());
        Set<String> authors = new HashSet<>();
        int additions = 0;
        int deletions = 0;
        List<GitHistoryService.CommitItem> commits = new ArrayList<>();

        for (GitCommitRecord record : records) {
            authors.add(record.getAuthor());
            additions += record.getAdditions();
            deletions += record.getDeletions();
            if (record.getCommittedAt() != null) {
                daily.merge(LocalDate.ofInstant(record.getCommittedAt(), ZoneOffset.UTC).toString(), 1, Integer::sum);
            }
            commits.add(new GitHistoryService.CommitItem(
                    record.getSha().substring(0, Math.min(8, record.getSha().length())),
                    record.getMessage(), record.getAuthor(), record.getCommittedAt(),
                    record.getAdditions(), record.getDeletions(), record.getChangedFiles(), record.getUrl()));
        }

        Map<String, GitCommitRecord> byId = new HashMap<>();
        for (GitCommitRecord record : records) byId.put(record.getId(), record);
        for (GitFileChangeRecord change : changes) {
            if (!commitIds.contains(change.getCommit().getId())) continue;
            FileAggregate aggregate = files.computeIfAbsent(change.getPath(), FileAggregate::new);
            aggregate.commits++;
            aggregate.additions += change.getAdditions();
            aggregate.deletions += change.getDeletions();
            GitCommitRecord parent = byId.get(change.getCommit().getId());
            if (parent != null) {
                if (aggregate.lastChanged == null || (parent.getCommittedAt() != null && parent.getCommittedAt().isAfter(aggregate.lastChanged))) {
                    aggregate.lastChanged = parent.getCommittedAt();
                    aggregate.lastAuthor = parent.getAuthor();
                }
            }
        }

        List<GitHistoryService.FileHistory> hotspots = files.values().stream()
                .map(FileAggregate::toHistory)
                .sorted(Comparator.comparingInt(GitHistoryService.FileHistory::churn).reversed()
                        .thenComparing(Comparator.comparingInt(GitHistoryService.FileHistory::commits).reversed()))
                .limit(12)
                .toList();
        List<GitHistoryService.ActivityPoint> activity = daily.entrySet().stream()
                .limit(14)
                .map(e -> new GitHistoryService.ActivityPoint(e.getKey(), e.getValue()))
                .toList();

        long totalStored = state == null ? commitRepository.countByProject_Id(project.getId()) : state.getCommitsStored();
        Instant syncedAt = state == null ? null : state.getSyncedAt();
        String latest = state == null ? null : state.getLatestCommitSha();
        boolean persisted = totalStored > 0;
        return new PersistentHistoryReport(project.getId(), project.getName(), ownerRepo,
                records.size(), authors.size(), files.size(), additions, deletions, additions + deletions,
                commits, hotspots, activity, persisted, newCommits, totalStored, latest, syncedAt);
    }

    private Project getProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
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
                if (response.statusCode() == 404) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub repository or commit history could not be found.");
                if (response.statusCode() == 403 || response.statusCode() == 429) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub rate limit or access restriction blocked history analysis.");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub history request failed with HTTP " + response.statusCode() + ".");
            }
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException ex) { throw ex;
        } catch (IOException ex) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not read GitHub history.");
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub history request was interrupted."); }
    }

    private static int normalizeCount(Integer value) { return Math.max(5, Math.min(Optional.ofNullable(value).orElse(DEFAULT_COMMITS), MAX_COMMITS)); }

    private static String extractOwnerRepo(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Git history requires a GitHub repository source.");
        String normalized = sourceUrl.trim();
        int query = normalized.indexOf('?'); if (query >= 0) normalized = normalized.substring(0, query);
        int fragment = normalized.indexOf('#'); if (fragment >= 0) normalized = normalized.substring(0, fragment);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.endsWith(".git")) normalized = normalized.substring(0, normalized.length() - 4);
        Matcher matcher = GITHUB_REPO.matcher(normalized);
        if (!matcher.matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Git history is currently supported for GitHub repositories only.");
        return matcher.group(1);
    }

    private static String text(JsonNode node, String field, String fallback) { String value = node.path(field).asText(""); return value.isBlank() ? fallback : value; }
    private static String firstLine(String message) { int newline = message.indexOf('\n'); return newline >= 0 ? message.substring(0, newline).trim() : message.trim(); }
    private static Instant parseInstant(String value) { try { return value.isBlank() ? null : Instant.parse(value); } catch (Exception ignored) { return null; } }

    public record PersistentHistoryReport(String projectId, String projectName, String repository,
                                          int commitsAnalyzed, int authorCount, int filesChanged,
                                          int additions, int deletions, int churn,
                                          List<GitHistoryService.CommitItem> recentCommits,
                                          List<GitHistoryService.FileHistory> hotspots,
                                          List<GitHistoryService.ActivityPoint> activity,
                                          boolean persisted, int newCommits, long totalStoredCommits,
                                          String latestCommitSha, Instant lastSyncedAt) {}

    private static final class FileAggregate {
        private final String path; private int commits; private int additions; private int deletions;
        private String lastAuthor = "Unknown"; private Instant lastChanged;
        private FileAggregate(String path) { this.path = path; }
        private GitHistoryService.FileHistory toHistory() { return new GitHistoryService.FileHistory(path, commits, additions, deletions, additions + deletions, lastAuthor, lastChanged); }
    }
}
