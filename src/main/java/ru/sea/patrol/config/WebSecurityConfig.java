package ru.sea.patrol.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.sea.patrol.security.JwtAuthenticationConverter;
import ru.sea.patrol.security.ReactiveSecurityManager;

import java.util.List;

@Slf4j
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    @Value("${jwt.secret}")
    private String secret;

    private final String [] publicRoutes = {
            "/", "/game", "/assets/**", "/**.html","/**.svg", "/**.glb",
            "/api/v1/auth/signup", "/api/v1/auth/login",
            "/sw.js", "/registerSW.js", "/manifest.webmanifest", "/workbox**"};

    private final ReactiveSecurityManager securityManager;
    private final JwtAuthenticationConverter bearerTokenServerAuthenticationConverter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(request -> {
                    var config = new CorsConfiguration();
                    config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:4173")); // Разрешаем только этот домен
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("*"));
                    config.setAllowCredentials(true); // Если нужно передавать cookies
                    config.setMaxAge(3600L); // Время жизни предварительного запроса (preflight)
                    return config;
                }))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll() // Разрешаем OPTIONS запросы
                        .pathMatchers(publicRoutes).permitAll() // Публичные маршруты
                        .anyExchange().authenticated() // Все остальные маршруты требуют аутентификации
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(this::handleAuthenticationError) // Обработка ошибок аутентификации
                        .accessDeniedHandler(this::handleAccessDeniedError) // Обработка ошибок доступа
                )
                .addFilterAt(
                        bearerAuthenticationFilter(securityManager, bearerTokenServerAuthenticationConverter),
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    private Mono<Void> handleAuthenticationError(ServerWebExchange exchange, AuthenticationException ex) {
        log.error("IN securityWebFilterChain - unauthorized error: {}", ex.getMessage());
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> handleAccessDeniedError(ServerWebExchange exchange, AccessDeniedException ex) {
        log.error("IN securityWebFilterChain - access denied: {}", ex.getMessage());
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    private AuthenticationWebFilter bearerAuthenticationFilter(
            ReactiveSecurityManager securityManager,
           JwtAuthenticationConverter bearerTokenServerAuthenticationConverter) {
        AuthenticationWebFilter bearerAuthenticationFilter = new AuthenticationWebFilter(securityManager);
        bearerAuthenticationFilter.setServerAuthenticationConverter(bearerTokenServerAuthenticationConverter);
        bearerAuthenticationFilter.setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.pathMatchers("/**"));

        return bearerAuthenticationFilter;
    }
}
