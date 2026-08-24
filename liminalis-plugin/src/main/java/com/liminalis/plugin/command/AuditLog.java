package com.liminalis.plugin.command;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Level;

/**
 * An append-only record of every admin change.
 *
 * <p>Worth having because of how this server works: abilities are hand-assigned, technical
 * deaths are excused by judgement, and a season runs for months. "Who gave them that, and
 * when?" is a question that will be asked, and memory will not answer it.
 *
 * <p>Written synchronously. These entries are rare, tiny, and must not be lost to a crash
 * that happens right after the change they describe.
 */
public final class AuditLog {

    private static final String FILE_NAME = "audit.log";

    private final JavaPlugin plugin;
    private final Path file;

    public AuditLog(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.file = plugin.getDataFolder().toPath().resolve(FILE_NAME);
    }

    /**
     * Records a change.
     *
     * @param operator who ran the command
     * @param action   dotted action name, e.g. {@code lives.set}
     * @param target   who it was applied to
     * @param before   the value before, or null if not applicable
     * @param after    the value after, or null if not applicable
     */
    public void record(String operator, String action, String target,
                       String before, String after) {
        StringBuilder line = new StringBuilder()
                .append(Instant.now())
                .append("\toperator=").append(operator)
                .append("\taction=").append(action)
                .append("\ttarget=").append(target);
        if (before != null || after != null) {
            line.append("\tbefore=").append(before).append("\tafter=").append(after);
        }
        line.append(System.lineSeparator());

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Never fail the command because the log could not be written - but do make the
            // gap in the record obvious in the server log.
            plugin.getLogger().log(Level.SEVERE,
                    "Could not write audit entry: " + line.toString().trim(), e);
        }
    }

    /** Records an action with no meaningful before/after, such as a reload. */
    public void record(String operator, String action, String target) {
        record(operator, action, target, null, null);
    }

    public Path file() {
        return file;
    }
}
