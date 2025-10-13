package ru.sea.patrol.security;

import io.jsonwebtoken.Claims;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenVerificationResult {
    private Claims claims;
    private String token;
}
