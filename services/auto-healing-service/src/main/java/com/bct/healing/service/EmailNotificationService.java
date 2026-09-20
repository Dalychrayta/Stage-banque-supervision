package com.bct.healing.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Envoie la vraie notification pour l'action NOTIFY_TEAM, en HTML. Un échec
 * d'envoi (SMTP injoignable, identifiants invalides...) ne doit jamais faire
 * planter l'action elle-même : on renvoie false, et l'appelant décide quoi
 * faire du statut de l'action.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Value("${notification.email.to:}")
    private String to;

    public boolean sendIncidentNotification(String resourceName, String causeCategory, String description, String severity) {
        return send("[BCT Supervision] Intervention requise — " + resourceName,
                "Intervention manuelle requise",
                "%s"
                        .formatted(HtmlUtils.htmlEscape(resourceName)),
                resourceName, causeCategory, description, severity);
    }

    /**
     * Prévient l'équipe qu'une réparation automatique a échoué techniquement :
     * sans cet email, l'incident resterait ouvert sans que personne ne le voie.
     */
    public boolean sendActionFailureNotification(String resourceName, String causeCategory,
                                                 String actionLabel, String failureMessage, String severity) {
        return send("[BCT Supervision] Réparation automatique échouée — " + resourceName,
                "Réparation automatique échouée",
                "L'action <b>%s</b> a échoué sur <b>%s</b>. L'incident reste ouvert et demande une intervention."
                        .formatted(HtmlUtils.htmlEscape(actionLabel), HtmlUtils.htmlEscape(resourceName)),
                resourceName, causeCategory, failureMessage, severity);
    }

    private boolean send(String subject, String title, String intro, String resourceName,
                         String causeCategory, String description, String severity) {
        if (to.isBlank()) {
            log.warn("NOTIFICATION_EMAIL_TO non configuré — notification non envoyée pour {}", resourceName);
            return false;
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(buildHtml(title, intro, resourceName, causeCategory, description, severity), true);
            mailSender.send(mimeMessage);
            log.info("Notification envoyée à {} pour {}", to, resourceName);
            return true;
        } catch (Exception e) {
            log.warn("Échec de l'envoi de la notification email pour {} : {}", resourceName, e.getMessage());
            return false;
        }
    }
    private String buildHtml(String title, String intro, String resourceName, String causeCategory, String description, String severity) {
        String safeResource = HtmlUtils.htmlEscape(resourceName);
        String safeDescription = HtmlUtils.htmlEscape(description);

        // Une action manuelle n'a pas de cause RCA ni de sévérité : ce n'est
        // pas une anomalie inconnue à afficher comme "null", c'est simplement
        // un humain qui a décidé d'agir.
        boolean hasSeverity = severity != null && !severity.isBlank();
        String badgeLabel = hasSeverity ? severity.toUpperCase() : "MANUELLE";
        String[] badgeColors = switch (hasSeverity ? severity.toUpperCase() : "MANUELLE") {
            case "CRITICAL" -> new String[]{"#fbeaea", "#c23b3b"};
            case "WARNING" -> new String[]{"#faf0dd", "#a1690f"};
            default -> new String[]{"#edf2f7", "#4a5568"};
        };

        boolean hasCause = causeCategory != null && !causeCategory.isBlank();
        String causeRow = hasCause ? """
                <tr>
                  <td style="padding:10px 0;border-bottom:1px solid #eef0ed;color:#6b7280;font-size:13px;width:130px;">Cause retenue</td>
                  <td style="padding:10px 0;border-bottom:1px solid #eef0ed;color:#1b1f23;font-size:13px;font-family:monospace;">%s</td>
                </tr>
                """.formatted(HtmlUtils.htmlEscape(causeCategory)) : "";

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        return """
                <div style="background:#f3f4f1;padding:24px 12px;font-family:-apple-system,Segoe UI,Arial,sans-serif;">
                  <div style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:10px;overflow:hidden;border:1px solid #e2e4de;">
                    <div style="background:#111a2e;padding:18px 24px;">
                      <span style="color:#ffffff;font-size:15px;font-weight:600;letter-spacing:.02em;">BCT Supervision</span>
                    </div>
                    <div style="padding:26px 24px 8px;">
                      <span style="display:inline-block;background:%s;color:%s;font-size:11px;font-weight:600;letter-spacing:.03em;padding:4px 10px;border-radius:8px;margin-bottom:14px;">%s</span>
                      <h1 style="margin:0 0 6px;font-size:18px;color:#1b1f23;font-weight:600;">%s</h1>
                      <p style="margin:0 0 20px;color:#454b52;font-size:14px;line-height:1.6;">
                        %s
                      </p>
                      <table style="width:100%%;border-collapse:collapse;">
                        <tr>
                          <td style="padding:10px 0;border-bottom:1px solid #eef0ed;color:#6b7280;font-size:13px;width:130px;">Ressource</td>
                          <td style="padding:10px 0;border-bottom:1px solid #eef0ed;color:#1b1f23;font-size:13px;font-weight:600;">%s</td>
                        </tr>
                        %s
                        <tr>
                          <td style="padding:10px 0;color:#6b7280;font-size:13px;width:130px;vertical-align:top;">Détail</td>
                          <td style="padding:10px 0;color:#1b1f23;font-size:13px;">%s</td>
                        </tr>
                      </table>
                    </div>
                    <div style="padding:16px 24px;background:#f7f8f6;border-top:1px solid #eef0ed;">
                      <p style="margin:0;color:#9098a3;font-size:11px;">Message automatique généré le %s par la plateforme BCT Supervision. Ne pas répondre à cet email.</p>
                    </div>
                  </div>
                </div>
                """.formatted(badgeColors[0], badgeColors[1], badgeLabel, title, intro, safeResource, causeRow, safeDescription, timestamp);
    }
}
