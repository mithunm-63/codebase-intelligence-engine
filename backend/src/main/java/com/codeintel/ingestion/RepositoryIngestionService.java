package com.codeintel.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.codeintel.api.dto.IngestionResponse;
import com.codeintel.analysis.AstAnalysisService;
import com.codeintel.analysis.CodeClassRepository;
import com.codeintel.analysis.DependencyAnalysisService;
import com.codeintel.dependency.DependencyAnalysisResult;
import com.codeintel.parser.model.AstAnalysisResult;
import com.codeintel.project.Project;
import com.codeintel.project.ProjectRepository;
import com.codeintel.project.ProjectStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RepositoryIngestionService {
    private static final int SAMPLE_LIMIT = 20;

    private final ProjectRepository projectRepository;
    private final GitHubRepositoryClient gitHubRepositoryClient;
    private final RepositoryLimits limits;
    private final AstAnalysisService astAnalysisService;
    private final DependencyAnalysisService dependencyAnalysisService;
    private final CodeClassRepository codeClassRepository;

    public RepositoryIngestionService(ProjectRepository projectRepository,
                                      GitHubRepositoryClient gitHubRepositoryClient,
                                      RepositoryLimits limits,
                                      AstAnalysisService astAnalysisService,
                                      DependencyAnalysisService dependencyAnalysisService,
                                      CodeClassRepository codeClassRepository) {
        this.projectRepository = projectRepository;
        this.gitHubRepositoryClient = gitHubRepositoryClient;
        this.limits = limits;
        this.astAnalysisService = astAnalysisService;
        this.dependencyAnalysisService = dependencyAnalysisService;
        this.codeClassRepository = codeClassRepository;
    }

    public IngestionResponse ingestZip(String projectId, MultipartFile file) {
        Project project = requireProject(projectId);
        if (file == null || file.isEmpty()) {
            throw new IngestionException("ZIP upload is empty.");
        }
        validateUploadSize(file.getSize());
        project.setStatus(ProjectStatus.INGESTING);
        project.setErrorMessage(null);
        projectRepository.save(project);

        Path root = null;
        Path zip = null;
        try {
            root = createWorkspace(projectId);
            zip = root.resolve("repository.zip");
            file.transferTo(zip);
            IngestionResponse response = extractAndScan(project, zip, root.resolve("source"));
            projectRepository.save(project);
            return response;
        } catch (Exception e) {
            markFailed(project, e);
            if (e instanceof IngestionException ingestionException) throw ingestionException;
            throw new IngestionException("Could not ingest the ZIP repository.", e);
        } finally {
            deleteRecursively(root);
        }
    }

    public IngestionResponse ingestGitHub(String projectId, String repositoryUrl) {
        Project project = requireProject(projectId);
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new IngestionException("GitHub repository URL is required.");
        }
        project.setStatus(ProjectStatus.INGESTING);
        project.setSourceUrl(repositoryUrl.trim());
        project.setErrorMessage(null);
        projectRepository.save(project);

        Path root = null;
        try {
            root = createWorkspace(projectId);
            Path zip = root.resolve("repository.zip");
            GitHubRepositoryClient.DownloadedRepository downloaded =
                    gitHubRepositoryClient.downloadPublicRepository(repositoryUrl, zip, limits);
            IngestionResponse response = extractAndScan(project, zip, root.resolve("source"));
            project.setSourceUrl(downloaded.repositoryUrl());
            projectRepository.save(project);
            return response;
        } catch (Exception e) {
            markFailed(project, e);
            if (e instanceof IngestionException ingestionException) throw ingestionException;
            throw new IngestionException("Could not ingest the GitHub repository.", e);
        } finally {
            deleteRecursively(root);
        }
    }

    private IngestionResponse extractAndScan(Project project, Path zip, Path target) throws IOException {
        Files.createDirectories(target);
        ScanResult scan = extractZip(zip, target);
        project.setRepositorySizeBytes(scan.uncompressedBytes());
        project.setTotalFiles(scan.totalFiles());
        project.setJavaFiles(scan.javaFiles());
        project.setMainJavaFiles(scan.mainJavaFiles());
        project.setTestJavaFiles(scan.testJavaFiles());

        AstAnalysisResult ast = astAnalysisService.analyze(project, target);
        DependencyAnalysisResult dependencies = dependencyAnalysisService.analyze(project, target);
        project.setStatus(ProjectStatus.READY);
        projectRepository.save(project);
        var indexedClasses = codeClassRepository.findAllByProject_IdOrderByQualifiedName(project.getId()).stream()
                .limit(50).toList();
        return new IngestionResponse(
                project.getId(), project.getStatus(), scan.uncompressedBytes(), scan.totalFiles(),
                scan.javaFiles(), scan.mainJavaFiles(), scan.testJavaFiles(), scan.sampleFiles(),
                ast.classCount(), ast.interfaceCount(), ast.enumCount(), ast.recordCount(),
                ast.annotationCount(), ast.methodCount(), ast.constructorCount(), ast.fieldCount(), ast.importCount(),
                dependencies.resolvedDependencyCount(), dependencies.dependencyOccurrences(), dependencies.unresolvedReferenceCount(),
                ast.parseErrorCount(), ast.parseErrors(),
                indexedClasses.stream().map(c -> c.getQualifiedName()).toList(),
                indexedClasses.stream().map(c -> c.getId()).toList());
    }

    private ScanResult extractZip(Path zip, Path target) throws IOException {
        long uncompressedBytes = 0;
        int totalFiles = 0;
        int javaFiles = 0;
        int mainJavaFiles = 0;
        int testJavaFiles = 0;
        List<String> sampleFiles = new ArrayList<>();

        try (InputStream raw = Files.newInputStream(zip);
             ZipInputStream zis = new ZipInputStream(raw)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = normalizeZipEntry(entry.getName());
                if (entryName.isBlank() || entry.isDirectory() || shouldIgnore(entryName)) {
                    zis.closeEntry();
                    continue;
                }

                if (isUnsafePath(entryName)) {
                    throw new IngestionException("Archive contains an unsafe path.");
                }

                totalFiles++;
                if (totalFiles > limits.getMaxFiles()) {
                    throw new IngestionException("Repository contains more files than the configured limit.");
                }

                Path output = target.resolve(entryName).normalize();
                if (!output.startsWith(target)) {
                    throw new IngestionException("Archive contains an unsafe path.");
                }
                Files.createDirectories(output.getParent());

                try (var out = Files.newOutputStream(output)) {
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        uncompressedBytes += read;
                        if (uncompressedBytes > limits.getMaxRepositoryBytes()) {
                            throw new IngestionException("Repository content exceeds the configured public-demo limit.");
                        }
                        out.write(buffer, 0, read);
                    }
                }

                if (entryName.toLowerCase(Locale.ROOT).endsWith(".java")) {
                    javaFiles++;
                    String lower = "/" + entryName.toLowerCase(Locale.ROOT);
                    if (lower.contains("/src/main/java/")) mainJavaFiles++;
                    if (lower.contains("/src/test/java/") || lower.contains("/src/test/")) testJavaFiles++;
                    if (javaFiles > limits.getMaxJavaFiles()) {
                        throw new IngestionException("Repository contains more Java files than the configured limit.");
                    }
                }

                if (sampleFiles.size() < SAMPLE_LIMIT) sampleFiles.add(entryName);
                zis.closeEntry();
            }
        }

        return new ScanResult(uncompressedBytes, totalFiles, javaFiles, mainJavaFiles, testJavaFiles, sampleFiles);
    }

    private void validateUploadSize(long size) {
        if (size > limits.getMaxRepositoryBytes()) {
            throw new IngestionException("ZIP upload exceeds the configured repository limit.");
        }
    }

    private Project requireProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IngestionException("Project not found: " + projectId));
    }

    private void markFailed(Project project, Exception e) {
        project.setStatus(ProjectStatus.FAILED);
        String message = e.getMessage();
        project.setErrorMessage(message == null ? "Repository ingestion failed." : message);
        projectRepository.save(project);
    }

    private Path createWorkspace(String projectId) throws IOException {
        return Files.createTempDirectory("codeintel-" + projectId + "-");
    }

    private static String normalizeZipEntry(String entryName) {
        return entryName.replace('\\', '/').replaceAll("^\\./+", "");
    }

    private static boolean isUnsafePath(String entryName) {
        if (entryName.startsWith("/")) return true;
        for (String segment : entryName.split("/")) {
            if (segment.equals("..") || segment.equals(".")) return true;
        }
        return false;
    }

    private static boolean shouldIgnore(String entryName) {
        String lower = entryName.toLowerCase(Locale.ROOT);
        return lower.startsWith(".git/")
                || lower.contains("/.git/")
                || lower.startsWith("target/")
                || lower.contains("/target/")
                || lower.startsWith("node_modules/")
                || lower.contains("/node_modules/")
                || lower.startsWith(".idea/")
                || lower.contains("/.idea/");
    }

    private static void deleteRecursively(Path root) {
        if (root == null) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private record ScanResult(long uncompressedBytes, int totalFiles, int javaFiles, int mainJavaFiles,
                              int testJavaFiles, List<String> sampleFiles) {}
}
