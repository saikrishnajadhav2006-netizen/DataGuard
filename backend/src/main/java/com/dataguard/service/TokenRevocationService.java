package com.dataguard.service;

import com.dataguard.entity.RevokedToken;
import com.dataguard.repository.RevokedTokenRepository;
import com.dataguard.security.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Service
public class TokenRevocationService {
    private final RevokedTokenRepository repository;
    private final JwtService jwtService;

    public TokenRevocationService(RevokedTokenRepository repository, JwtService jwtService) {
        this.repository = repository;
        this.jwtService = jwtService;
    }

    @Transactional
    public void revoke(String token) {
        repository.deleteAllByExpiresAtBefore(Instant.now());
        Instant expiry = jwtService.extractExpiration(token).toInstant();
        repository.save(new RevokedToken(hash(token), expiry));
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String token) { return repository.existsById(hash(token)); }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }
}
