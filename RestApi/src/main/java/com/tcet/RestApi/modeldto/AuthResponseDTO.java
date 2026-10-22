package com.tcet.RestApi.modeldto;

public record AuthResponseDTO(
        String token,
        String tokenType,
        long expiresIn,
        String username,
        String role) {
}
