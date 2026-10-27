package com.htv.farmverse.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransportArchitectureGuardTest {
    private static final Path SOURCE = Path.of("src/main/java");
    private static final List<String> TRANSPORT_ANNOTATIONS = List.of(
            "@RestController",
            "@GrpcService"
    );

    @Test
    void httpAndGrpcAdaptersMustNotDependOnPersistenceRepositories()
            throws Exception {
        List<String> adapters = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(SOURCE)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            if (TRANSPORT_ANNOTATIONS.stream()
                                    .noneMatch(source::contains)) {
                                return;
                            }
                            adapters.add(path.toString());
                            if (source.contains("Repository")
                                    || source.contains("JpaRepository")) {
                                violations.add(path.toString());
                            }
                        } catch (Exception error) {
                            throw new IllegalStateException(error);
                        }
                    });
        }

        assertThat(adapters).isNotEmpty();
        assertThat(violations).isEmpty();
    }
}
