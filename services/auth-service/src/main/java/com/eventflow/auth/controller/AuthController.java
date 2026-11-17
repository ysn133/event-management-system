package com.eventflow.auth.controller;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.eventflow.auth.dto.*;
import com.eventflow.auth.service.AuthService;
@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth=auth; }
    @PostMapping("/login") public LoginResponse login(@Valid @RequestBody LoginRequest request) { return auth.login(request); }
    @GetMapping("/me") public AuthUserResponse me(@AuthenticationPrincipal Jwt jwt) { return auth.current(jwt.getSubject()); }
}
