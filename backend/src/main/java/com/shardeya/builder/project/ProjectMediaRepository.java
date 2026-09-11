package com.shardeya.builder.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMediaRepository extends JpaRepository<ProjectMedia, UUID> {

    List<ProjectMedia> findByProjectIdAndDeletedAtIsNullOrderBySortOrder(UUID projectId);

    Optional<ProjectMedia> findByIdAndDeletedAtIsNull(UUID id);
}
