package com.scaler.productcatalogservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

// The rules that decide if a token is accepted: who made it, who it is for, and when it ends.
class SecurityConfigTest {

    private final OAuth2TokenValidator<Jwt> validator = SecurityConfig.tokenValidator("userauthservice", "shopnest-api");

    private Jwt token(String issuer, List<String> audience, Instant expiresAt) {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer(issuer)
                .audience(audience)
                .subject("7")
                .issuedAt(expiresAt.minusSeconds(900))
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    void validate_rightIssuerAudienceAndTime_isAccepted() {
        Jwt jwt = token("userauthservice", List.of("shopnest-api"), Instant.now().plusSeconds(600));

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void validate_tokenMadeForAnotherApi_isRefused() {
        Jwt jwt = token("userauthservice", List.of("payments-api"), Instant.now().plusSeconds(600));

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void validate_tokenFromAnotherIssuer_isRefused() {
        Jwt jwt = token("someone-else", List.of("shopnest-api"), Instant.now().plusSeconds(600));

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void validate_tokenThatEndedTwoMinutesAgo_isRefused() {
        Jwt jwt = token("userauthservice", List.of("shopnest-api"), Instant.now().minusSeconds(120));

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void validate_tokenThatEndedTwentySecondsAgo_isStillAcceptedBecauseOfTheDefaultClockGap() {
        Jwt jwt = token("userauthservice", List.of("shopnest-api"), Instant.now().minusSeconds(20));

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }
}
