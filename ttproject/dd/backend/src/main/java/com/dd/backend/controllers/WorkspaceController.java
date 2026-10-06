package com.dd.backend.controllers;

import com.dd.backend.models.Workspace;
import com.dd.backend.repositories.WorkspaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
@CrossOrigin(origins = "*") // Update for production
public class WorkspaceController {

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @GetMapping
    public List<Workspace> getWorkspaces(@RequestParam(required = false) String userId) {
        if (userId != null) {
            return workspaceRepository.findByUserId(userId);
        }
        return workspaceRepository.findAll();
    }

    @PostMapping
    public Workspace createWorkspace(@RequestBody Workspace workspace) {
        return workspaceRepository.save(workspace);
    }
}
