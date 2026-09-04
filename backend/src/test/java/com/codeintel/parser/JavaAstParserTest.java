package com.codeintel.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeintel.parser.model.AstAnalysisResult;
import com.codeintel.parser.model.ParsedType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

class JavaAstParserTest {
    @Test
    void extractsTypesFieldsMethodsConstructorsAndImports() throws Exception {
        Path root = Files.createTempDirectory("cie-ast-test");
        Path file = root.resolve("src/main/java/com/example/PaymentService.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                package com.example;

                import java.util.List;
                import static java.util.Collections.emptyList;

                @Service
                public class PaymentService {
                    private final PaymentRepository repository;

                    public PaymentService(PaymentRepository repository) {
                        this.repository = repository;
                    }

                    public Payment process(Order order) {
                        return repository.save(order);
                    }
                }
                """);

        AstAnalysisResult result = new JavaAstParser().analyze(root);

        assertThat(result.classCount()).isEqualTo(1);
        assertThat(result.methodCount()).isEqualTo(1);
        assertThat(result.constructorCount()).isEqualTo(1);
        assertThat(result.fieldCount()).isEqualTo(1);
        assertThat(result.importCount()).isEqualTo(2);
        assertThat(result.parseErrorCount()).isZero();

        ParsedType type = result.types().get(0);
        assertThat(type.qualifiedName()).isEqualTo("com.example.PaymentService");
        assertThat(type.annotations()).contains("Service");
        assertThat(type.fields().get(0).name()).isEqualTo("repository");
        assertThat(type.methods()).extracting("name").containsExactly("PaymentService", "process");
    }

    @Test
    void reportsBadJavaWithoutFailingTheWholeScan() throws Exception {
        Path root = Files.createTempDirectory("cie-ast-bad-test");
        Path file = root.resolve("Broken.java");
        Files.writeString(file, "class Broken { public void broken( {");

        AstAnalysisResult result = new JavaAstParser().analyze(root);

        assertThat(result.parseErrorCount()).isEqualTo(1);
        assertThat(result.types()).isEmpty();
        assertThat(result.parseErrors().get(0)).contains("Broken.java");
    }
}
