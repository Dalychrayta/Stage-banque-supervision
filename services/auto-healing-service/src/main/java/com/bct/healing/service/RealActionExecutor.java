package com.bct.healing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Exécute de VRAIES actions de remédiation — pour l'instant uniquement sur
 * la cible réelle srv-002 (PlatformeBack, relancé sur le PC pour servir de
 * serveur de test réel). Toutes les autres ressources restent simulées :
 * on ne touche jamais un processus qu'on ne contrôle pas.
 */
@Component
@Slf4j
public class RealActionExecutor {

    public static final String REAL_TARGET_RESOURCE_ID = "srv-002";

    private static final int REAL_TARGET_PORT = 8085;
    private static final String REAL_TARGET_PROJECT_DIR = "C:\\Users\\MSI\\Documents\\PlatformeBack";
    private static final String MVN_CMD = "C:\\Users\\MSI\\tools\\apache-maven-3.9.9\\bin\\mvn.cmd";

    /** Résultat structuré d'une action réelle : succès explicite, jamais deviné depuis un texte. */
    public record ActionResult(boolean success, String message) {}

    public boolean isRealTarget(String resourceId) {
        return REAL_TARGET_RESOURCE_ID.equals(resourceId);
    }

    /** Arrête vraiment le processus PlatformeBack et le relance vraiment. */
    public ActionResult restartRealService() {
        try {
            Integer pid = findPidListeningOnPort(REAL_TARGET_PORT);
            if (pid != null) {
                int exitCode = new ProcessBuilder("cmd", "/c", "taskkill", "/PID", String.valueOf(pid), "/F")
                        .inheritIO()
                        .start()
                        .waitFor();
                if (exitCode != 0) {
                    log.error("[RÉEL] taskkill a échoué (code {}) pour le PID {}", exitCode, pid);
                    return new ActionResult(false, "[RÉEL - ÉCHEC] Impossible d'arrêter le processus PID " + pid + " (code " + exitCode + ").");
                }
                log.info("[RÉEL] PlatformeBack arrêté (PID {}, port {})", pid, REAL_TARGET_PORT);
            } else {
                log.warn("[RÉEL] Aucun processus trouvé sur le port {} — déjà arrêté ?", REAL_TARGET_PORT);
            }

            Files.createDirectories(Paths.get(System.getenv("TEMP")));
            File logFile = new File(System.getenv("TEMP"), "platformeback-autoheal.log");

            ProcessBuilder relaunch = new ProcessBuilder("cmd", "/c", MVN_CMD, "-q", "spring-boot:run");
            relaunch.directory(new File(REAL_TARGET_PROJECT_DIR));
            relaunch.redirectOutput(ProcessBuilder.Redirect.to(logFile));
            relaunch.redirectError(ProcessBuilder.Redirect.to(logFile));
            relaunch.start(); // ne bloque pas : le nouveau processus tourne en tâche de fond

            log.info("[RÉEL] Relance de PlatformeBack en cours (log: {})", logFile.getAbsolutePath());
            return new ActionResult(true, "[RÉEL] Processus PlatformeBack (ancien PID " + pid + ") arrêté et relance déclenchée sur le port " + REAL_TARGET_PORT + ".");
        } catch (Exception e) {
            log.error("[RÉEL] Échec du redémarrage réel de PlatformeBack: {}", e.getMessage(), e);
            return new ActionResult(false, "[RÉEL - ÉCHEC] " + e.getMessage());
        }
    }

    private Integer findPidListeningOnPort(int port) throws Exception {
        Process p = new ProcessBuilder("cmd", "/c", "netstat -ano | findstr :" + port + " | findstr LISTENING").start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line = reader.readLine();
            p.waitFor();
            if (line == null || line.isBlank()) return null;
            String[] parts = line.trim().split("\\s+");
            return Integer.parseInt(parts[parts.length - 1]);
        }
    }
}
