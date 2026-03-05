package ru.sea.patrol.auth.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationConverter implements ServerAuthenticationConverter {

    private final JwtUtil jwtService;
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return extractJwtToken(exchange)
                .flatMap(jwtService::check)
                .flatMap(this::create);
    }

    private Mono<String> extractJwtToken(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getMethod() == HttpMethod.GET && request.getURI().getPath().startsWith("/ws/")) {
            return Mono.justOrEmpty(request.getQueryParams().getFirst("token"));
        }

        return Mono.justOrEmpty(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(value -> value.startsWith(BEARER_PREFIX))
                .map(value -> value.substring(BEARER_PREFIX.length()));
    }

    public Mono<Authentication> create(TokenVerificationResult verificationResult) {
        Claims claims = verificationResult.getClaims();
        String subject = claims.getSubject();

        String role = claims.get("role", String.class);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));

        return Mono.justOrEmpty(new UsernamePasswordAuthenticationToken(subject, null, authorities));
    }
}
