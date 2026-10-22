package com.tcet.RestApi.service;

import com.tcet.RestApi.model.User;
import com.tcet.RestApi.dto.RegisterRequestDTO;
import com.tcet.RestApi.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User registerUser(RegisterRequestDTO request) {
        String username = request.getUsername().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(normalizeRole(request.getRole()));
        return userRepository.save(user);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "VIEWER";
        }
        String normalized = role.trim().toUpperCase();
        if (!normalized.equals("ADMIN") && !normalized.equals("VIEWER")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be ADMIN or VIEWER");
        }
        return normalized;
    }
}
