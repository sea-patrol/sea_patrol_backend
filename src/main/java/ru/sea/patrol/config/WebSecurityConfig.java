package ru.sea.patrol.config;

import tools.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import ru.sea.patrol.auth.security.JwtAuthenticationConverter;
import ru.sea.patrol.auth.security.ReactiveSecurityManager;
import ru.sea.patrol.error.api.ApiErrorResponse;

@Slf4j
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

	private static final String ERROR_CODE_UNAUTHORIZED = "SEAPATROL_UNAUTHORIZED";
	private static final String ERROR_CODE_FORBIDDEN = "SEAPATROL_FORBIDDEN";

	private final String[] publicRoutes = {
			"/", "/game", "/assets/**", "/**.html", "/**.svg", "/**.glb",
			"/api/v1/auth/signup", "/api/v1/auth/login",
			"/sw.js", "/registerSW.js", "/manifest.webmanifest", "/workbox**"
	};

	private final ReactiveSecurityManager securityManager;
	private final JwtAuthenticationConverter bearerTokenServerAuthenticationConverter;
	private final ObjectMapper objectMapper;

	@Bean
	public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.cors(cors -> cors.configurationSource(request -> {
					var config = new CorsConfiguration();
					config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:4173"));
					config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
					config.setAllowedHeaders(List.of("*"));
					config.setAllowCredentials(true);
					config.setMaxAge(3600L);
					return config;
				}))
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers(HttpMethod.OPTIONS).permitAll()
						.pathMatchers(publicRoutes).permitAll()
						.anyExchange().authenticated())
				.exceptionHandling(exceptionHandling -> exceptionHandling
						.authenticationEntryPoint(this::handleAuthenticationError)
						.accessDeniedHandler(this::handleAccessDeniedError))
				.addFilterAt(
						bearerAuthenticationFilter(securityManager, bearerTokenServerAuthenticationConverter),
						SecurityWebFiltersOrder.AUTHENTICATION)
				.build();
	}

	private Mono<Void> handleAuthenticationError(ServerWebExchange exchange, AuthenticationException ex) {
		log.warn("security unauthorized: {}", ex.getMessage());
		return writeJsonError(exchange, HttpStatus.UNAUTHORIZED, ERROR_CODE_UNAUTHORIZED, "Unauthorized");
	}

	private Mono<Void> handleAccessDeniedError(ServerWebExchange exchange, AccessDeniedException ex) {
		log.warn("security access denied: {}", ex.getMessage());
		return writeJsonError(exchange, HttpStatus.FORBIDDEN, ERROR_CODE_FORBIDDEN, "Forbidden");
	}

	private Mono<Void> writeJsonError(ServerWebExchange exchange, HttpStatus status, String code, String message) {
		if (exchange.getResponse().isCommitted()) {
			return Mono.empty();
		}

		exchange.getResponse().setStatusCode(status);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

		byte[] bytes;
		try {
			bytes = objectMapper.writeValueAsBytes(ApiErrorResponse.of(code, message));
		} catch (Exception ex) {
			log.error("Failed to serialize security error payload", ex);
			return exchange.getResponse().setComplete();
		}

		DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
		return exchange.getResponse().writeWith(Mono.just(buffer));
	}

	private AuthenticationWebFilter bearerAuthenticationFilter(
			ReactiveSecurityManager securityManager,
			JwtAuthenticationConverter bearerTokenServerAuthenticationConverter
	) {
		AuthenticationWebFilter bearerAuthenticationFilter = new AuthenticationWebFilter(securityManager);
		bearerAuthenticationFilter.setServerAuthenticationConverter(bearerTokenServerAuthenticationConverter);
		bearerAuthenticationFilter.setAuthenticationFailureHandler((webFilterExchange, exception) ->
				handleAuthenticationError(webFilterExchange.getExchange(), exception));
		bearerAuthenticationFilter.setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.pathMatchers("/**"));

		return bearerAuthenticationFilter;
	}
}
