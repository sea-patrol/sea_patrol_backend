package ru.sea.patrol.auth.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
		String secret,
		String secretBase64,
		@NotNull Integer expiration,
		@NotBlank String issuer
) {
}