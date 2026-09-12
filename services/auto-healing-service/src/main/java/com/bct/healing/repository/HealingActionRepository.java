package com.bct.healing.repository;

import com.bct.healing.model.ActionStatus;
import com.bct.healing.model.ActionType;
import com.bct.healing.model.HealingAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HealingActionRepository extends JpaRepository<HealingAction, Long> {

    List<HealingAction> findByResourceIdOrderByTriggeredAtDesc(String resourceId);

    List<HealingAction> findByStatusOrderByTriggeredAtDesc(ActionStatus status);

    Page<HealingAction> findAllByOrderByTriggeredAtDesc(Pageable pageable);

    boolean existsByIncidentId(Long incidentId);

    /**
     * Dernière action de ce type RÉELLEMENT exécutée sur cette ressource — sert
     * au délai de garde. Les actions passées en SKIPPED sont exclues : une action
     * qu'on a justement refusé d'exécuter ne doit pas repousser l'échéance, sinon
     * le délai de garde ne se terminerait jamais.
     */
    Optional<HealingAction> findFirstByResourceIdAndActionTypeAndStatusNotOrderByTriggeredAtDesc(
            String resourceId, ActionType actionType, ActionStatus excludedStatus);

    long countByStatus(ActionStatus status);
}
