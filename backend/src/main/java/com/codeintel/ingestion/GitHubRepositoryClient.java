package com.codeintel.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class GitHubRepositoryClient {
    private static final Pattern GITHUB_REPO = Pattern.compile(
            "^https://github\\.com/([^/]+/[^/]+?)(?:/)?$",
            Pattern.CASE_INSENSITIVE);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DownloadedRepository downloadPublicRepository(String repositoryUrl, Path destinationZip, RepositoryLimits limits) {
        String normalized = normalize(repositoryUrl);
        Matcher matcher = GITHUB_REPO.matcher(normalized);
        if (!matcher.matches()) {
            throw new IngestionException("Only public GitHub repository URLs are supported in this phase.");
        }

        String ownerRepo = matcher.group(1);
        JsonNode metadata = getRepositoryMetadata(ownerRepo);
        if (metadata.path("private").asBoolean(false)) {
            throw new IngestionException("Private GitHub repositories are not supported in the public demo.");
        }

        long reportedSize = metadata.path("size").asLong(-1L) * 1024L;
        if (reportedSize > limits.getMaxRepositoryBytes()) {
            throw new IngestionException("Repository is larger than the configured public-demo limit.");
        }

        String defaultBranch = metadata.path("default_branch").asText(null);
        if (defaultBranch == null || defaultBranch.isBlank()) {
            throw new IngestionException("GitHub did not provide a default branch for this repository.");
        }

        String archiveUrl = "https://codeload.github.com/" + ownerRepo + "/zip/refs/heads/" + encodePathSegment(defaultBranch);
        HttpRequest request = HttpRequest.newBuilder(URI.create(archiveUrl))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "Codebase-Intelligence-Engine")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IngestionException("GitHub archive download failed with HTTP " + response.statusCode() + ".");
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > limits.getMaxRepositoryBytes()) {
                throw new IngestionException("Repository archive exceeds the configured public-demo limit.");
            }

            Files.createDirectories(destinationZip.getParent());
            try (InputStream input = response.body()) {
                long copied = copyWithLimit(input, destinationZip, limits.getMaxRepositoryBytes());
                return new DownloadedRepository(normalized, defaultBranch, copied);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestionException("GitHub download was interrupted.", e);
        } catch (IOException e) {
            throw new IngestionException("Could not download the GitHub repository.", e);
        }
    }

    private JsonNode getRepositoryMetadata(String ownerRepo) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + ownerRepo))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Codebase-Intelligence-Engine")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IngestionException("GitHub repository could not be accessed. HTTP " + response.statusCode() + ".");
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestionException("GitHub request was interrupted.", e);
        } catch (IOException e) {
            throw new IngestionException("Could not read GitHub repository metadata.", e);
        }
    }

    private static long copyWithLimit(InputStream input, Path destination, long limit) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (var output = Files.newOutputStream(destination)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new IngestionException("Repository archive exceeds the configured public-demo limit.");
                }
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static String normalize(String repositoryUrl) {
        String normalized = repositoryUrl.trim();
        if (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.endsWith(".git")) normalized = normalized.substring(0, normalized.length() - 4);
        return normalized;
    }

    private static String encodePathSegment(String value) {
        return value.replace(" ", "%20").replace("#", "%23").replace("?", "%3F");
    }

    public record DownloadedRepository(String repositoryUrl, String defaultBranch, long archiveBytes) {}
}
