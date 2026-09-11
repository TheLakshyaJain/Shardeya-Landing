package com.shardeya.builder.plot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlotRepository extends JpaRepository<Plot, UUID>, JpaSpecificationExecutor<Plot> {

    Optional<Plot> findByIdAndDeletedAtIsNull(UUID id);

    // plot_number_norm is a generated column — comparing against the SAME
    // normalisation the DB applies (upper + strip non-alphanumeric via 'g'
    // flag) here in Java would drift the moment either side changes; instead
    // this reads the already-generated column back for the given project.
    @Query("SELECT p FROM Plot p WHERE p.projectId = :projectId AND p.deletedAt IS NULL AND p.plotNumberNorm = :norm")
    Optional<Plot> findByNormalisedNumber(@Param("projectId") UUID projectId, @Param("norm") String norm);

    @Query("SELECT p FROM Plot p WHERE p.projectId = :projectId AND p.deletedAt IS NULL AND p.gridRow = :row AND p.gridCol = :col")
    Optional<Plot> findByCell(@Param("projectId") UUID projectId, @Param("row") int row, @Param("col") int col);

    List<Plot> findByProjectIdAndDeletedAtIsNull(UUID projectId);

    long countByProjectIdAndDeletedAtIsNull(UUID projectId);

    // Quick Create's preview needs to flag collisions against every existing
    // plot in the project at once (potentially thousands of generated
    // numbers) -- bulk-fetching the already-generated norm column once and
    // checking membership in-memory beats one findByNormalisedNumber query
    // per generated number.
    @Query("SELECT p.plotNumberNorm FROM Plot p WHERE p.projectId = :projectId AND p.deletedAt IS NULL")
    List<String> findAllNormalisedNumbers(@Param("projectId") UUID projectId);

    // Quick Create's sequential-fallback placement needs every already-
    // occupied cell in the project up front (to skip past them while
    // scanning for the next open one) -- same bulk-fetch-once-then-check-
    // in-memory approach as findAllNormalisedNumbers, for the same reason.
    @Query("SELECT p.gridRow AS gridRow, p.gridCol AS gridCol FROM Plot p WHERE p.projectId = :projectId " +
            "AND p.deletedAt IS NULL AND p.gridRow IS NOT NULL AND p.gridCol IS NOT NULL")
    List<CellPosition> findAllOccupiedCells(@Param("projectId") UUID projectId);

    interface CellPosition {
        Integer getGridRow();
        Integer getGridCol();
    }

    List<Plot> findByProjectId(UUID projectId);

    List<Plot> findByProjectIdAndGridRowIsNullAndDeletedAtIsNull(UUID projectId);

    @Query("SELECT p.projectId AS projectId, p.status AS status, COUNT(p) AS cnt FROM Plot p " +
            "WHERE p.projectId IN :projectIds AND p.deletedAt IS NULL GROUP BY p.projectId, p.status")
    List<StatusCount> countByStatusForProjects(@Param("projectIds") List<UUID> projectIds);

    interface StatusCount {
        UUID getProjectId();
        Plot.Status getStatus();
        long getCnt();
    }
}
