package com.dd.backend.repositories;

import com.dd.backend.models.WorkspaceBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkspaceBlockRepository extends JpaRepository<WorkspaceBlock, Long> {
    List<WorkspaceBlock> findByWorkspaceIdOrderByPositionAsc(Long workspaceId);
}
