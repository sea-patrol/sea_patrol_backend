package ru.sea.patrol.auth.security;

import io.jsonwebtoken.Claims;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationConverter implements ServerAuthenticationConverter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtUtil jwtService;

	@Override
	public Mono<Authentication> convert(ServerWebExchange exchange) {
		return extractJwtToken(exchange)
				.flatMap(jwtService::check)
				.flatMap(this::create);
	}

	private Mono<String> extractJwtToken(ServerWebExchange exchange) {
		ServerHttpRequest request = exchange.getRequest();
		String path = request.getPath().value();

		if (HttpMethod.GET.equals(request.getMethod()) && path.startsWith("/ws/")) {
			return Mono.justOrEmpty(request.getQueryParams().getFirst("token"))
					.map(JwtAuthenticationConverter::stripBearerPrefixIfPresent)
					.filter(StringUtils::hasText);
		}

		return Mono.justOrEmpty(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
				.map(String::trim)
				.filter(JwtAuthenticationConverter::startsWithBearerPrefixIgnoreCase)
				.map(JwtAuthenticationConverter::stripBearerPrefixIfPresent)
				.filter(StringUtils::hasText);
	}

	public Mono<Authentication> create(TokenVerificationResult verificationResult) {
		Claims claims = verificationResult.getClaims();
		String subject = claims.getSubject();

		String role = claims.get("role", String.class);
		List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));

		return Mono.just(new UsernamePasswordAuthenticationToken(subject, null, authorities));
	}

	private static boolean startsWithBearerPrefixIgnoreCase(String value) {
		if (value == null) {
			return false;
		}
		return value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
	}

	private static String stripBearerPrefixIfPresent(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (startsWithBearerPrefixIgnoreCase(trimmed)) {
			return trimmed.substring(BEARER_PREFIX.length()).trim();
		}
		return trimmed;
	}
}