package com.rosterguard.api.controller;

import com.rosterguard.api.dto.AuthRequest;
import com.rosterguard.api.dto.AuthResponse;
import com.rosterguard.infrastructure.persistence.UserRepository;
import com.rosterguard.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "JWT Token Generation and Authentication")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository,
                          JwtTokenProvider tokenProvider,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue JWT token with tenant isolation")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        Optional<UserRepository.UserRecord> userOpt =
                userRepository.findByTenantAndUsername(request.getTenantId(), request.getUsername());

        if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().passwordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserRepository.UserRecord user = userOpt.get();
        if (!user.active()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String token = tokenProvider.generateToken(user.tenantId(), user.username(), user.role());
        return ResponseEntity.ok(new AuthResponse(token, user.tenantId(), user.username(), user.role()));
    }
}
