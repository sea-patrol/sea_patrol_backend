package ru.sea.patrol.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.sea.patrol.exception.UnauthorizedException;
import ru.sea.patrol.service.UserService;

@Component
@RequiredArgsConstructor
public class AuthenticationManager implements ReactiveAuthenticationManager {

    private final UserService userService;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        CustomPrincipal principal = (CustomPrincipal) authentication.getPrincipal();
        return userService.getUserById(principal.getId())
                .filter(user -> !user.isLocked())
                .switchIfEmpty(Mono.error(new UnauthorizedException("User disabled")))
                .map(user -> authentication);
    }
}
