package com.liminalis.plugin;

import com.liminalis.core.command.ConfirmationTracker;
import com.liminalis.core.profile.JsonProfileStore;
import com.liminalis.core.profile.ProfileBackup;
import com.liminalis.core.profile.ProfileStore;
import com.liminalis.plugin.ability.AbilityService;
import com.liminalis.plugin.ability.DronesAbility;
import com.liminalis.plugin.ability.drones.DroneService;
import com.liminalis.plugin.ability.PriestAbility;
import com.liminalis.plugin.boon.Blessings;
import com.liminalis.plugin.boon.Curses;
import com.liminalis.plugin.combat.CombatListener;
import com.liminalis.plugin.command.AbilityCommand;
import com.liminalis.plugin.command.AdminCommand;
import com.liminalis.plugin.command.AuditLog;
import com.liminalis.plugin.command.ItemsMenu;
import com.liminalis.plugin.command.LimboPlayerCommand;
import com.liminalis.plugin.command.ProfileCommand;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.injury.Injuries;
import com.liminalis.plugin.injury.OffhandBlocker;
import com.liminalis.plugin.hud.PlayerHud;
import com.liminalis.plugin.injury.InjuryService;
import com.liminalis.plugin.rescue.RescueService;
import com.liminalis.plugin.rescue.ThresholdStone;
import com.liminalis.plugin.singularity.LoreBooks;
import com.liminalis.plugin.singularity.SingularityService;
import com.liminalis.plugin.limbo.GhostVisitService;
import com.liminalis.plugin.limbo.LimboChatListener;
import com.liminalis.plugin.limbo.LimboService;
import com.liminalis.plugin.limbo.LimboWorld;
import com.liminalis.plugin.lives.DeathListener;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import com.liminalis.plugin.trait.FirstJoinService;
import com.liminalis.plugin.trait.MarkOfReturn;
import com.liminalis.plugin.trait.MoreOrdinaryTraits;
import com.liminalis.plugin.trait.OrdinaryTraits;
import com.liminalis.plugin.trait.SingularityTraits;
import com.liminalis.plugin.trait.TraitTuning;
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
    private InjuryService injuries;
    private SingularityService singularity;
    private DroneService droneService;
    private RescueService rescue;
    private AbilityService abilities;
    private PlayerHud hud;
    private OffhandBlocker offhand;
    private ModifierRegistry registry;

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

        limboWorld = new LimboWorld(this, config);
        limboWorld.createOrLoad();

        registry = new ModifierRegistry();
        modifiers = new ModifierService(this, registry, profiles, messages);
        limbo = new LimboService(this, profiles, limboWorld, modifiers, messages, debug);
        ghosts = new GhostVisitService(this, config, profiles, limbo, messages, debug);

        singularity = new SingularityService(this, config, profiles, limboWorld, registry, modifiers, messages, debug);
        droneService = new DroneService(this,
                new TraitTuning(() -> config.get().traits().tuning()),
                profiles, messages, debug);
        registerModifiers();
        injuries = new InjuryService(this, config, profiles, registry, modifiers, messages, debug);
        rescue = new RescueService(this, config, profiles, limbo, limboWorld, registry, messages, debug);
        ThresholdStone.registerRecipe(this, messages);
        abilities = new AbilityService(this, config, profiles, registry, modifiers,
                rescue, messages, debug);
        hud = new PlayerHud(this, config, profiles, registry, singularity);
        offhand = new OffhandBlocker(this, profiles, messages);

        getServer().getPluginManager().registerEvents(profiles, this);
        getServer().getPluginManager().registerEvents(modifiers, this);
        getServer().getPluginManager().registerEvents(new CombatListener(config, debug), this);
        getServer().getPluginManager().registerEvents(limbo, this);
        getServer().getPluginManager().registerEvents(ghosts, this);
        getServer().getPluginManager().registerEvents(
                new LimboChatListener(this, config, profiles, messages, debug), this);
        getServer().getPluginManager().registerEvents(
                new DeathListener(this, config, profiles, modifiers, messages, debug), this);
        getServer().getPluginManager().registerEvents(new FirstJoinService(
                this, config, profiles, registry, modifiers, messages, debug), this);
        getServer().getPluginManager().registerEvents(injuries, this);
        getServer().getPluginManager().registerEvents(singularity, this);
        getServer().getPluginManager().registerEvents(droneService, this);
        getServer().getPluginManager().registerEvents(droneService.control(), this);
        getServer().getPluginManager().registerEvents(rescue, this);
        getServer().getPluginManager().registerEvents(abilities, this);
        getServer().getPluginManager().registerEvents(offhand, this);
        modifiers.start();
        injuries.start();
        singularity.start();
        droneService.start();
        rescue.start();
        hud.start();
        offhand.start();

        // Validates every page fits a real book. Called here rather than left until the
        // first one drops, so an unreadable page is a startup failure on the machine of
        // whoever wrote it instead of a mystery on a player's screen weeks later.
        LoreBooks.all();

        // Same argument, sharper case. A damage category with no wound behind it produces a
        // blow that is classified as maiming and then inflicts nothing at all, and there is
        // no error, no log line and no way for a player to tell. Refusing to start is the
        // only version of this failure anybody ever finds out about.
        Injuries.validate(new TraitTuning(() -> config.get().traits().tuning()));

        registerCommands();

        getLogger().info("Liminalis enabled - " + registry.size() + " modifier(s) registered.");
    }

    @Override
    public void onDisable() {
        // Order matters: strip attribute modifiers before the profiles are flushed, so a
        // player's saved state never includes bonuses that only exist while we are running.
        if (offhand != null) {
            offhand.stop();
        }
        if (hud != null) {
            hud.stop();
        }
        if (rescue != null) {
            rescue.stop();
        }
        if (singularity != null) {
            droneService.stop();
            singularity.stop();
        }
        if (injuries != null) {
            injuries.stop();
        }
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

    /**
     * Builds every modifier this build knows how to apply.
     *
     * <p>Tuning is read through a supplier rather than copied, so /liminalis reload
     * rebalances a live server instead of merely reporting that it did.
     */
    private void registerModifiers() {
        TraitTuning tuning = new TraitTuning(() -> config.get().traits().tuning());
        OrdinaryTraits.all(tuning).forEach(registry::register);
        MoreOrdinaryTraits.all(tuning).forEach(registry::register);
        SingularityTraits.all(tuning, limbo, singularity).forEach(registry::register);
        Blessings.all(tuning).forEach(registry::register);
        Curses.all(tuning).forEach(registry::register);
        Injuries.all(tuning).forEach(registry::register);
        registry.register(new PriestAbility(this, tuning, profiles, registry, modifiers, messages));
        registry.register(new DronesAbility(tuning, droneService, messages));
        registry.register(new MarkOfReturn(tuning, limbo));
    }

    private void registerCommands() {
        AuditLog audit = new AuditLog(this);
        ConfirmationTracker confirmations =
                new ConfirmationTracker(CONFIRMATION_WINDOW_MILLIS, System::currentTimeMillis);

        ItemsMenu itemsMenu = new ItemsMenu(this, messages);
        getServer().getPluginManager().registerEvents(itemsMenu, this);

        AdminCommand admin = new AdminCommand(this, config, messages, profiles, registry,
                modifiers, abilities, limbo, limboWorld, singularity, itemsMenu, audit,
                confirmations, debug, this::runBackup);
        LimboPlayerCommand limboCommand =
                new LimboPlayerCommand(profiles, limbo, ghosts, rescue, modifiers, messages);
        ProfileCommand profileCommand =
                new ProfileCommand(config, profiles, registry, messages);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    admin.build(),
                    "Liminalis administration",
                    List.of("lim"));
            event.registrar().register(
                    limboCommand.build(),
                    "For those with no lives left");
            event.registrar().register(
                    profileCommand.build(),
                    "What you are");
            event.registrar().register(
                    new AbilityCommand(profiles, abilities, messages).build(),
                    "Use your ability");
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
