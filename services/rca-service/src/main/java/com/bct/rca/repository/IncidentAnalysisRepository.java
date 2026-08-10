package com.bct.rca.repository;

import com.bct.rca.model.AnalysisStatus;
import com.bct.rca.model.IncidentAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IncidentAnalysisRepository extends JpaRepository<IncidentAnalysis, Long> {

    List<IncidentAnalysis> findByResourceIdOrderByAnalyzedAtDesc(String resourceId);

    List<IncidentAnalysis> findByStatusOrderByAnalyzedAtDesc(AnalysisStatus status);

    List<IncidentAnalysis> findBySeverityAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
            String severity, LocalDateTime after);

    List<IncidentAnalysis> findByAnalyzedAtAfterOrderByAnalyzedAtDesc(LocalDateTime after);

    Page<IncidentAnalysis> findAllByOrderByAnalyzedAtDesc(Pageable pageable);

    long countByStatus(AnalysisStatus status);

    long countBySeverityAndStatus(String severity, AnalysisStatus status);
}
