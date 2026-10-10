package com.dataguard.controller;

import com.dataguard.dto.AuthDto;
import com.dataguard.entity.User;
import com.dataguard.repository.UserRepository;
import com.dataguard.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthDto.RegisterRequest request) {
        if (request == null || isBlank(request.getFullName())
                || isBlank(request.getEmail()) || isBlank(request.getPassword())) {
            return ResponseEntity.badRequest().body("Full name, email, and password are required.");
        }
        if (request.getPassword().length() < 6) {
            return ResponseEntity.badRequest().body("Password must contain at least 6 characters.");
        }

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email is already in use. Please sign in.");
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.Role.DEVELOPER);

        // Validate JWT configuration before persisting, avoiding an orphan account if token creation fails.
        String jwtToken = jwtService.generateToken(email);
        userRepository.save(user);

        return ResponseEntity.ok(new AuthDto.AuthResponse(
                jwtToken, user.getEmail(), user.getFullName(), user.getRole().name()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthDto.LoginRequest request) {
        if (request == null || isBlank(request.getEmail()) || isBlank(request.getPassword())) {
            return ResponseEntity.badRequest().body("Email and password are required.");
        }

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (BadCredentialsException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password.");
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unable to authenticate with these credentials.");
        }

        User user = userRepository.findByEmail(email).orElseThrow();
        String jwtToken = jwtService.generateToken(user.getEmail());

        return ResponseEntity.ok(new AuthDto.AuthResponse(
                jwtToken, user.getEmail(), user.getFullName(), user.getRole().name()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtService jwtService, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }
}
