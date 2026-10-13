package com.dataguard.controller;

import com.dataguard.dto.AuthDto;
import com.dataguard.entity.User;
import com.dataguard.repository.UserRepository;
import com.dataguard.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

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
        if (!EMAIL_PATTERN.matcher(request.getEmail().trim()).matches()) {
            return ResponseEntity.badRequest().body("Enter a valid email address.");
        }
        if (request.getPassword().length() < 8 || request.getPassword().length() > 72) {
            return ResponseEntity.badRequest().body("Password must contain between 8 and 72 characters.");
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
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.badRequest().body("Enter a valid email address.");
        }
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

    public record ProfileRequest(String fullName) {}
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(new AuthDto.AuthResponse(null, user.getEmail(), user.getFullName(), user.getRole().name()));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody ProfileRequest request, Authentication authentication) {
        if (request == null || isBlank(request.fullName())) {
            return ResponseEntity.badRequest().body("Username is required.");
        }
        String fullName = request.fullName().trim();
        if (fullName.length() > 80) {
            return ResponseEntity.badRequest().body("Username must be 80 characters or fewer.");
        }
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        user.setFullName(fullName);
        userRepository.save(user);
        return ResponseEntity.ok(new AuthDto.AuthResponse(null, user.getEmail(), user.getFullName(), user.getRole().name()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request, Authentication authentication) {
        if (request == null || isBlank(request.currentPassword()) || isBlank(request.newPassword())) {
            return ResponseEntity.badRequest().body("Current password and new password are required.");
        }
        if (request.newPassword().length() < 8 || request.newPassword().length() > 72) {
            return ResponseEntity.badRequest().body("New password must contain between 8 and 72 characters.");
        }
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body("Choose a new password different from your current password.");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        return ResponseEntity.ok(java.util.Map.of("message", "Password updated successfully."));
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
