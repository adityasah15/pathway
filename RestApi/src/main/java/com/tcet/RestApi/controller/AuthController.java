package com.tcet.RestApi.controller;

import com.tcet.RestApi.modeldto.LoginRequestDTO;
import com.tcet.RestApi.modeldto.RegisterRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;


import com.tcet.RestApi.model.User;
import com.tcet.RestApi.security.JwtService;
import com.tcet.RestApi.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    private final AuthenticationManager authenticationManager;

    private final JwtService jwtService;


    // Register API
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(
            @RequestBody RegisterRequestDTO request) {

        User user = userService.registerUser(request);

        return new ResponseEntity<>(
                user,
                HttpStatus.CREATED
        );
    }


    // Login API
    @PostMapping("/login")
    public ResponseEntity<String> login(
            @RequestBody LoginRequestDTO request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        String token = jwtService.generateToken(
                request.getUsername()
        );

        return ResponseEntity.ok(token);
    }
}