package ru.sea.patrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.sea.patrol.dto.AuthRequestDto;
import ru.sea.patrol.dto.AuthResponseDto;
import ru.sea.patrol.dto.UserDto;
import ru.sea.patrol.dto.UserRegistrationDto;
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
}
