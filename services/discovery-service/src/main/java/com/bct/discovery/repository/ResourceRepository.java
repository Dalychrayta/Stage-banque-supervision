package com.bct.discovery.repository;

import com.bct.discovery.model.Resource;
import com.bct.discovery.model.ResourceStatus;
import com.bct.discovery.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    Optional<Resource> findByResourceId(String resourceId);

    boolean existsByResourceId(String resourceId);

    List<Resource> findByStatus(ResourceStatus status);

    List<Resource> findByType(ResourceType type);

    List<Resource> findByEnvironment(String environment);

    @Query("SELECT r FROM Resource r WHERE r.status != 'MAINTENANCE' ORDER BY r.name")
    List<Resource> findAllActive();

    @Query("SELECT COUNT(r) FROM Resource r WHERE r.status = :status")
    long countByStatus(ResourceStatus status);
}
