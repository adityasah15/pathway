package com.tcet.RestApi.dto;

public record AuthResponseDTO(
        String token,
        String tokenType,
        long expiresIn,
        String username,
        String role) {
}
