package com.codeintel.ingestion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.codeintel.project.Project;
import com.codeintel.project.ProjectRepository;
import com.codeintel.project.ProjectStatus;
import com.codeintel.analysis.AstAnalysisService;
import com.codeintel.analysis.CodeClassRepository;
import com.codeintel.analysis.DependencyAnalysisService;
import com.codeintel.graph.ArchitectureGraphService;
import com.codeintel.dependency.DependencyAnalysisResult;
import com.codeintel.parser.model.AstAnalysisResult;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class RepositoryIngestionServiceTest {

    @Test
    void rejectsUnsafeZipPaths() throws Exception {
        Project project = project();
        ProjectRepository repository = repository(project);
        RepositoryIngestionService service = newService(repository);

        MockMultipartFile multipart = new MockMultipartFile(
                "file", "repo.zip", "application/zip", zipWithEntry("../evil.txt", "blocked"));

        assertThatThrownBy(() -> service.ingestZip("project-1", multipart))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("unsafe path");
    }

    @Test
    void countsJavaFilesAndMainAndTestRoots() throws Exception {
        Project project = project();
        ProjectRepository repository = repository(project);
        RepositoryIngestionService service = newService(repository);

        MockMultipartFile multipart = new MockMultipartFile(
                "file", "repo.zip", "application/zip", multiEntryZip());

        var response = service.ingestZip("project-1", multipart);

        Assertions.assertThat(response.totalFiles()).isEqualTo(3);
        Assertions.assertThat(response.javaFiles()).isEqualTo(3);
        Assertions.assertThat(response.mainJavaFiles()).isEqualTo(2);
        Assertions.assertThat(response.testJavaFiles()).isEqualTo(1);
        Assertions.assertThat(response.status()).isEqualTo(ProjectStatus.READY);
    }

    private static Project project() {
        Project project = new Project();
        project.setName("test-project");
        project.setStatus(ProjectStatus.CREATED);
        return project;
    }

    private static ProjectRepository repository(Project project) {
        ProjectRepository repository = mock(ProjectRepository.class);
        when(repository.findById(any())).thenReturn(Optional.of(project));
        when(repository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return repository;
    }

    private static RepositoryIngestionService newService(ProjectRepository repository) {
        RepositoryLimits limits = new RepositoryLimits(true, 1, 100, 100);
        AstAnalysisService astService = mock(AstAnalysisService.class);
        try {
            when(astService.analyze(any(Project.class), any())).thenReturn(
                    new AstAnalysisResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, java.util.List.of(),
                            java.util.List.of(), java.util.List.of()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        DependencyAnalysisService dependencyService = mock(DependencyAnalysisService.class);
        ArchitectureGraphService graphService = mock(ArchitectureGraphService.class);
        try {
            when(dependencyService.analyze(any(Project.class), any())).thenReturn(
                    new DependencyAnalysisResult(0, 0, 0, java.util.Map.of(), java.util.List.of(), java.util.List.of()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new RepositoryIngestionService(repository, mock(GitHubRepositoryClient.class), limits, astService, dependencyService, mock(CodeClassRepository.class), graphService);
    }

    private static byte[] zipWithEntry(String name, String contents) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(contents.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static byte[] multiEntryZip() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            write(zip, "demo/src/main/java/com/example/A.java", "class A {}");
            write(zip, "demo/src/main/java/com/example/B.java", "class B {}");
            write(zip, "demo/src/test/java/com/example/ATest.java", "class ATest {}");
        }
        return bytes.toByteArray();
    }

    private static void write(ZipOutputStream zip, String name, String contents) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
