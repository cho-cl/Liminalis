package com.liminalis.plugin.config;

import com.liminalis.core.config.ConfigParser;
import com.liminalis.core.config.ConfigResult;
import com.liminalis.core.config.LiminalisConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Holds the live config and swaps it wholesale on reload.
 *
 * <p>When a reload fails validation the previous config stays in force and the problems are
 * logged individually. Silently falling back to defaults would be worse than not reloading:
 * the server would keep running with numbers nobody chose, and it would look like it worked.
 */
public final class ConfigService {

    private final JavaPlugin plugin;

    /** Volatile because gameplay reads this from the main thread while reload replaces it. */
    private volatile LiminalisConfig current = LiminalisConfig.DEFAULTS;

    public ConfigService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** The config currently in force. Never null. */
    public LiminalisConfig get() {
        return current;
    }

    /**
     * Re-reads config.yml from disk.
     *
     * @return the problems found; empty means the new config was applied
     */
    public List<String> reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        FileConfiguration yaml = plugin.getConfig();
        Map<String, Object> values = yaml.getValues(true);

        ConfigResult result = ConfigParser.parse(values);
        if (!result.valid()) {
            plugin.getLogger().severe("config.yml was NOT applied - "
                    + result.errors().size() + " problem(s) found:");
            result.errors().forEach(error -> plugin.getLogger().severe("  - " + error));
            plugin.getLogger().severe("The previous configuration remains in force.");
            return result.errors();
        }

        current = result.config();
        return List.of();
    }

    /**
     * Writes a single value into config.yml and reloads.
     *
     * <p>Used by the handful of settings an operator flips mid-session, so the change
     * survives a restart. A runtime-only toggle would silently revert on the next reboot,
     * and the first anyone would know is a player losing a life in a duel that was meant to
     * be free.
     *
     * @return the problems found; empty means the change was saved and applied
     */
    public List<String> setAndSave(String path, Object value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
        return reload();
    }
}
