package com.bct.collector.repository;

import com.bct.collector.model.MetricSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, Long> {

    List<MetricSnapshot> findByResourceIdOrderByCollectedAtDesc(String resourceId);

    List<MetricSnapshot> findByResourceIdAndCollectedAtBetweenOrderByCollectedAtAsc(
            String resourceId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT m FROM MetricSnapshot m WHERE m.resourceId = :resourceId ORDER BY m.collectedAt DESC")
    List<MetricSnapshot> findLatestByResourceId(String resourceId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT DISTINCT m.resourceId FROM MetricSnapshot m")
    List<String> findDistinctResourceIds();
}
