package ru.sea.patrol.auth.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.sea.patrol.auth.api.dto.AuthRequestDto;
import ru.sea.patrol.auth.api.dto.AuthResponseDto;
import ru.sea.patrol.auth.api.dto.UserDto;
import ru.sea.patrol.auth.api.dto.UserRegistrationDto;
import ru.sea.patrol.auth.security.ReactiveSecurityManager;
import ru.sea.patrol.user.service.UserService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final ReactiveSecurityManager reactiveSecurityManager;
	private final UserService userService;

	@PostMapping("/signup")
	public Mono<UserDto> signup(@Valid @RequestBody UserRegistrationDto dto) {
		return userService.create(dto);
	}

	@PostMapping("/login")
	public Mono<AuthResponseDto> login(@Valid @RequestBody AuthRequestDto dto) {
		return reactiveSecurityManager.login(dto.getUsername(), dto.getPassword())
				.map(tokenDetails -> AuthResponseDto.builder()
						.username(dto.getUsername())
						.token(tokenDetails.getToken())
						.issuedAt(tokenDetails.getIssuedAt())
						.expiresAt(tokenDetails.getExpiresAt())
						.build());
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
