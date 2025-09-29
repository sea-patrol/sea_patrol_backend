package ru.sea.patrol.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
public class AuthRequestDto {
    private String username;
    private String password;
}
