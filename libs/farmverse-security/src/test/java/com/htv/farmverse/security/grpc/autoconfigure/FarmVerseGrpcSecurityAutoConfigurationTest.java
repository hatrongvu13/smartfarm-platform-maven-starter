package com.htv.farmverse.security.grpc.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.htv.farmverse.security.grpc.JwtServerInterceptor;

import org.junit.jupiter.api.Test;

class FarmVerseGrpcSecurityAutoConfigurationTest {

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private static final String CANONICAL =
            "com.htv.farmverse.security.grpc.autoconfigure.FarmVerseGrpcSecurityAutoConfiguration";

    private static final String OBSOLETE =
            "com.htv.farmverse.security.autoconfigure.FarmVerseGrpcSecurityAutoConfiguration";

    @Test
    void autoConfigurationMetadataImportsOnlyCanonicalGrpcConfiguration() throws Exception {
        String content;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(IMPORTS_RESOURCE)) {
            assertThat(stream).as(IMPORTS_RESOURCE).isNotNull();
            content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        List<String> imports = content.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .toList();

        assertThat(imports).containsOnlyOnce(CANONICAL);
        assertThat(imports).doesNotContain(OBSOLETE);
    }

    @Test
    void obsoleteGrpcAutoConfigurationClassIsAbsent() {
        assertThatThrownBy(() -> Class.forName(OBSOLETE))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void canonicalConfigurationDeclaresSingleJwtInterceptorFactory() {
        long jwtInterceptorFactories = Arrays.stream(
                        FarmVerseGrpcSecurityAutoConfiguration.class.getDeclaredMethods())
                .filter(this::returnsJwtServerInterceptor)
                .count();

        assertThat(jwtInterceptorFactories).isEqualTo(1);
    }

    private boolean returnsJwtServerInterceptor(Method method) {
        return JwtServerInterceptor.class.equals(method.getReturnType());
    }
}
