package com.liminalis.plugin;

import com.liminalis.core.command.ConfirmationTracker;
import com.liminalis.core.profile.JsonProfileStore;
import com.liminalis.core.profile.ProfileBackup;
import com.liminalis.core.profile.ProfileStore;
import com.liminalis.plugin.combat.CombatListener;
import com.liminalis.plugin.command.AuditLog;
import com.liminalis.plugin.command.LimboPlayerCommand;
import com.liminalis.plugin.command.LiminalisCommand;
import com.liminalis.plugin.command.LivesAndLimboCommands;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.limbo.GhostVisitService;
import com.liminalis.plugin.limbo.LimboChatListener;
import com.liminalis.plugin.limbo.LimboService;
import com.liminalis.plugin.limbo.LimboWorld;
import com.liminalis.plugin.lives.DeathListener;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Wires the foundation together.
 *
 * <p>Deliberately dull: everything here is construction and ordering. Behaviour lives in the
 * services, and the rules those services depend on live in {@code liminalis-core}, where they
 * are tested without a server.
 */
@SuppressWarnings("UnstableApiUsage")
public final class LiminalisPlugin extends JavaPlugin {

    /** Sorts lexicographically by age, which is what backup pruning relies on. */
    private static final DateTimeFormatter BACKUP_LABEL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").withZone(ZoneId.systemDefault());

    private static final long CONFIRMATION_WINDOW_MILLIS = 10_000L;

    private ConfigService config;
    private Messages messages;
    private ProfileManager profiles;
    private ModifierService modifiers;
    private Debug debug;
    private Path playersDirectory;
    private LimboWorld limboWorld;
    private LimboService limbo;
    private GhostVisitService ghosts;

    @Override
    public void onEnable() {
        config = new ConfigService(this);
        List<String> configErrors = config.reload();
        if (!configErrors.isEmpty()) {
            // Running on defaults would be worse than not running: the wrong numbers would be
            // applied to real players and written into their profiles permanently, and it
            // would look like everything worked. Refuse to start instead.
            getLogger().severe("Liminalis will NOT enable until config.yml is valid.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        messages = new Messages(this);
        messages.reload();

        debug = new Debug(this);
        debug.set(config.get().debug());

        playersDirectory = getDataFolder().toPath().resolve("players");
        ProfileStore store = new JsonProfileStore(playersDirectory);

        profiles = new ProfileManager(this, store, config);
        profiles.indexStoredProfiles();

        if (config.get().backupOnStart()) {
            runBackup().ifPresent(path ->
                    getLogger().info("Backed up profiles to " + path.getFileName() + "."));
        }

        ModifierRegistry registry = new ModifierRegistry();
        modifiers = new ModifierService(this, registry, profiles);

        limboWorld = new LimboWorld(this, config);
        limboWorld.createOrLoad();
        limbo = new LimboService(this, profiles, limboWorld, messages, debug);
        ghosts = new GhostVisitService(this, config, profiles, limbo, messages, debug);

        getServer().getPluginManager().registerEvents(profiles, this);
        getServer().getPluginManager().registerEvents(modifiers, this);
        getServer().getPluginManager().registerEvents(new CombatListener(config, debug), this);
        getServer().getPluginManager().registerEvents(limbo, this);
        getServer().getPluginManager().registerEvents(ghosts, this);
        getServer().getPluginManager().registerEvents(
                new LimboChatListener(this, config, profiles, messages, debug), this);
        getServer().getPluginManager().registerEvents(
                new DeathListener(this, config, profiles, messages, debug), this);
        modifiers.start();

        registerCommands(registry);

        getLogger().info("Liminalis enabled - " + registry.size() + " modifier(s) registered.");
    }

    @Override
    public void onDisable() {
        // Order matters: strip attribute modifiers before the profiles are flushed, so a
        // player's saved state never includes bonuses that only exist while we are running.
        if (ghosts != null) {
            ghosts.shutdown();
        }
        if (modifiers != null) {
            modifiers.stop();
        }
        if (profiles != null) {
            profiles.shutdown();
        }
    }

    private void registerCommands(ModifierRegistry registry) {
        AuditLog audit = new AuditLog(this);
        ConfirmationTracker confirmations =
                new ConfirmationTracker(CONFIRMATION_WINDOW_MILLIS, System::currentTimeMillis);

        LiminalisCommand command = new LiminalisCommand(
                config, messages, profiles, modifiers, audit, confirmations, debug,
                this::runBackup);
        LivesAndLimboCommands livesAndLimbo = new LivesAndLimboCommands(
                config, profiles, limbo, audit, confirmations, command.knownPlayers());
        LimboPlayerCommand limboCommand =
                new LimboPlayerCommand(profiles, limbo, ghosts, messages);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    command.build(livesAndLimbo),
                    "Liminalis administration",
                    List.of("lim"));
            event.registrar().register(
                    limboCommand.build(),
                    "For those with no lives left");
        });
    }

    private Optional<Path> runBackup() {
        return ProfileBackup.run(
                playersDirectory,
                getDataFolder().toPath().resolve("backups"),
                BACKUP_LABEL.format(Instant.now()),
                config.get().keepBackups());
    }

    public Debug debug() {
        return debug;
    }
}
