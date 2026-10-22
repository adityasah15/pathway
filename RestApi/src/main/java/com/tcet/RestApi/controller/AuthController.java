package com.tcet.RestApi.controller;

import com.tcet.RestApi.model.User;
import com.tcet.RestApi.dto.AuthResponseDTO;
import com.tcet.RestApi.dto.LoginRequestDTO;
import com.tcet.RestApi.dto.RegisterRequestDTO;
import com.tcet.RestApi.dto.UserResponseDTO;
import com.tcet.RestApi.security.JwtService;
import com.tcet.RestApi.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDTO registerUser(@Valid @RequestBody RegisterRequestDTO request) {
        User user = userService.registerUser(request);
        return new UserResponseDTO(user.getId(), user.getUsername(), user.getRole());
    }

    @PostMapping("/login")
    public AuthResponseDTO login(@Valid @RequestBody LoginRequestDTO request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        String username = authentication.getName();
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(authority -> authority.replace("ROLE_", ""))
                .orElse("VIEWER");

        String token = jwtService.generateToken(username);
        return new AuthResponseDTO(token, "Bearer", jwtService.getExpirationSeconds(), username, role);
    }
}
