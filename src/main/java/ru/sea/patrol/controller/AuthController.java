package ru.sea.patrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.sea.patrol.dto.AuthRequestDto;
import ru.sea.patrol.dto.AuthResponseDto;
import ru.sea.patrol.dto.UserDto;
import ru.sea.patrol.dto.UserRegistrationDto;
import ru.sea.patrol.security.CustomPrincipal;
import ru.sea.patrol.security.SecurityService;
import ru.sea.patrol.service.UserService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SecurityService securityService;
    private final UserService userService;

    @PostMapping("/signup")
    public Mono<UserDto> signup(@RequestBody UserRegistrationDto dto) {
        return userService.signup(dto);
    }

    @PostMapping("/login")
    public Mono<AuthResponseDto> login(@RequestBody AuthRequestDto dto) {
        return securityService.authenticate(dto.getUsername(), dto.getPassword())
                .flatMap(tokenDetails -> Mono.just(
                        AuthResponseDto.builder()
                                .userId(tokenDetails.getUserId())
                                .username(dto.getUsername())
                                .token(tokenDetails.getToken())
                                .issuedAt(tokenDetails.getIssuedAt())
                                .expiresAt(tokenDetails.getExpiresAt())
                                .build()
                ));
    }

    @GetMapping("/info")
    public Mono<UserDto> getUserInfo(Authentication authentication) {
        CustomPrincipal customPrincipal = (CustomPrincipal) authentication.getPrincipal();

        return userService.getUserById(customPrincipal.getId());
    }
}
