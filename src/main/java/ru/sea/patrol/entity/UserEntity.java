package ru.sea.patrol.entity;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    private UUID id;

    private String username;

    private String password;

    private UserRole role;

    private String email;

    private boolean locked;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @ToString.Include(name = "password")
    private String maskPassword() {
        return "********";
    }
}
