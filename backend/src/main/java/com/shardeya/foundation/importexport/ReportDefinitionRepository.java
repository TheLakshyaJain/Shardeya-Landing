package com.shardeya.foundation.importexport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, String> {

    List<ReportDefinition> findByProfileOrderBySortOrder(String profile);
}
