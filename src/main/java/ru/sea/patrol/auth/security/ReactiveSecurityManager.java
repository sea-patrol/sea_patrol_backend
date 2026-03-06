package ru.sea.patrol.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.sea.patrol.error.domain.AuthException;
import ru.sea.patrol.error.domain.UnauthorizedException;
import ru.sea.patrol.service.session.GameSessionRegistry;
import ru.sea.patrol.user.service.UserService;

@Component
@RequiredArgsConstructor
public class ReactiveSecurityManager implements ReactiveAuthenticationManager {

    private final JwtUtil jwtService;
    private final UserService userService;
    private final CustomPasswordEncoder passwordEncoder;
    private final GameSessionRegistry sessionRegistry;

    public Mono<TokenDetails> login(String username, String password) {
        return userService.retrieve(username)
                .flatMap(user -> {
                    if (user.isLocked()) {
                        return Mono.error(new AuthException("Account disabled", "SEAPATROL_USER_ACCOUNT_DISABLED"));
                    }

                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        return Mono.error(new AuthException("Invalid password", "SEAPATROL_INVALID_PASSWORD"));
                    }

                    if (!sessionRegistry.isLoginAllowed(username)) {
                        return Mono.error(new AuthException(
                                GameSessionRegistry.DUPLICATE_SESSION_MESSAGE,
                                GameSessionRegistry.DUPLICATE_SESSION_ERROR_CODE
                        ));
                    }

                    return Mono.just(jwtService.generateToken(user).toBuilder()
                            .username(username)
                            .build());
                })
                .switchIfEmpty(Mono.error(new AuthException("Invalid username", "SEAPATROL_INVALID_USERNAME")));
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return userService.retrieve(authentication.getName())
                .filter(user -> !user.isLocked())
                .switchIfEmpty(Mono.error(new UnauthorizedException("User disabled")))
                .map(user -> authentication);
    }
}
