package com.htv.smartfarm.inventory.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Production-safety guard (refactor §6 / §E): fails the build if the production profile could
 * ever execute DESTRUCTIVE Hibernate schema generation. Reads the actual packaged
 * application-prod.yml and asserts ddl-auto is 'validate' and never create / create-drop /
 * update. An application restart must NEVER drop or recreate the production schema.
 */
class ProdSchemaSafetyTest {

    private String prodConfig() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/application-prod.yml")) {
            assertThat(in).as("application-prod.yml must exist on the classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void prodDdlAutoIsValidateAndNeverDestructive() throws Exception {
        String cfg = prodConfig();
        assertThat(cfg).contains("ddl-auto: validate");
        assertThat(cfg).doesNotContain("ddl-auto: create");
        assertThat(cfg).doesNotContain("ddl-auto: create-drop");
        assertThat(cfg).doesNotContain("ddl-auto: update");
    }

    @Test
    void prodDoesNotEnableHibernateSchemaGenerationAlternatives() throws Exception {
        String cfg = prodConfig();
        assertThat(cfg).doesNotContain("hbm2ddl.auto: create");
        assertThat(cfg).doesNotContain("generate-ddl: true");
    }
}
