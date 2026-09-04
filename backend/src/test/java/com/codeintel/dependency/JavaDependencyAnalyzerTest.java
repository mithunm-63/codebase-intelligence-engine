package com.codeintel.dependency;

import com.codeintel.analysis.CodeClass;
import com.codeintel.analysis.CodeClassRepository;
import com.codeintel.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaDependencyAnalyzerTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesFieldInheritanceAndMethodCallDependencies() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(source);
        Files.writeString(source.resolve("PaymentRepository.java"), """
                package com.example;
                public class PaymentRepository { }
                """);
        Files.writeString(source.resolve("UserService.java"), """
                package com.example;
                public class UserService { public void load() {} }
                """);
        Files.writeString(source.resolve("PaymentService.java"), """
                package com.example;
                import java.util.Objects;
                public class PaymentService {
                    private final PaymentRepository repository;
                    public PaymentService(PaymentRepository repository) { this.repository = repository; }
                    public void process(UserService userService) { userService.load(); repository.save(); repository.save(); }
                }
                """);
        Files.writeString(source.resolve("PaymentController.java"), """
                package com.example;
                public class PaymentController extends PaymentService {
                    public PaymentController(PaymentRepository repository) { super(repository); }
                }
                """);

        CodeClassRepository repository = Mockito.mock(CodeClassRepository.class);
        Project project = Mockito.mock(Project.class);
        Mockito.when(project.getId()).thenReturn("project-1");

        Mockito.when(repository.findAllByProject_IdOrderByQualifiedName("project-1"))
                .thenReturn(List.of(
                        codeClass("com.example.PaymentRepository", "PaymentRepository"),
                        codeClass("com.example.UserService", "UserService"),
                        codeClass("com.example.PaymentService", "PaymentService"),
                        codeClass("com.example.PaymentController", "PaymentController")
                ));

        JavaDependencyAnalyzer analyzer = new JavaDependencyAnalyzer(repository);
        DependencyAnalysisResult result = analyzer.analyze(project, tempDir.resolve("src/main/java"));

        assertTrue(result.resolvedDependencyCount() >= 4);
        assertTrue(result.dependencies().stream().anyMatch(d ->
                d.sourceQualifiedName().equals("com.example.PaymentService")
                        && d.targetQualifiedName().equals("com.example.PaymentRepository")
                        && d.type() == DependencyType.FIELD_TYPE));
        assertTrue(result.dependencies().stream().anyMatch(d ->
                d.sourceQualifiedName().equals("com.example.PaymentController")
                        && d.targetQualifiedName().equals("com.example.PaymentService")
                        && d.type() == DependencyType.EXTENDS));
        assertTrue(result.dependencies().stream().anyMatch(d ->
                d.sourceQualifiedName().equals("com.example.PaymentService")
                        && d.targetQualifiedName().equals("com.example.UserService")
                        && d.type() == DependencyType.METHOD_CALL));
        assertTrue(result.dependencies().stream().anyMatch(d ->
                d.sourceQualifiedName().equals("com.example.PaymentService")
                        && d.targetQualifiedName().equals("com.example.PaymentRepository")
                        && d.type() == DependencyType.METHOD_CALL
                        && d.occurrenceCount() == 2));
    }

    private static CodeClass codeClass(String qualifiedName, String name) {
        CodeClass codeClass = new CodeClass();
        codeClass.setQualifiedName(qualifiedName);
        codeClass.setName(name);
        codeClass.setKind("CLASS");
        return codeClass;
    }
}
