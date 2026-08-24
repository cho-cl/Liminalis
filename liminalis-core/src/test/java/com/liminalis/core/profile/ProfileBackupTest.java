package com.liminalis.core.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A backup that quietly does nothing is worse than no backup, because you only find out on
 * the day you need it.
 */
class ProfileBackupTest {

    @TempDir
    Path root;

    private Path profiles() throws IOException {
        Path dir = root.resolve("players");
        Files.createDirectories(dir);
        return dir;
    }

    private Path backups() {
        return root.resolve("backups");
    }

    private void writeProfile(Path dir, String name) throws IOException {
        Files.writeString(dir.resolve(name), "{}", StandardCharsets.UTF_8);
    }

    @Test
    void copiesEveryProfileIntoTheLabelledDirectory() throws IOException {
        Path profiles = profiles();
        writeProfile(profiles, "a.json");
        writeProfile(profiles, "b.json");

        Optional<Path> created = ProfileBackup.run(profiles, backups(), "2026-01-01_000000", 5);

        assertThat(created).isPresent();
        Path backup = created.orElseThrow();
        assertThat(backup.getFileName()).hasToString("2026-01-01_000000");
        assertThat(namesIn(backup)).containsExactlyInAnyOrder("a.json", "b.json");
    }

    @Test
    void leavesTheOriginalsWhereTheyAre() throws IOException {
        Path profiles = profiles();
        writeProfile(profiles, "a.json");

        ProfileBackup.run(profiles, backups(), "2026-01-01_000000", 5);

        assertThat(namesIn(profiles)).containsExactly("a.json");
    }

    @Test
    void skipsEntirelyWhenThereIsNothingToBackUp() throws IOException {
        Optional<Path> created = ProfileBackup.run(profiles(), backups(), "2026-01-01_000000", 5);

        assertThat(created).isEmpty();
        assertThat(Files.exists(backups())).isFalse();
    }

    @Test
    void skipsWhenTheServerHasNeverStoredAProfile() {
        Path neverCreated = root.resolve("players-that-do-not-exist");

        Optional<Path> created = ProfileBackup.run(neverCreated, backups(), "2026-01-01_000000", 5);

        assertThat(created).isEmpty();
    }

    @Test
    void prunesTheOldestBackupsOnceTheLimitIsPassed() throws IOException {
        Path profiles = profiles();
        writeProfile(profiles, "a.json");

        ProfileBackup.run(profiles, backups(), "2026-01-01_000000", 3);
        ProfileBackup.run(profiles, backups(), "2026-01-02_000000", 3);
        ProfileBackup.run(profiles, backups(), "2026-01-03_000000", 3);
        ProfileBackup.run(profiles, backups(), "2026-01-04_000000", 3);

        assertThat(namesIn(backups())).containsExactlyInAnyOrder(
                "2026-01-02_000000", "2026-01-03_000000", "2026-01-04_000000");
    }

    @Test
    void keepsEverythingWhenUnderTheLimit() throws IOException {
        Path profiles = profiles();
        writeProfile(profiles, "a.json");

        ProfileBackup.run(profiles, backups(), "2026-01-01_000000", 3);
        ProfileBackup.run(profiles, backups(), "2026-01-02_000000", 3);

        assertThat(namesIn(backups())).hasSize(2);
    }

    @Test
    void reusingALabelReplacesThatBackupRatherThanFailing() throws IOException {
        Path profiles = profiles();
        writeProfile(profiles, "a.json");
        ProfileBackup.run(profiles, backups(), "2026-01-01_000000", 3);

        writeProfile(profiles, "b.json");
        Optional<Path> second = ProfileBackup.run(profiles, backups(), "2026-01-01_000000", 3);

        assertThat(second).isPresent();
        assertThat(namesIn(second.orElseThrow())).containsExactlyInAnyOrder("a.json", "b.json");
        assertThat(namesIn(backups())).containsExactly("2026-01-01_000000");
    }

    private static List<String> namesIn(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.map(p -> p.getFileName().toString()).toList();
        }
    }
}
