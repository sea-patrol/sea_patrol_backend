package ru.sea.patrol.auth.api.dto;

import lombok.Data;

@Data
public class AuthRequestDto {
    private String username;
    private String password;
}
