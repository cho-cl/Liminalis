package com.liminalis.core.profile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Copies the profile directory aside on server start, and prunes old copies.
 *
 * <p>The whole point is cheap insurance against the failure modes that a per-file atomic
 * write cannot help with: a bad migration, a mistaken admin command applied to everyone, or
 * a disk that loses the directory. Restoring is a folder copy.
 *
 * <p>Takes its timestamp label as a parameter rather than reading the clock, so the caller
 * decides the naming and these rules stay testable.
 */
public final class ProfileBackup {

    private ProfileBackup() {
    }

    /**
     * Backs up {@code profiles} into {@code backupsRoot/label}, then keeps only the newest
     * {@code keep} backups by name.
     *
     * @return the directory created, or empty if there was nothing worth backing up
     */
    public static Optional<Path> run(Path profiles, Path backupsRoot, String label, int keep) {
        if (!Files.isDirectory(profiles) || isEmpty(profiles)) {
            return Optional.empty();
        }
        try {
            Path destination = backupsRoot.resolve(label);
            deleteRecursively(destination);
            Files.createDirectories(destination);
            copyProfiles(profiles, destination);
            prune(backupsRoot, keep);
            return Optional.of(destination);
        } catch (IOException e) {
            throw new UncheckedIOException("could not back up profiles to " + backupsRoot, e);
        }
    }

    private static void copyProfiles(Path profiles, Path destination) throws IOException {
        try (Stream<Path> entries = Files.list(profiles)) {
            for (Path source : entries.filter(Files::isRegularFile).toList()) {
                Files.copy(source, destination.resolve(source.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Deletes the oldest backups. Labels are timestamps, so sorting them by name sorts them
     * by age - which is exactly why the caller must supply a label that sorts that way.
     */
    private static void prune(Path backupsRoot, int keep) throws IOException {
        List<Path> existing;
        try (Stream<Path> entries = Files.list(backupsRoot)) {
            existing = entries.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        int excess = existing.size() - keep;
        for (int i = 0; i < excess; i++) {
            deleteRecursively(existing.get(i));
        }
    }

    private static boolean isEmpty(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            throw new UncheckedIOException("could not inspect " + directory, e);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }
}
