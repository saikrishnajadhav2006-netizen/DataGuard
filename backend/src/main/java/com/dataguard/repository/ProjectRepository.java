package com.dataguard.repository;

import com.dataguard.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    java.util.Optional<Project> findByName(String name);
}
