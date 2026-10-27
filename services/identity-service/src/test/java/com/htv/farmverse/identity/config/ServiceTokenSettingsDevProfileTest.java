package com.htv.farmverse.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Configuration;

class ServiceTokenSettingsDevProfileTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(context -> {
                        context.getEnvironment().setActiveProfiles("dev");
                        new ConfigDataApplicationContextInitializer()
                                .initialize(context);
                    })
                    .withUserConfiguration(TestConfiguration.class);

    @Test
    void loadsGatewayClientFromDevProfile() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            var binder = Binder.get(context.getEnvironment());
            assertThat(binder.bind(
                    "farmverse.identity.service-token.clients.gateway",
                    Bindable.of(ServiceTokenSettings.Client.class)
            ).isBound()).isTrue();

            ServiceTokenSettings settings = context.getBean(
                    ServiceTokenSettings.class
            );
            assertThat(settings.clients())
                    .as(
                            "service-token clients from the dev profile"
                    )
                    .containsKey("gateway")
                    .containsKeys("order", "reporting");
            assertThat(settings.clients().get("gateway").audiences())
                    .contains("farmverse-identity", "farmverse-order");
            assertThat(settings.clients().get("gateway").delegationEnabled())
                    .isTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ServiceTokenSettings.class)
    static class TestConfiguration {
    }
}
