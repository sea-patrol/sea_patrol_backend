package ru.sea.patrol.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TokenDetails {
    private UUID userId;
    private String token;
    private Date issuedAt;
    private Date expiresAt;
}
