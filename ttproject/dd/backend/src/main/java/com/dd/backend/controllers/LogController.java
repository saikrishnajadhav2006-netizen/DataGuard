package com.dd.backend.controllers;

import com.dd.backend.models.LogEntry;
import com.dd.backend.repositories.LogEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    @Autowired
    private LogEntryRepository repository;

    @GetMapping
    public List<LogEntry> getAllLogs(@RequestParam(required = false) String userId) {
        if (userId != null) {
            return repository.findByUserIdOrderByDateDesc(userId);
        }
        return repository.findAll();
    }

    @PostMapping
    public LogEntry createLog(@RequestBody LogEntry logEntry) {
        return repository.save(logEntry);
    }
}
