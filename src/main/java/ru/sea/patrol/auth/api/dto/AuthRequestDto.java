package ru.sea.patrol.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequestDto {

	@NotBlank
	@Size(min = 3, max = 64)
	private String username;

	@NotBlank
	@Size(max = 72)
	private String password;
}
