package com.shardeya.foundation.importexport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportRowRepository extends JpaRepository<ImportRow, UUID> {

    List<ImportRow> findByImportJobIdOrderByRowNumber(UUID importJobId);

    List<ImportRow> findByImportJobIdAndStatusOrderByRowNumber(UUID importJobId, ImportRow.Status status);

    @Query("SELECT r FROM ImportRow r WHERE r.importJobId = :jobId AND r.id = :rowId")
    Optional<ImportRow> findInJob(@Param("jobId") UUID jobId, @Param("rowId") UUID rowId);
}
