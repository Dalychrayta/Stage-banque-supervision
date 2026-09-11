package com.bct.healing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Exécute de VRAIES actions de remédiation sur la cible réelle srv-002
 * (PlatformeBack), désormais conteneurisée elle aussi.
 *
 * Pourquoi conteneurisée : un conteneur est isolé de sa machine hôte par
 * conception. Tant que la cible était un processus Windows hors Docker,
 * ce service — qui tourne dans un conteneur Linux — ne pouvait ni la voir
 * ni agir dessus. En mettant la cible dans le même monde, le redémarrage
 * se fait par l'API Docker et le nettoyage par un volume partagé.
 *
 * Portée volontairement étroite : ce code ne sait nommer QU'UN conteneur
 * et QU'UN dossier, tous deux fixés par configuration. Même si le socket
 * Docker monté donne théoriquement accès à tous les conteneurs, le code
 * n'offre aucun moyen d'en désigner un autre.
 */
@Component
@Slf4j
public class RealActionExecutor {

    public static final String REAL_TARGET_RESOURCE_ID = "srv-002";

    /** Le seul conteneur que ce service a le droit de redémarrer. */
    @Value("${real-target.container:bct-platformeback}")
    private String targetContainer = "bct-platformeback";

    /** Le seul dossier où ce service a le droit de supprimer des fichiers. */
    @Value("${real-target.log-dir:/target-logs}")
    private String logDir = "/target-logs";

    /** Âge minimum d'un log archivé pour être supprimable. */
    @Value("${real-target.log-retention-days:7}")
    private int logRetentionDays = 7;

    private static final String ACTIVE_LOG_FILE = "platformeback.log";
    private static final String ARCHIVED_LOG_PREFIX = "platformeback-";
    private static final String LOG_SUFFIX = ".log";

    /** Résultat structuré d'une action réelle : succès explicite, jamais deviné depuis un texte. */
    public record ActionResult(boolean success, String message) {}

    public boolean isRealTarget(String resourceId) {
        return REAL_TARGET_RESOURCE_ID.equals(resourceId);
    }

    // ------------------------------------------------------------------
    // Redémarrage réel du conteneur cible
    // ------------------------------------------------------------------

    /** Redémarre réellement le conteneur de la cible via l'API Docker. */
    public ActionResult restartRealService() {
        try {
            Process p = new ProcessBuilder("docker", "restart", targetContainer)
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                output = reader.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + " | " + b);
            }
            boolean finished = p.waitFor(90, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                log.error("[RÉEL] Redémarrage de {} : délai dépassé", targetContainer);
                return new ActionResult(false,
                        "[RÉEL - ÉCHEC] Délai dépassé au redémarrage du conteneur " + targetContainer);
            }
            if (p.exitValue() != 0) {
                log.error("[RÉEL] Redémarrage de {} en échec (code {}) : {}", targetContainer, p.exitValue(), output);
                return new ActionResult(false, String.format(
                        "[RÉEL - ÉCHEC] Redémarrage du conteneur %s impossible (code %d) : %s",
                        targetContainer, p.exitValue(), output));
            }
            log.info("[RÉEL] Conteneur {} redémarré", targetContainer);
            return new ActionResult(true,
                    "[RÉEL] Conteneur " + targetContainer + " redémarré via l'API Docker.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ActionResult(false, "[RÉEL - ÉCHEC] Redémarrage interrompu");
        } catch (Exception e) {
            log.error("[RÉEL] Échec du redémarrage de {} : {}", targetContainer, e.getMessage(), e);
            return new ActionResult(false, "[RÉEL - ÉCHEC] " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Libération réelle d'espace disque
    // ------------------------------------------------------------------

    /**
     * Un fichier est supprimable seulement s'il est un log ARCHIVÉ de la cible
     * (préfixe + suffixe attendus) et assez ancien. Le fichier en cours
     * d'écriture et tout autre fichier sont systématiquement épargnés.
     */
    static boolean isDeletableArchivedLog(String fileName, Instant lastModified, Instant now, int retentionDays) {
        if (fileName == null || ACTIVE_LOG_FILE.equals(fileName)) return false;
        if (!fileName.startsWith(ARCHIVED_LOG_PREFIX) || !fileName.endsWith(LOG_SUFFIX)) return false;
        if (lastModified == null || now == null) return false;
        return Duration.between(lastModified, now).toDays() >= retentionDays;
    }

    /**
     * Locale explicite : le message remonte tel quel dans l'interface française.
     * Sans elle, le séparateur décimal suivrait la locale de la machine et
     * changerait entre le poste de développement et le conteneur.
     */
    static String humanReadableBytes(long bytes) {
        if (bytes < 1024) return bytes + " o";
        if (bytes < 1024 * 1024) return String.format(Locale.FRENCH, "%.1f Ko", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.FRENCH, "%.1f Mo", bytes / (1024.0 * 1024));
        return String.format(Locale.FRENCH, "%.2f Go", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Supprime réellement les logs archivés de la cible qui dépassent la durée
     * de rétention, et retourne le vrai volume libéré. Strictement limité au
     * dossier de logs partagé avec la cible.
     */
    public ActionResult freeRealDiskSpace() {
        File dir = new File(logDir);
        if (!dir.isDirectory()) {
            log.warn("[RÉEL] Dossier de logs introuvable : {}", logDir);
            return new ActionResult(false, "[RÉEL - ÉCHEC] Dossier de logs introuvable : " + logDir);
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return new ActionResult(false, "[RÉEL - ÉCHEC] Impossible de lister le dossier " + logDir);
        }

        Instant now = Instant.now();
        long freedBytes = 0;
        List<String> deleted = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (File f : files) {
            if (!f.isFile()) continue;
            if (!isDeletableArchivedLog(f.getName(), Instant.ofEpochMilli(f.lastModified()), now, logRetentionDays)) {
                continue;
            }
            long size = f.length();
            if (f.delete()) {
                freedBytes += size;
                deleted.add(f.getName());
            } else {
                failed.add(f.getName());
            }
        }

        if (deleted.isEmpty() && failed.isEmpty()) {
            log.info("[RÉEL] Aucun log archivé de plus de {} jours dans {}", logRetentionDays, logDir);
            return new ActionResult(true, String.format(
                    "[RÉEL] Aucun fichier à supprimer : aucun log archivé de plus de %d jours.", logRetentionDays));
        }

        if (!failed.isEmpty()) {
            log.error("[RÉEL] {} fichier(s) non supprimable(s) : {}", failed.size(), failed);
            return new ActionResult(false, String.format(
                    "[RÉEL - ÉCHEC] %d fichier(s) supprimé(s) (%s libérés), mais %d non supprimable(s) : %s",
                    deleted.size(), humanReadableBytes(freedBytes), failed.size(), String.join(", ", failed)));
        }

        log.info("[RÉEL] {} log(s) archivé(s) supprimé(s), {} libérés dans {}",
                deleted.size(), humanReadableBytes(freedBytes), logDir);
        return new ActionResult(true, String.format(
                "[RÉEL] %d fichier(s) de log supprimé(s), %s libérés (archives de plus de %d jours).",
                deleted.size(), humanReadableBytes(freedBytes), logRetentionDays));
    }
}
