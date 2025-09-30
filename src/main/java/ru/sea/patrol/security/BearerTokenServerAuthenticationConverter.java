package ru.sea.patrol.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@RequiredArgsConstructor
public class BearerTokenServerAuthenticationConverter implements ServerAuthenticationConverter {

    private final JwtHandler jwtHandler;
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Function<String, Mono<String>> getBearerValue = authValue -> Mono.justOrEmpty(authValue.substring(BEARER_PREFIX.length()));

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return extractJwtToken(exchange)
                .flatMap(getBearerValue)
                .flatMap(jwtHandler::check)
                .flatMap(UserAuthenticationBearer::create);
    }

    private Mono<String> extractJwtToken(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getMethod() == HttpMethod.GET && request.getURI().getPath().startsWith("/ws/")) {
            return Mono.justOrEmpty(BEARER_PREFIX + request.getQueryParams().getFirst("token"));
        } else {
            return Mono.justOrEmpty(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        }
    }
}
