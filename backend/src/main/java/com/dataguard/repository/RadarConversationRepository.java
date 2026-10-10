package com.dataguard.repository;

import com.dataguard.entity.RadarConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RadarConversationRepository extends JpaRepository<RadarConversation, Long> {
    List<RadarConversation> findAllByOwner_EmailOrderByUpdatedAtDesc(String email);
    Optional<RadarConversation> findByIdAndOwner_Email(Long id, String email);
}
