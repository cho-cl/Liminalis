package com.liminalis.plugin.command;

import com.liminalis.core.combat.CombatSettings;
import com.liminalis.core.command.ConfirmationTracker;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.core.profile.ProfileCodec;
import com.liminalis.core.profile.ProfileModifierIds;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The {@code /liminalis} admin tree.
 *
 * <p>Four properties matter more than the individual commands:
 *
 * <ul>
 *   <li><strong>Offline players work everywhere.</strong> Targets are resolved against stored
 *       profiles, so abilities can be assigned while the recipient is asleep.</li>
 *   <li><strong>Every mutation is audited</strong> with before and after values.</li>
 *   <li><strong>Destructive commands must be repeated</strong> inside a short window.</li>
 *   <li><strong>Reads and writes are separate.</strong> Anything named get/list/inspect is
 *       safe to explore with.</li>
 * </ul>
 *
 * <p>Text here is diagnostic tooling rather than the server's voice, so it lives in code
 * rather than messages.yml. Lore that players read goes in the file; operator output does not.
 */
@SuppressWarnings("UnstableApiUsage")
public final class LiminalisCommand {

    private static final NamedTextColor ACCENT = NamedTextColor.AQUA;
    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;
    private static final NamedTextColor GOOD = NamedTextColor.GREEN;
    private static final NamedTextColor BAD = NamedTextColor.RED;
    private static final NamedTextColor WARN = NamedTextColor.YELLOW;

    private final ConfigService config;
    private final Messages messages;
    private final ProfileManager profiles;
    private final ModifierService modifiers;
    private final AuditLog audit;
    private final ConfirmationTracker confirmations;
    private final Debug debug;
    private final Supplier<Optional<java.nio.file.Path>> backupRunner;
    private final ProfileCodec codec = new ProfileCodec();

    public LiminalisCommand(ConfigService config,
                            Messages messages,
                            ProfileManager profiles,
                            ModifierService modifiers,
                            AuditLog audit,
                            ConfirmationTracker confirmations,
                            Debug debug,
                            Supplier<Optional<java.nio.file.Path>> backupRunner) {
        this.config = Objects.requireNonNull(config);
        this.messages = Objects.requireNonNull(messages);
        this.profiles = Objects.requireNonNull(profiles);
        this.modifiers = Objects.requireNonNull(modifiers);
        this.audit = Objects.requireNonNull(audit);
        this.confirmations = Objects.requireNonNull(confirmations);
        this.debug = Objects.requireNonNull(debug);
        this.backupRunner = Objects.requireNonNull(backupRunner);
    }

    public LiteralCommandNode<CommandSourceStack> build(LivesAndLimboCommands extra,
                                                       TraitCommands traits,
                                                       BoonCommands boons) {
        return Commands.literal("liminalis")
                .requires(source -> source.getSender().hasPermission("liminalis.admin"))
                .executes(this::showOverview)
                .then(reloadTree())
                .then(profileTree())
                .then(debugTree())
                .then(dataTree())
                .then(extra.livesTree())
                .then(extra.limboTree())
                .then(traits.tree())
                .then(boons.tree())
                .build();
    }

    // --------------------------------------------------------------------------- reload

    private LiteralArgumentBuilder<CommandSourceStack> reloadTree() {
        return Commands.literal("reload")
                .requires(permission("liminalis.admin.reload"))
                .executes(context -> reload(context, true, true))
                .then(Commands.literal("config")
                        .executes(context -> reload(context, true, false)))
                .then(Commands.literal("messages")
                        .executes(context -> reload(context, false, true)))
                .then(Commands.literal("all")
                        .executes(context -> reload(context, true, true)));
    }

    private int reload(CommandContext<CommandSourceStack> context,
                       boolean doConfig, boolean doMessages) {
        CommandSender sender = context.getSource().getSender();

        if (doConfig) {
            List<String> errors = config.reload();
            if (!errors.isEmpty()) {
                sender.sendMessage(Component.text(
                        "config.yml was NOT applied. The previous config is still in force:",
                        BAD));
                errors.forEach(error ->
                        sender.sendMessage(Component.text("  - " + error, BAD)));
                audit.record(nameOf(sender), "reload.config.rejected", "-",
                        null, errors.size() + " problem(s)");
                return Command.SINGLE_SUCCESS;
            }
            sender.sendMessage(Component.text("config.yml reloaded.", GOOD));
        }

        if (doMessages) {
            messages.reload();
            sender.sendMessage(Component.text("messages.yml reloaded.", GOOD));
        }

        audit.record(nameOf(sender), "reload", (doConfig ? "config " : "") + (doMessages ? "messages" : ""));
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------- profile

    private LiteralArgumentBuilder<CommandSourceStack> profileTree() {
        return Commands.literal("profile")
                .requires(permission("liminalis.admin.profile"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(knownPlayers())
                        .executes(this::showProfile));
    }

    private int showProfile(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String requested = StringArgumentType.getString(context, "player");

        PlayerProfile profile = resolveProfile(sender, requested);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }

        boolean online = isOnline(profile.id());
        sender.sendMessage(Component.text("── " + profile.lastKnownName() + " ──", ACCENT));
        sender.sendMessage(field("id", profile.id().toString()));
        sender.sendMessage(field("status", online ? "online" : "offline"));
        sender.sendMessage(field("lives", profile.livesRemaining()
                + " / " + config.get().lives().startingLives()));
        sender.sendMessage(field("deaths", Integer.toString(profile.totalDeaths())));
        sender.sendMessage(field("in limbo", Boolean.toString(profile.inLimbo())));
        sender.sendMessage(field("traits", joinOrNone(profile.traitIds())));
        sender.sendMessage(field("blessing", orNone(profile.blessingId())));
        sender.sendMessage(field("curse", orNone(profile.curseId())));
        sender.sendMessage(field("marks", joinOrNone(profile.markIds())));
        sender.sendMessage(field("ability", orNone(profile.abilityId())
                + (profile.abilityId() == null ? "" : " (tier " + profile.abilityTier() + ")")));
        sender.sendMessage(field("first join done", Boolean.toString(profile.firstJoinComplete())));

        int referenced = ProfileModifierIds.referencedBy(profile).size();
        int attachedNow = online ? modifiers.attachedTo(playerOf(profile.id())).size() : -1;
        if (online && attachedNow != referenced) {
            // Worth surfacing: it means some id in the profile has no code in this build.
            sender.sendMessage(Component.text("  " + referenced + " modifier(s) referenced, "
                    + attachedNow + " attached - see the server log for unknown ids.", WARN));
        }
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------------------- debug

    private LiteralArgumentBuilder<CommandSourceStack> debugTree() {
        return Commands.literal("debug")
                .requires(permission("liminalis.admin.debug"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(Component.text(
                            "Verbose logging is " + (debug.enabled() ? "on" : "off") + ".",
                            LABEL));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("on").executes(context -> setDebug(context, true)))
                .then(Commands.literal("off").executes(context -> setDebug(context, false)));
    }

    private int setDebug(CommandContext<CommandSourceStack> context, boolean value) {
        CommandSender sender = context.getSource().getSender();
        boolean before = debug.enabled();
        debug.set(value);
        sender.sendMessage(Component.text(
                "Verbose logging " + (value ? "enabled" : "disabled") + ".", GOOD));
        audit.record(nameOf(sender), "debug", "-",
                Boolean.toString(before), Boolean.toString(value));
        return Command.SINGLE_SUCCESS;
    }

    // ----------------------------------------------------------------------------- data

    private LiteralArgumentBuilder<CommandSourceStack> dataTree() {
        return Commands.literal("data")
                .requires(permission("liminalis.admin.data"))
                .then(Commands.literal("save")
                        .then(Commands.literal("all").executes(this::saveAll))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(knownPlayers())
                                .executes(this::saveOne)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(knownPlayers())
                                .executes(this::inspect)))
                .then(Commands.literal("backup").executes(this::backup))
                .then(Commands.literal("reloadfile")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(knownPlayers())
                                .executes(this::reloadFile)));
    }

    private int saveAll(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        int count = 0;
        for (PlayerProfile profile : profiles.residentProfiles()) {
            profiles.saveNow(profile);
            count++;
        }
        sender.sendMessage(Component.text("Saved " + count + " resident profile(s).", GOOD));
        audit.record(nameOf(sender), "data.save.all", "-", null, count + " profile(s)");
        return Command.SINGLE_SUCCESS;
    }

    private int saveOne(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = resolveProfile(sender,
                StringArgumentType.getString(context, "player"));
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        profiles.saveNow(profile);
        sender.sendMessage(Component.text("Saved " + profile.lastKnownName() + ".", GOOD));
        audit.record(nameOf(sender), "data.save", describe(profile));
        return Command.SINGLE_SUCCESS;
    }

    private int inspect(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = resolveProfile(sender,
                StringArgumentType.getString(context, "player"));
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text("── raw profile: "
                + profile.lastKnownName() + " ──", ACCENT));
        for (String line : codec.toJson(profile).split("\n")) {
            sender.sendMessage(Component.text(line, VALUE));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int backup(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Optional<java.nio.file.Path> created = backupRunner.get();
        created.ifPresentOrElse(
                path -> sender.sendMessage(Component.text(
                        "Backed up to " + path.getFileName() + ".", GOOD)),
                () -> sender.sendMessage(Component.text(
                        "Nothing to back up - no profiles stored yet.", WARN)));
        audit.record(nameOf(sender), "data.backup", "-", null,
                created.map(path -> path.getFileName().toString()).orElse("skipped"));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Discards the in-memory profile and re-reads it from disk.
     *
     * <p>Destructive: anything not yet written is lost, which for an online player can mean
     * a life spent this session. Hence the confirmation.
     */
    private int reloadFile(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String requested = StringArgumentType.getString(context, "player");

        Optional<UUID> id = profiles.resolve(requested);
        if (id.isEmpty()) {
            sender.sendMessage(unknownPlayer(requested));
            return Command.SINGLE_SUCCESS;
        }

        if (!confirmations.submit(nameOf(sender), "data.reloadfile:" + id.get())) {
            sender.sendMessage(Component.text("This discards any unsaved changes for "
                    + requested + ".", WARN));
            sender.sendMessage(Component.text("Run the same command again within "
                    + confirmations.windowSeconds() + "s to confirm.", WARN));
            return Command.SINGLE_SUCCESS;
        }

        Player online = playerOf(id.get());
        if (online != null) {
            sender.sendMessage(Component.text(
                    requested + " is online; their live profile cannot be swapped safely."
                            + " Have them log out first.", BAD));
            return Command.SINGLE_SUCCESS;
        }

        Optional<PlayerProfile> reloaded = profiles.lookup(id.get());
        reloaded.ifPresentOrElse(
                profile -> sender.sendMessage(Component.text(
                        "Re-read profile for " + profile.lastKnownName() + " from disk.", GOOD)),
                () -> sender.sendMessage(Component.text(
                        "No stored profile for " + requested + ".", BAD)));
        audit.record(nameOf(sender), "data.reloadfile", requested + "(" + id.get() + ")");
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------------- overview

    private int showOverview(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("── Liminalis ──", ACCENT));
        sender.sendMessage(field("known profiles", Integer.toString(profiles.knownIds().size())));
        sender.sendMessage(field("resident", Integer.toString(profiles.residentProfiles().size())));
        sender.sendMessage(field("starting lives", Integer.toString(config.get().lives().startingLives())));

        CombatSettings combat = config.get().combat();
        sender.sendMessage(field("pvp damage", "x" + combat.pvpDamageMultiplier()
                + indirectSummary(combat)));
        sender.sendMessage(field("food healing", "x" + combat.foodHealingMultiplier()));
        sender.sendMessage(field("regeneration", "x" + combat.regenerationMultiplier()));

        sender.sendMessage(field("verbose logging", debug.enabled() ? "on" : "off"));
        sender.sendMessage(Component.text(
                "Subcommands: reload, profile, debug, data, lives, limbo, trait, boon", LABEL));
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------- helpers

    private java.util.function.Predicate<CommandSourceStack> permission(String node) {
        return source -> source.getSender().hasPermission(node);
    }

    public SuggestionProvider<CommandSourceStack> knownPlayers() {
        return (context, builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (String name : profiles.knownNames()) {
                if (name.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }

    /** Resolves and loads, reporting to the sender and returning null if it cannot. */
    private PlayerProfile resolveProfile(CommandSender sender, String requested) {
        Optional<UUID> id = profiles.resolve(requested);
        if (id.isEmpty()) {
            sender.sendMessage(unknownPlayer(requested));
            return null;
        }
        try {
            Optional<PlayerProfile> profile = profiles.lookup(id.get());
            if (profile.isEmpty()) {
                sender.sendMessage(Component.text(
                        "No stored profile for " + requested + ".", BAD));
                return null;
            }
            return profile.get();
        } catch (RuntimeException e) {
            sender.sendMessage(Component.text(
                    "Profile for " + requested + " could not be read: " + e.getMessage(), BAD));
            return null;
        }
    }

    private Component unknownPlayer(String requested) {
        return Component.text("Liminalis has never seen '" + requested
                + "'. Use a name it knows, or a raw UUID.", BAD);
    }

    /** Names the indirect sources that are NOT counted, since those are the surprising ones. */
    private static String indirectSummary(CombatSettings combat) {
        List<String> excluded = new java.util.ArrayList<>();
        if (!combat.includeProjectiles()) {
            excluded.add("projectiles");
        }
        if (!combat.includePets()) {
            excluded.add("pets");
        }
        if (!combat.includeExplosives()) {
            excluded.add("explosives");
        }
        return excluded.isEmpty() ? "" : " (excluding " + String.join(", ", excluded) + ")";
    }

    private Component field(String label, String value) {
        return Component.text("  " + label + ": ", LABEL).append(Component.text(value, VALUE));
    }

    private static String orNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static String joinOrNone(java.util.Collection<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String describe(PlayerProfile profile) {
        return profile.lastKnownName() + "(" + profile.id() + ")";
    }

    private static String nameOf(CommandSender sender) {
        return sender.getName();
    }

    private boolean isOnline(UUID id) {
        return playerOf(id) != null;
    }

    private Player playerOf(UUID id) {
        return org.bukkit.Bukkit.getPlayer(id);
    }
}
