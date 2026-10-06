package com.dd.backend.repositories;

import com.dd.backend.models.LogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogEntryRepository extends JpaRepository<LogEntry, Long> {
    List<LogEntry> findByUserIdOrderByDateDesc(String userId);
}
