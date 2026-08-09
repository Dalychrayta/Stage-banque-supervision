package com.bct.healing.repository;

import com.bct.healing.model.ActionStatus;
import com.bct.healing.model.HealingAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HealingActionRepository extends JpaRepository<HealingAction, Long> {

    List<HealingAction> findByResourceIdOrderByTriggeredAtDesc(String resourceId);

    List<HealingAction> findByStatusOrderByTriggeredAtDesc(ActionStatus status);

    List<HealingAction> findTop500ByOrderByTriggeredAtDesc();

    long countByStatus(ActionStatus status);
}
