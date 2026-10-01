package com.htv.smartfarm.security.jwt;

import com.htv.smartfarm.security.config.SmartFarmSecurityProperties;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

public final class JwtSecurityFactory {

    private JwtSecurityFactory() {
    }

    public static JwtDecoder servletDecoder(
            SmartFarmSecurityProperties.Jwt properties
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.jwkSetUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        decoder.setJwtValidator(
                new SmartFarmJwtValidator(properties)
        );

        return decoder;
    }

    public static ReactiveJwtDecoder reactiveDecoder(
            SmartFarmSecurityProperties.Jwt properties
    ) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(properties.jwkSetUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        decoder.setJwtValidator(
                new SmartFarmJwtValidator(properties)
        );

        return decoder;
    }
}
