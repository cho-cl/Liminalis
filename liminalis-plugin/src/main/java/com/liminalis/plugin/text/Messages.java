package com.liminalis.plugin.text;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Every line of text the server says to a player.
 *
 * <p>Kept in messages.yml rather than in code because this is a lore server: the wording is
 * part of the design, and it should be possible to change the voice of the whole thing
 * without a rebuild.
 *
 * <p>A missing key renders as a loud red marker rather than an empty string. Silence would
 * look like a feature that failed to fire, and would be hunted as a bug in the wrong place.
 */
public final class Messages {

    private static final String FILE_NAME = "messages.yml";
    private static final String PREFIX_KEY = "prefix";

    private final JavaPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();

    private volatile Map<String, String> entries = Map.of();
    private volatile Component prefix = Component.empty();

    public Messages(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Re-reads messages.yml, writing out the bundled copy first if it is missing. */
    public void reload() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, String> loaded = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : yaml.getValues(true).entrySet()) {
            if (entry.getValue() instanceof String text) {
                loaded.put(entry.getKey(), text);
            }
        }

        this.entries = Map.copyOf(loaded);
        String rawPrefix = loaded.get(PREFIX_KEY);
        this.prefix = rawPrefix == null ? Component.empty() : mini.deserialize(rawPrefix);
    }

    /**
     * Renders a message.
     *
     * <p>{@code <prefix>} is available inside every message, so whether a given line carries
     * the plugin prefix is a decision made in the file rather than at each call site.
     */
    public Component get(String key, TagResolver... resolvers) {
        String raw = entries.get(key);
        if (raw == null) {
            return Component.text("[missing message: " + key + "]", NamedTextColor.RED);
        }
        TagResolver all = TagResolver.resolver(
                TagResolver.resolver(PREFIX_KEY, Tag.selfClosingInserting(prefix)),
                TagResolver.resolver(resolvers));
        return mini.deserialize(raw, all);
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        audience.sendMessage(get(key, resolvers));
    }

    /** Convenience for the common {@code <name>}-style substitution. */
    public static TagResolver placeholder(String name, String value) {
        return Placeholder.unparsed(name, value == null ? "none" : value);
    }

    public static TagResolver placeholder(String name, Component value) {
        return Placeholder.component(name, value);
    }

    public static TagResolver placeholder(String name, int value) {
        return Placeholder.unparsed(name, Integer.toString(value));
    }
}
