package com.htv.farmverse.security.jwt;

import com.htv.farmverse.security.config.FarmVerseSecurityProperties;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

public final class JwtSecurityFactory {

    private JwtSecurityFactory() {
    }

    public static JwtDecoder servletDecoder(
            FarmVerseSecurityProperties.Jwt properties
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.jwkSetUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        decoder.setJwtValidator(
                new FarmVerseJwtValidator(properties)
        );

        return decoder;
    }

    public static ReactiveJwtDecoder reactiveDecoder(
            FarmVerseSecurityProperties.Jwt properties
    ) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(properties.jwkSetUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        decoder.setJwtValidator(
                new FarmVerseJwtValidator(properties)
        );

        return decoder;
    }
}
