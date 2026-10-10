package com.dataguard.repository;

import com.dataguard.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
    long deleteAllByExpiresAtBefore(Instant instant);
}
