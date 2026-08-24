package com.liminalis.core.profile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Stores one JSON document per player, named for their UUID.
 *
 * <p>Writes go to a temporary file, are flushed to the disk itself, and are then moved over
 * the real file in a single atomic operation. The failure this prevents is specific and
 * realistic: the server is killed mid-write, and a player's profile is left half-written and
 * unreadable. With the swap, an interrupted save leaves the previous good profile untouched.
 *
 * <p>Flat files are the right choice at this scale for a reason beyond simplicity - the
 * Creator hand-assigns abilities, and a file you can open in a text editor is worth more
 * here than a database you have to query.
 */
public final class JsonProfileStore implements ProfileStore {

    private static final String EXTENSION = ".json";
    private static final String TEMP_EXTENSION = ".tmp";

    private final Path directory;
    private final ProfileCodec codec;

    public JsonProfileStore(Path directory) {
        this(directory, new ProfileCodec());
    }

    public JsonProfileStore(Path directory, ProfileCodec codec) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /** The directory profiles are read from and written to. */
    public Path directory() {
        return directory;
    }

    @Override
    public Optional<PlayerProfile> load(UUID id) {
        Path file = fileFor(id);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ProfileStorageException("could not read profile " + file, e);
        }
        // A ProfileFormatException from here is deliberately allowed to propagate: a
        // corrupt profile must stop the login, not be replaced with a blank one.
        return Optional.of(codec.fromJson(json));
    }

    @Override
    public void save(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        Path target = fileFor(profile.id());
        Path temp = target.resolveSibling(target.getFileName() + TEMP_EXTENSION);
        String json = codec.toJson(profile);

        try {
            Files.createDirectories(directory);
            writeDurably(temp, json);
            moveOver(temp, target);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new ProfileStorageException("could not write profile " + target, e);
        }
    }

    @Override
    public Set<UUID> knownIds() {
        if (!Files.isDirectory(directory)) {
            return Set.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        try (Stream<Path> entries = Files.list(directory)) {
            entries.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(EXTENSION))
                    .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                    .forEach(stem -> parseUuid(stem).ifPresent(ids::add));
        } catch (IOException e) {
            throw new ProfileStorageException("could not list profiles in " + directory, e);
        }
        return ids;
    }

    private Path fileFor(UUID id) {
        return directory.resolve(id + EXTENSION);
    }

    /** Writes and forces to disk, so an abrupt shutdown cannot leave an empty file. */
    private static void writeDurably(Path temp, String json) throws IOException {
        try (OutputStream out = Files.newOutputStream(temp,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
                StandardOpenOption.SYNC)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void moveOver(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Some filesystems cannot do it atomically. A plain replace is still far better
            // than writing over the live file in place.
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Nothing useful to do; the failed save is already being reported.
        }
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Thrown when the filesystem, rather than the file's contents, is the problem. */
    public static final class ProfileStorageException extends RuntimeException {

        public ProfileStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
