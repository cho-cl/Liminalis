package com.liminalis.plugin;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Runtime-toggleable verbose logging.
 *
 * <p>Separate from {@code config.debug} because the config value is the startup default,
 * while this can be flipped mid-session from {@code /liminalis debug} without touching a file
 * or reloading anything.
 *
 * <p>Messages are built lazily so that nothing is formatted while debugging is off.
 */
public final class Debug {

    private final JavaPlugin plugin;
    private final AtomicBoolean enabled = new AtomicBoolean();

    public Debug(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public boolean enabled() {
        return enabled.get();
    }

    public void set(boolean value) {
        enabled.set(value);
    }

    public void log(Supplier<String> message) {
        if (enabled.get()) {
            plugin.getLogger().info("[debug] " + message.get());
        }
    }
}
