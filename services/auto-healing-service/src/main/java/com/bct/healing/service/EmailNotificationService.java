package com.bct.healing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envoie la vraie notification pour l'action NOTIFY_TEAM. Un échec d'envoi
 * (SMTP injoignable, identifiants invalides...) ne doit jamais faire planter
 * l'action elle-même : on renvoie false, et l'appelant décide quoi faire du
 * statut de l'action.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Value("${notification.email.to:}")
    private String to;

    public boolean sendIncidentNotification(String resourceName, String causeCategory, String description) {
        if (to.isBlank()) {
            log.warn("NOTIFICATION_EMAIL_TO non configuré — notification NOTIFY_TEAM non envoyée pour {}", resourceName);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("[BCT Supervision] Intervention requise — " + resourceName);
            message.setText("""
                    Une anomalie sur %s nécessite une intervention manuelle — \
                    la plateforme n'a pas pu la résoudre elle-même.

                    Cause retenue : %s
                    Détail : %s

                    Dashboard : http://localhost:4200/anomalies
                    """.formatted(resourceName, causeCategory, description));
            mailSender.send(message);
            log.info("Notification envoyée à {} pour {}", to, resourceName);
            return true;
        } catch (Exception e) {
            log.warn("Échec de l'envoi de la notification email pour {} : {}", resourceName, e.getMessage());
            return false;
        }
    }
}
