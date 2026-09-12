package com.bct.healing.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealingActionTest {

    @Test
    void anActionSavedWithoutAnActorIsAttributedToThePlatformRatherThanLeftAnonymous() {
        // Une action sans acteur n'a aucune valeur pour un auditeur. Si un
        // chemin de code oubliait de le renseigner, elle doit être attribuée
        // à la plateforme — jamais rester anonyme.
        HealingAction action = HealingAction.builder().resourceId("srv-001").build();

        action.onCreate();

        assertThat(action.getTriggeredBy()).isEqualTo(HealingAction.SYSTEM_ACTOR);
    }

    @Test
    void anActorAlreadySetIsNeverOverwritten() {
        HealingAction action = HealingAction.builder()
                .resourceId("srv-002")
                .triggeredBy(HealingAction.USER_ACTOR_PREFIX + "operator")
                .build();

        action.onCreate();

        assertThat(action.getTriggeredBy()).isEqualTo("utilisateur:operator");
    }
}
