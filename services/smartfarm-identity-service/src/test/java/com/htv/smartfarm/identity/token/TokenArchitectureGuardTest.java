package com.htv.smartfarm.identity.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenArchitectureGuardTest {
    private static final Path SOURCE = Path.of("src/main/java");

    @Test
    void legacyFlatRefreshPersistenceMustNotReturn() throws Exception {
        List<String> violations = scan(
                "RefreshRepository",
                "RefreshJpaRepository",
                "RefreshEntity"
        );
        assertThat(violations).isEmpty();
    }

    @Test
    void businessServicesMustNotCallLegacyIssueMethods() throws Exception {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(SOURCE)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("TokenService.java"))
                    .filter(path -> !path.endsWith("TokenArchitectureGuardTest.java"))
                    .forEach(path -> {
                        try {
                            String text = Files.readString(path);
                            if (text.contains(".issueUserToken(")
                                    || text.contains(".issueServiceToken(")
                                    || text.contains(".issueDelegatedServiceToken(")) {
                                violations.add(path.toString());
                            }
                        } catch (Exception error) {
                            throw new IllegalStateException(error);
                        }
                    });
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void tokenServiceMustNotContainGrantPolicyOrRepositoryAccess() throws Exception {
        String source = Files.readString(
                SOURCE.resolve("com/htv/smartfarm/identity/token/TokenService.java")
        );

        assertThat(source)
                .doesNotContain("AuthorizationVersionService")
                .doesNotContain("issueUserToken(")
                .doesNotContain("issueServiceToken(")
                .doesNotContain("issueDelegatedServiceToken(")
                .doesNotContain("Repository")
                .doesNotContain("JpaRepository");
    }

    private List<String> scan(String... forbidden) throws Exception {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(SOURCE)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("TokenArchitectureGuardTest.java"))
                    .forEach(path -> {
                        try {
                            String text = Files.readString(path);
                            for (String value : forbidden) {
                                if (text.contains(value)) {
                                    violations.add(path + " -> " + value);
                                }
                            }
                        } catch (Exception error) {
                            throw new IllegalStateException(error);
                        }
                    });
        }
        return violations;
    }
}
