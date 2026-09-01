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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Every line of text the server says to a player.
 *
 * <p>Kept in messages.yml rather than in code because this is a lore server: the wording is
 * part of the design, and it should be possible to change the voice of the whole thing without
 * a rebuild.
 *
 * <p><strong>The bundled copy is merged underneath the operator's.</strong> That is not
 * tidiness - it is a bug fix. The file is written out once on first run and never touched
 * again, so before this, every message key added in a later version was simply absent from a
 * server that had been running a while, and rendered on players' screens as
 * "[missing message: ...]". Creature names, item names and whole features looked broken while
 * being perfectly correct in the jar. Anything the operator has written wins; anything they
 * have never seen falls back to what shipped.
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

        Map<String, String> merged = new LinkedHashMap<>(bundledDefaults());
        int shipped = merged.size();

        Map<String, String> operators = flatten(YamlConfiguration.loadConfiguration(file));
        merged.putAll(operators);

        int filledIn = 0;
        for (String key : merged.keySet()) {
            if (!operators.containsKey(key)) {
                filledIn++;
            }
        }
        if (filledIn > 0) {
            plugin.getLogger().info(filledIn + " message(s) not in your messages.yml are"
                    + " using the bundled text. Delete the file to regenerate it with"
                    + " everything, or copy the lines you want to change out of the jar.");
        }

        this.entries = Map.copyOf(merged);
        String rawPrefix = merged.get(PREFIX_KEY);
        this.prefix = rawPrefix == null ? Component.empty() : mini.deserialize(rawPrefix);
        plugin.getLogger().fine(() -> "loaded " + merged.size() + " messages ("
                + shipped + " shipped)");
    }

    /** Reads the copy inside the jar, which is always complete for this build. */
    private Map<String, String> bundledDefaults() {
        try (InputStream stream = plugin.getResource(FILE_NAME)) {
            if (stream == null) {
                plugin.getLogger().severe("No bundled " + FILE_NAME
                        + " in the jar; missing keys cannot be filled in.");
                return Map.of();
            }
            return flatten(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            plugin.getLogger().severe("Could not read the bundled " + FILE_NAME
                    + ": " + e.getMessage());
            return Map.of();
        }
    }

    private static Map<String, String> flatten(YamlConfiguration yaml) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : yaml.getValues(true).entrySet()) {
            if (entry.getValue() instanceof String text) {
                values.put(entry.getKey(), text);
            }
        }
        return values;
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

    /** Whether a key is actually defined, for the startup audit. */
    public boolean has(String key) {
        return entries.containsKey(key);
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
