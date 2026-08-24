package com.liminalis.plugin.command;

import com.liminalis.core.command.ConfirmationTracker;
import com.liminalis.core.limbo.GhostVisitRules;
import com.liminalis.core.lives.LifeRules;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.limbo.LimboService;
import com.liminalis.plugin.profile.ProfileManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The {@code lives} and {@code limbo} branches of the admin tree.
 *
 * <p>Split out of {@link LiminalisCommand} to keep either file readable. Everything here
 * follows the same rules as the rest of the tree: offline players work, mutations are
 * audited with before and after values, and anything that could destroy someone's run has
 * to be typed twice.
 *
 * <p>Both {@code lives set} and {@code limbo send} work on offline players by writing the
 * profile. Enforcement then happens when they next log in, which is also what makes Limbo
 * inescapable in the first place.
 */
@SuppressWarnings("UnstableApiUsage")
public final class LivesAndLimboCommands {

    private static final NamedTextColor ACCENT = NamedTextColor.AQUA;
    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;
    private static final NamedTextColor GOOD = NamedTextColor.GREEN;
    private static final NamedTextColor BAD = NamedTextColor.RED;
    private static final NamedTextColor WARN = NamedTextColor.YELLOW;

    private static final String MARK_OF_RETURN = "mark_of_return";

    private final ConfigService config;
    private final ProfileManager profiles;
    private final LimboService limbo;
    private final AuditLog audit;
    private final ConfirmationTracker confirmations;
    private final SuggestionProvider<CommandSourceStack> playerNames;

    public LivesAndLimboCommands(ConfigService config,
                                 ProfileManager profiles,
                                 LimboService limbo,
                                 AuditLog audit,
                                 ConfirmationTracker confirmations,
                                 SuggestionProvider<CommandSourceStack> playerNames) {
        this.config = Objects.requireNonNull(config);
        this.profiles = Objects.requireNonNull(profiles);
        this.limbo = Objects.requireNonNull(limbo);
        this.audit = Objects.requireNonNull(audit);
        this.confirmations = Objects.requireNonNull(confirmations);
        this.playerNames = Objects.requireNonNull(playerNames);
    }

    // ----------------------------------------------------------------------------- lives

    public LiteralArgumentBuilder<CommandSourceStack> livesTree() {
        return Commands.literal("lives")
                .requires(permission("liminalis.admin.lives"))
                .then(Commands.literal("get")
                        .then(target().executes(this::getLives)))
                .then(Commands.literal("set")
                        .then(target().then(Commands.argument("amount",
                                        IntegerArgumentType.integer(0, 100))
                                .executes(this::setLives))))
                .then(Commands.literal("give")
                        .then(target().executes(context -> adjustLives(context, 1))
                                .then(Commands.argument("amount",
                                                IntegerArgumentType.integer(1, 100))
                                        .executes(context -> adjustLives(context,
                                                IntegerArgumentType.getInteger(context, "amount"))))))
                .then(Commands.literal("take")
                        .then(target().executes(context -> adjustLives(context, -1))
                                .then(Commands.argument("amount",
                                                IntegerArgumentType.integer(1, 100))
                                        .executes(context -> adjustLives(context,
                                                -IntegerArgumentType.getInteger(context, "amount"))))))
                .then(Commands.literal("pvpcounts")
                        .executes(this::showPvpCounts)
                        .then(Commands.literal("on").executes(c -> setPvpCounts(c, true)))
                        .then(Commands.literal("off").executes(c -> setPvpCounts(c, false))));
    }

    private int getLives(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = resolve(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text(profile.lastKnownName() + ": ", LABEL)
                .append(Component.text(profile.livesRemaining() + " / "
                        + config.get().lives().startingLives() + " lives", VALUE))
                .append(Component.text(profile.inLimbo() ? "  (in Limbo)" : "", WARN)));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Sets lives outright.
     *
     * <p>Setting someone to zero does <em>not</em> send them to Limbo on its own - that would
     * make a typo catastrophic. Use {@code limbo send} to actually condemn somebody.
     */
    private int setLives(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = resolve(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        int amount = IntegerArgumentType.getInteger(context, "amount");

        if (amount == 0 && !confirmed(sender, "lives.set0:" + profile.id())) {
            sender.sendMessage(Component.text("That leaves " + profile.lastKnownName()
                    + " on zero lives - their next death sends them to Limbo.", WARN));
            sender.sendMessage(repeatPrompt());
            return Command.SINGLE_SUCCESS;
        }

        int before = profile.livesRemaining();
        profile.setLivesRemaining(amount);
        profiles.saveNow(profile);

        sender.sendMessage(Component.text(profile.lastKnownName() + ": " + before
                + " -> " + amount + " lives", GOOD));
        audit.record(name(sender), "lives.set", describe(profile),
                Integer.toString(before), Integer.toString(amount));
        return Command.SINGLE_SUCCESS;
    }

    private int adjustLives(CommandContext<CommandSourceStack> context, int delta) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = resolve(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        int before = profile.livesRemaining();
        int after = Math.max(0, Math.min(100, before + delta));
        profile.setLivesRemaining(after);
        profiles.saveNow(profile);

        sender.sendMessage(Component.text(profile.lastKnownName() + ": " + before
                + " -> " + after + " lives", GOOD));
        audit.record(name(sender), delta >= 0 ? "lives.give" : "lives.take", describe(profile),
                Integer.toString(before), Integer.toString(after));
        return Command.SINGLE_SUCCESS;
    }

    private int showPvpCounts(CommandContext<CommandSourceStack> context) {
        context.getSource().getSender().sendMessage(Component.text(
                "Player kills currently "
                        + (config.get().lives().pvpDeathsCount() ? "DO" : "do NOT")
                        + " cost a life.", LABEL));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Writes the toggle into config.yml so it survives a restart, then reloads.
     *
     * <p>Persisting matters more than it looks: a runtime-only toggle would silently revert
     * on the next restart, and the first anyone would know is somebody losing a life in a
     * duel that was supposed to be free.
     */
    private int setPvpCounts(CommandContext<CommandSourceStack> context, boolean value) {
        CommandSender sender = context.getSource().getSender();
        boolean before = config.get().lives().pvpDeathsCount();

        List<String> errors = config.setAndSave("lives.pvp-deaths-count", value);
        if (!errors.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Could not apply that - config.yml has other problems:", BAD));
            errors.forEach(error -> sender.sendMessage(Component.text("  - " + error, BAD)));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(Component.text("Player kills now "
                + (value ? "cost a life." : "cost nothing."), GOOD));
        audit.record(name(sender), "lives.pvpcounts", "-",
                Boolean.toString(before), Boolean.toString(value));
        return Command.SINGLE_SUCCESS;
    }

    // ----------------------------------------------------------------------------- limbo

    public LiteralArgumentBuilder<CommandSourceStack> limboTree() {
        return Commands.literal("limbo")
                .requires(permission("liminalis.admin.limbo"))
                .then(Commands.literal("list").executes(this::listLimbo))
                .then(Commands.literal("send")
                        .then(target().executes(this::sendToLimbo)))
                .then(Commands.literal("revive")
                        .then(target().executes(this::revive)))
                .then(Commands.literal("tp").executes(this::teleportToLimbo))
                .then(Commands.literal("ghostreset")
                        .then(target().executes(this::resetGhostCooldown)));
    }

    private int listLimbo(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        long now = System.currentTimeMillis();
        int found = 0;

        sender.sendMessage(Component.text("-- In Limbo --", ACCENT));
        for (UUID id : profiles.knownIds()) {
            Optional<PlayerProfile> maybe = safeLookup(id);
            if (maybe.isEmpty() || !maybe.get().inLimbo()) {
                continue;
            }
            PlayerProfile profile = maybe.get();
            found++;
            String since = profile.limboSince() > 0
                    ? describe(now - profile.limboSince()) + " ago" : "unknown";
            sender.sendMessage(Component.text("  " + profile.lastKnownName() + " ", VALUE)
                    .append(Component.text("(fell " + since + ")", LABEL)));
        }
        if (found == 0) {
            sender.sendMessage(Component.text("  nobody", LABEL));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int sendToLimbo(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = resolve(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (profile.inLimbo()) {
            sender.sendMessage(Component.text(profile.lastKnownName()
                    + " is already in Limbo.", WARN));
            return Command.SINGLE_SUCCESS;
        }
        if (!confirmed(sender, "limbo.send:" + profile.id())) {
            sender.sendMessage(Component.text("This ends " + profile.lastKnownName()
                    + "'s run. They will need rescuing to come back.", WARN));
            sender.sendMessage(repeatPrompt());
            return Command.SINGLE_SUCCESS;
        }

        int before = profile.livesRemaining();
        profile.setLivesRemaining(0);
        profile.setInLimbo(true);
        profile.setLimboSince(System.currentTimeMillis());
        profiles.saveNow(profile);

        Player online = org.bukkit.Bukkit.getPlayer(profile.id());
        if (online != null) {
            limbo.sendToLimbo(online);
        }
        sender.sendMessage(Component.text(profile.lastKnownName() + " is in Limbo."
                + (online == null ? " They will arrive when they next log in." : ""), GOOD));
        audit.record(name(sender), "limbo.send", describe(profile),
                before + " lives", "in limbo");
        return Command.SINGLE_SUCCESS;
    }

    private int revive(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = resolve(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }

        int lives = config.get().limbo().revivalLives();
        if (!LifeRules.revive(profile, lives, MARK_OF_RETURN)) {
            sender.sendMessage(Component.text(profile.lastKnownName()
                    + " is not in Limbo.", WARN));
            return Command.SINGLE_SUCCESS;
        }
        GhostVisitRules.clearCooldown(profile);
        profiles.saveNow(profile);

        Player online = org.bukkit.Bukkit.getPlayer(profile.id());
        if (online != null) {
            limbo.returnToLiving(online);
        }
        sender.sendMessage(Component.text(profile.lastKnownName() + " has returned with "
                + lives + " lives and the Mark of Return."
                + (online == null ? " They will arrive when they next log in." : ""), GOOD));
        audit.record(name(sender), "limbo.revive", describe(profile),
                "in limbo", lives + " lives");
        return Command.SINGLE_SUCCESS;
    }

    private int teleportToLimbo(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can teleport.", BAD));
            return Command.SINGLE_SUCCESS;
        }
        if (!limbo.world().isReady()) {
            sender.sendMessage(Component.text("Limbo is not open.", BAD));
            return Command.SINGLE_SUCCESS;
        }
        player.teleport(limbo.world().arrivalPoint());
        sender.sendMessage(Component.text("Sent you to Limbo. You are not trapped -"
                + " containment only applies to the dead.", LABEL));
        audit.record(name(sender), "limbo.tp", name(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int resetGhostCooldown(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = resolve(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        GhostVisitRules.clearCooldown(profile);
        profiles.saveNow(profile);
        sender.sendMessage(Component.text(profile.lastKnownName()
                + " can visit the living again immediately.", GOOD));
        audit.record(name(sender), "limbo.ghostreset", describe(profile));
        return Command.SINGLE_SUCCESS;
    }

    // --------------------------------------------------------------------------- helpers

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> target() {
        return Commands.argument("player", StringArgumentType.word()).suggests(playerNames);
    }

    private PlayerProfile resolve(CommandSender sender, CommandContext<CommandSourceStack> context) {
        String requested = StringArgumentType.getString(context, "player");
        Optional<UUID> id = profiles.resolve(requested);
        if (id.isEmpty()) {
            sender.sendMessage(Component.text("Liminalis has never seen '" + requested
                    + "'. Use a name it knows, or a raw UUID.", BAD));
            return null;
        }
        try {
            Optional<PlayerProfile> profile = profiles.lookup(id.get());
            if (profile.isEmpty()) {
                sender.sendMessage(Component.text("No stored profile for "
                        + requested + ".", BAD));
                return null;
            }
            return profile.get();
        } catch (RuntimeException e) {
            sender.sendMessage(Component.text("Profile for " + requested
                    + " could not be read: " + e.getMessage(), BAD));
            return null;
        }
    }

    private Optional<PlayerProfile> safeLookup(UUID id) {
        try {
            return profiles.lookup(id);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private boolean confirmed(CommandSender sender, String token) {
        return confirmations.submit(name(sender), token);
    }

    private Component repeatPrompt() {
        return Component.text("Run the same command again within "
                + confirmations.windowSeconds() + "s to confirm.", WARN);
    }

    private Predicate<CommandSourceStack> permission(String node) {
        return source -> source.getSender().hasPermission(node);
    }

    private static String describe(PlayerProfile profile) {
        return profile.lastKnownName() + "(" + profile.id() + ")";
    }

    private static String name(CommandSender sender) {
        return sender.getName();
    }

    private static String describe(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0, millis));
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1, minutes) + "m";
    }
}
