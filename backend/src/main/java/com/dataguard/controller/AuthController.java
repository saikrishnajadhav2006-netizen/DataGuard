package com.dataguard.controller;

import com.dataguard.dto.AuthDto;
import com.dataguard.entity.User;
import com.dataguard.repository.UserRepository;
import com.dataguard.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.AuthenticationException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final com.dataguard.service.TokenRevocationService tokenRevocationService;

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<java.util.Map<String, String>> invalidCredentials(AuthenticationException ignored) {
        return ResponseEntity.status(401).body(java.util.Map.of("error", "Email or password is incorrect."));
    }





    
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(409).body(java.util.Map.of("error", "Email is already in use."));
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.Role.DEVELOPER); // Default role

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException duplicate) {
            return ResponseEntity.status(409).body(java.util.Map.of("error", "Email is already in use."));
        }

        String jwtToken = jwtService.generateToken(user.getEmail());
        return ResponseEntity.ok(new AuthDto.AuthResponse(jwtToken, user.getEmail(), user.getFullName(), user.getRole().name()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        User user = userRepository.findByEmail(email).orElseThrow();
        String jwtToken = jwtService.generateToken(user.getEmail());
        
        return ResponseEntity.ok(new AuthDto.AuthResponse(jwtToken, user.getEmail(), user.getFullName(), user.getRole().name()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokenRevocationService.revoke(authorization.substring(7));
        }
        return ResponseEntity.noContent().build();
    }

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                          AuthenticationManager authenticationManager, com.dataguard.service.TokenRevocationService tokenRevocationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.tokenRevocationService = tokenRevocationService;
    }
}
