package ru.sea.patrol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import ru.sea.patrol.entity.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserDto {
    private UUID id;
    private String username;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private UserRole role;
    private String email;
    private boolean locked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
