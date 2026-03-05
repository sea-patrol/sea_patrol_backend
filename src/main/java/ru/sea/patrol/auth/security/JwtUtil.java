package ru.sea.patrol.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import ru.sea.patrol.user.domain.UserEntity;

@Slf4j
@Component
public class JwtUtil {

	private static final int MIN_HS256_KEY_BYTES = 32;

	private final SecretKey signingKey;
	private final int expirationInSeconds;
	private final String issuer;

	public JwtUtil(JwtProperties properties) {
		this.expirationInSeconds = properties.expiration();
		this.issuer = properties.issuer();
		this.signingKey = buildSigningKey(properties);
	}

	public TokenDetails generateToken(UserEntity user) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("role", user.getRole().name());
		return generateToken(claims, user.getUsername());
	}

	public TokenDetails generateToken(Map<String, Object> claims, String subject) {
		long expirationTimeInMillis = expirationInSeconds * 1000L;
		Date expirationDate = new Date(System.currentTimeMillis() + expirationTimeInMillis);

		return generateToken(expirationDate, claims, subject);
	}

	public TokenDetails generateToken(Date expirationDate, Map<String, Object> claims, String subject) {
		Date createdDate = new Date();
		String token = Jwts.builder()
				.claims(claims)
				.issuer(issuer)
				.subject(subject)
				.issuedAt(createdDate)
				.id(UUID.randomUUID().toString())
				.expiration(expirationDate)
				.signWith(signingKey)
				.compact();

		return TokenDetails.builder()
				.token(token)
				.issuedAt(createdDate)
				.expiresAt(expirationDate)
				.build();
	}

	public Mono<TokenVerificationResult> check(String accessToken) {
		return Mono.defer(() -> {
			try {
				return Mono.just(verify(accessToken));
			} catch (AuthenticationException ex) {
				return Mono.error(ex);
			} catch (Exception ex) {
				return Mono.error(new BadCredentialsException("Invalid JWT", ex));
			}
		});
	}

	private TokenVerificationResult verify(String token) {
		if (!StringUtils.hasText(token)) {
			throw new BadCredentialsException("Missing JWT");
		}

		Claims claims = getClaimsFromToken(token);
		Date expirationDate = claims.getExpiration();
		if (expirationDate != null && expirationDate.before(new Date())) {
			throw new CredentialsExpiredException("Token expired");
		}

		return new TokenVerificationResult(claims, token);
	}

	private Claims getClaimsFromToken(String token) {
		try {
			return Jwts.parser()
					.verifyWith(signingKey)
					.build()
					.parseSignedClaims(token)
					.getPayload();
		} catch (ExpiredJwtException ex) {
			throw new CredentialsExpiredException("Token expired", ex);
		} catch (Exception ex) {
			throw new BadCredentialsException("Invalid JWT", ex);
		}
	}

	private static SecretKey buildSigningKey(JwtProperties properties) {
		String rawSecret = properties.secret();
		String base64Secret = properties.secretBase64();

		byte[] keyBytes;
		if (StringUtils.hasText(base64Secret)) {
			keyBytes = decodeBase64Flexible(base64Secret.trim());
		} else if (StringUtils.hasText(rawSecret)) {
			keyBytes = rawSecret.getBytes(StandardCharsets.UTF_8);
		} else {
			throw new IllegalStateException(
					"JWT secret is not configured. Set env JWT_SECRET (raw) or JWT_SECRET_BASE64 (base64/base64url)."
			);
		}

		if (keyBytes.length < MIN_HS256_KEY_BYTES) {
			throw new IllegalStateException(
					"JWT secret is too short for HS256: need at least %d bytes, got %d".formatted(MIN_HS256_KEY_BYTES, keyBytes.length)
			);
		}

		return Keys.hmacShaKeyFor(keyBytes);
	}

	private static byte[] decodeBase64Flexible(String value) {
		try {
			return Decoders.BASE64.decode(value);
		} catch (IllegalArgumentException ex) {
			return Decoders.BASE64URL.decode(value);
		}
	}
}
