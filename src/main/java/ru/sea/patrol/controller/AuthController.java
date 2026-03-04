package ru.sea.patrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import ru.sea.patrol.dto.auth.AuthRequestDto;
import ru.sea.patrol.dto.auth.AuthResponseDto;
import ru.sea.patrol.dto.auth.UserDto;
import ru.sea.patrol.dto.auth.UserRegistrationDto;
import ru.sea.patrol.security.ReactiveSecurityManager;
import ru.sea.patrol.service.UserService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final ReactiveSecurityManager reactiveSecurityManager;
    private final UserService userService;

    @PostMapping("/signup")
    public Mono<UserDto> signup(@RequestBody UserRegistrationDto dto) {
        return userService.create(dto);
    }

    @PostMapping("/login")
    public Mono<AuthResponseDto> login(@RequestBody AuthRequestDto dto) {
        return reactiveSecurityManager.login(dto.getUsername(), dto.getPassword())
                .flatMap(tokenDetails -> Mono.just(
                        AuthResponseDto.builder()
                                .username(dto.getUsername())
                                .token(tokenDetails.getToken())
                                .issuedAt(tokenDetails.getIssuedAt())
                                .expiresAt(tokenDetails.getExpiresAt())
                                .build()
                ));
    }

    @GetMapping("/me")
    public Mono<UserDto> me() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(authentication -> {
                    var dto = new UserDto();
                    dto.setUsername(authentication.getName());
                    return dto;
                });
    }
}
