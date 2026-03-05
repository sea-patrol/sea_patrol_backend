package ru.sea.patrol.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationDto {

	@NotBlank
	@Size(min = 3, max = 64)
	private String username;

	@NotBlank
	@Size(min = 6, max = 72)
	private String password;

	@NotBlank
	@Email
	private String email;
}
