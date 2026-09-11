package com.bct.healing.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RealActionExecutorTest {

    private final RealActionExecutor executor = new RealActionExecutor();

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");
    private static final int RETENTION_DAYS = 7;

    private static Instant daysAgo(long days) {
        return NOW.minus(days, ChronoUnit.DAYS);
    }

    @Test
    void isRealTarget_shouldMatchOnlyTheKnownRealResourceId() {
        assertThat(executor.isRealTarget("srv-002")).isTrue();
    }

    @Test
    void isRealTarget_shouldRejectEveryOtherResource() {
        assertThat(executor.isRealTarget("srv-001")).isFalse();
        assertThat(executor.isRealTarget("app-001")).isFalse();
        assertThat(executor.isRealTarget("app-002")).isFalse();
        assertThat(executor.isRealTarget("db-001")).isFalse();
        assertThat(executor.isRealTarget(null)).isFalse();
        assertThat(executor.isRealTarget("")).isFalse();
    }

    // --- Quels fichiers peuvent être supprimés par FREE_DISK_SPACE ---

    @Test
    void deletable_shouldAcceptAnOldArchivedLog() {
        assertThat(RealActionExecutor.isDeletableArchivedLog(
                "platformeback-2026-09-01.0.log", daysAgo(10), NOW, RETENTION_DAYS)).isTrue();
    }

    @Test
    void deletable_shouldNeverTouchTheActiveLogFile() {
        // Même très ancien, le fichier en cours d'écriture reste intouchable.
        assertThat(RealActionExecutor.isDeletableArchivedLog(
                "platformeback.log", daysAgo(365), NOW, RETENTION_DAYS)).isFalse();
    }

    @Test
    void deletable_shouldRejectArchivesYoungerThanRetention() {
        assertThat(RealActionExecutor.isDeletableArchivedLog(
                "platformeback-2026-09-10.0.log", daysAgo(1), NOW, RETENTION_DAYS)).isFalse();
        assertThat(RealActionExecutor.isDeletableArchivedLog(
                "platformeback-2026-09-05.0.log", daysAgo(6), NOW, RETENTION_DAYS)).isFalse();
    }

    @Test
    void deletable_shouldAcceptExactlyAtRetentionBoundary() {
        assertThat(RealActionExecutor.isDeletableArchivedLog(
                "platformeback-2026-09-04.0.log", daysAgo(7), NOW, RETENTION_DAYS)).isTrue();
    }

    @Test
    void deletable_shouldRejectAnyFileThatIsNotATargetLog() {
        // Un fichier d'une autre application, une archive, un exécutable...
        assertThat(RealActionExecutor.isDeletableArchivedLog(
                "autre-appli-2026-09-01.log", daysAgo(30), NOW, RETENTION_DAYS)).isFalse();
        assertThat(RealActionExecutor.isDeletableArchivedLog(
                "platformeback-2026-09-01.0.zip", daysAgo(30), NOW, RETENTION_DAYS)).isFalse();
        assertThat(RealActionExecutor.isDeletableArchivedLog(
                "important.docx", daysAgo(30), NOW, RETENTION_DAYS)).isFalse();
        assertThat(RealActionExecutor.isDeletableArchivedLog(
                null, daysAgo(30), NOW, RETENTION_DAYS)).isFalse();
    }

    @Test
    void deletable_shouldRejectWhenDateIsUnknown() {
        assertThat(RealActionExecutor.isDeletableArchivedLog(
                "platformeback-2026-09-01.0.log", null, NOW, RETENTION_DAYS)).isFalse();
    }

    // --- Affichage du volume libéré ---

    @Test
    void humanReadableBytes_shouldUseTheRightUnit() {
        assertThat(RealActionExecutor.humanReadableBytes(512)).isEqualTo("512 o");
        assertThat(RealActionExecutor.humanReadableBytes(2048)).isEqualTo("2,0 Ko");
        assertThat(RealActionExecutor.humanReadableBytes(5L * 1024 * 1024)).isEqualTo("5,0 Mo");
        assertThat(RealActionExecutor.humanReadableBytes(3L * 1024 * 1024 * 1024)).isEqualTo("3,00 Go");
    }

    @Test
    void freeRealDiskSpace_shouldFailCleanlyWhenLogDirectoryIsMissing() {
        // Le dossier de logs n'existe pas dans l'environnement de test :
        // l'action doit échouer proprement, sans rien supprimer ailleurs.
        RealActionExecutor.ActionResult result = executor.freeRealDiskSpace();
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("[RÉEL - ÉCHEC]");
    }

    // --- Suppression réelle sur un vrai dossier ---

    private static void writeFile(Path dir, String name, int sizeBytes, long ageDays) throws Exception {
        Path f = dir.resolve(name);
        Files.write(f, new byte[sizeBytes]);
        Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.from(
                Instant.now().minus(ageDays, ChronoUnit.DAYS)));
    }

    @Test
    void freeRealDiskSpace_shouldReallyDeleteOldArchivesAndSpareEverythingElse(@TempDir Path logDir)
            throws Exception {
        writeFile(logDir, "platformeback-2026-08-01.0.log", 4096, 30);   // vieille archive -> supprimée
        writeFile(logDir, "platformeback-2026-08-02.0.log", 2048, 20);   // vieille archive -> supprimée
        writeFile(logDir, "platformeback-2026-09-10.0.log", 1024, 1);    // archive récente -> gardée
        writeFile(logDir, "platformeback.log", 1024, 30);                // fichier actif  -> gardé
        writeFile(logDir, "autre-appli.log", 1024, 30);                  // autre appli    -> gardée

        ReflectionTestUtils.setField(executor, "logDir", logDir.toString());
        RealActionExecutor.ActionResult result = executor.freeRealDiskSpace();

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("2 fichier(s)").contains("6,0 Ko");

        File[] remaining = logDir.toFile().listFiles();
        assertThat(remaining).isNotNull();
        assertThat(remaining).extracting(File::getName)
                .containsExactlyInAnyOrder(
                        "platformeback-2026-09-10.0.log", "platformeback.log", "autre-appli.log");
    }

    @Test
    void freeRealDiskSpace_shouldSucceedWithoutDeletingWhenNothingIsOldEnough(@TempDir Path logDir)
            throws Exception {
        writeFile(logDir, "platformeback.log", 1024, 0);
        writeFile(logDir, "platformeback-2026-09-10.0.log", 1024, 2);

        ReflectionTestUtils.setField(executor, "logDir", logDir.toString());
        RealActionExecutor.ActionResult result = executor.freeRealDiskSpace();

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("Aucun fichier à supprimer");
        assertThat(logDir.toFile().listFiles()).hasSize(2);
    }
}
