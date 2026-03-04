package ru.sea.patrol.auth.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDto {

    private String username;
    private String token;
    private Date issuedAt;
    private Date expiresAt;
}
