package com.liminalis.plugin.command;

import com.liminalis.core.command.ConfirmationTracker;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.boon.Boon;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.modifier.ModifierType;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The {@code boon} branch of the admin tree: blessings and curses.
 *
 * <p>One subtree rather than two, because a player can only carry one of either and the
 * exclusivity has to be enforced somewhere. Doing it here means {@code boon set} can simply
 * take any boon id and put it in the right slot, clearing the other.
 */
@SuppressWarnings("UnstableApiUsage")
public final class BoonCommands {

    private static final NamedTextColor ACCENT = NamedTextColor.AQUA;
    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;
    private static final NamedTextColor GOOD = NamedTextColor.GREEN;
    private static final NamedTextColor BAD = NamedTextColor.RED;
    private static final NamedTextColor WARN = NamedTextColor.YELLOW;

    private final ProfileManager profiles;
    private final ModifierRegistry registry;
    private final ModifierService modifiers;
    private final Messages messages;
    private final AuditLog audit;
    private final ConfirmationTracker confirmations;
    private final SuggestionProvider<CommandSourceStack> playerNames;

    public BoonCommands(ProfileManager profiles,
                        ModifierRegistry registry,
                        ModifierService modifiers,
                        Messages messages,
                        AuditLog audit,
                        ConfirmationTracker confirmations,
                        SuggestionProvider<CommandSourceStack> playerNames) {
        this.profiles = Objects.requireNonNull(profiles);
        this.registry = Objects.requireNonNull(registry);
        this.modifiers = Objects.requireNonNull(modifiers);
        this.messages = Objects.requireNonNull(messages);
        this.audit = Objects.requireNonNull(audit);
        this.confirmations = Objects.requireNonNull(confirmations);
        this.playerNames = Objects.requireNonNull(playerNames);
    }

    /**
     * Blessings and curses get a subtree each rather than sharing one.
     *
     * <p>They were a single {@code boon} command originally, which meant tab-completing a
     * grant offered all ten ids mixed together and left it to the operator to remember which
     * half was which. Splitting them means the list you are shown is already the list you
     * meant, and it is much harder to hand somebody a curse while believing you gave a gift.
     *
     * <p>Mutual exclusivity still holds across both: granting either clears the other,
     * because a player carrying a blessing and a curse at once is a state the roll can never
     * produce, and so must not be a state a command can produce either.
     */
    public LiteralArgumentBuilder<CommandSourceStack> blessingTree() {
        return subtree("blessing", ModifierType.BLESSING);
    }

    public LiteralArgumentBuilder<CommandSourceStack> curseTree() {
        return subtree("curse", ModifierType.CURSE);
    }

    private LiteralArgumentBuilder<CommandSourceStack> subtree(String label, ModifierType type) {
        return Commands.literal(label)
                .requires(permission("liminalis.admin.boon"))
                .then(Commands.literal("list").executes(context -> list(context, type)))
                .then(Commands.literal("info")
                        .then(boonArgument(type).executes(context -> info(context, type))))
                .then(Commands.literal("give")
                        .then(playerArgument().then(boonArgument(type)
                                .executes(context -> set(context, type)))))
                .then(Commands.literal("clear")
                        .then(playerArgument().executes(context -> clear(context, type))));
    }

    private int list(CommandContext<CommandSourceStack> context, ModifierType type) {
        CommandSender sender = context.getSource().getSender();
        List<Boon> boons = boonsOf(type);
        sender.sendMessage(Component.text(
                "-- " + label(type) + " (" + boons.size() + ") --", ACCENT));
        for (Boon boon : boons) {
            sender.sendMessage(Component.text("  " + boon.id() + "  ", VALUE)
                    .append(messages.get(boon.descriptionKey())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String label(ModifierType type) {
        return type == ModifierType.CURSE ? "Curses" : "Blessings";
    }

    private int info(CommandContext<CommandSourceStack> context, ModifierType type) {
        CommandSender sender = context.getSource().getSender();
        Boon boon = boon(sender, context, type);
        if (boon == null) {
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text("-- " + boon.id() + " --", ACCENT));
        sender.sendMessage(Component.text("  name: ", LABEL)
                .append(messages.get(boon.nameKey())));
        sender.sendMessage(Component.text("  description: ", LABEL)
                .append(messages.get(boon.descriptionKey())));
        sender.sendMessage(Component.text("  kind: ", LABEL)
                .append(Component.text(boon.isCurse() ? "curse" : "blessing", VALUE)));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Puts a boon in the right slot and clears the other one.
     *
     * <p>A player carrying both a blessing and a curse is a state the roll can never produce,
     * so it must not be a state an admin command can produce either.
     */
    private int set(CommandContext<CommandSourceStack> context, ModifierType type) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        Boon boon = boon(sender, context, type);
        if (profile == null || boon == null) {
            return Command.SINGLE_SUCCESS;
        }

        String before = describeCurrent(profile);
        if (boon.isCurse()) {
            profile.setCurseId(boon.id());
            profile.setBlessingId(null);
        } else {
            profile.setBlessingId(boon.id());
            profile.setCurseId(null);
        }
        persist(profile);

        sender.sendMessage(Component.text(profile.lastKnownName() + ": " + before
                + " -> " + describeCurrent(profile), GOOD));
        audit.record(name(sender), "boon.set", describe(profile),
                before, describeCurrent(profile));
        return Command.SINGLE_SUCCESS;
    }

    /** Clears only the kind you asked about, so the command means exactly what it says. */
    private int clear(CommandContext<CommandSourceStack> context, ModifierType type) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        boolean curse = type == ModifierType.CURSE;
        String kind = curse ? "curse" : "blessing";
        if ((curse ? profile.curseId() : profile.blessingId()) == null) {
            sender.sendMessage(Component.text(
                    profile.lastKnownName() + " has no " + kind + ".", WARN));
            return Command.SINGLE_SUCCESS;
        }
        if (!confirmations.submit(name(sender), "boon.clear:" + profile.id())) {
            sender.sendMessage(Component.text("This removes " + profile.lastKnownName()
                    + "'s " + describeCurrent(profile) + " permanently.", WARN));
            sender.sendMessage(Component.text("Run the same command again within "
                    + confirmations.windowSeconds() + "s to confirm.", WARN));
            return Command.SINGLE_SUCCESS;
        }

        String before = describeCurrent(profile);
        if (curse) {
            profile.setCurseId(null);
        } else {
            profile.setBlessingId(null);
        }
        persist(profile);

        sender.sendMessage(Component.text("Cleared " + before + " from "
                + profile.lastKnownName() + ".", GOOD));
        audit.record(name(sender), "boon.clear", describe(profile), before, "none");
        return Command.SINGLE_SUCCESS;
    }

    // --------------------------------------------------------------------------- helpers

    private void persist(PlayerProfile profile) {
        profiles.saveNow(profile);
        Player online = Bukkit.getPlayer(profile.id());
        if (online != null) {
            modifiers.applyFromProfile(online);
        }
    }

    private static String describeCurrent(PlayerProfile profile) {
        if (profile.blessingId() != null) {
            return "blessing " + profile.blessingId();
        }
        if (profile.curseId() != null) {
            return "curse " + profile.curseId();
        }
        return "none";
    }

    private List<Boon> boonsOf(ModifierType type) {
        return registry.ofType(type).stream()
                .filter(Boon.class::isInstance)
                .map(Boon.class::cast)
                .toList();
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return Commands.argument("player", StringArgumentType.word()).suggests(playerNames);
    }

    /** Completion is filtered to one kind, which is the whole point of the split. */
    private RequiredArgumentBuilder<CommandSourceStack, String> boonArgument(ModifierType type) {
        return Commands.argument("boon", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String typed = builder.getRemainingLowerCase();
                    for (Boon boon : boonsOf(type)) {
                        if (boon.id().startsWith(typed)) {
                            builder.suggest(boon.id());
                        }
                    }
                    return builder.buildFuture();
                });
    }

    private Boon boon(CommandSender sender, CommandContext<CommandSourceStack> context,
                      ModifierType type) {
        String id = StringArgumentType.getString(context, "boon");
        Modifier modifier = registry.find(id).orElse(null);
        String kind = type == ModifierType.CURSE ? "curse" : "blessing";

        if (!(modifier instanceof Boon boon)) {
            sender.sendMessage(Component.text("No " + kind + " called " + id
                    + ". Try /liminalis " + kind + " list.", BAD));
            return null;
        }
        // Naming a curse on the blessing command is almost always a slip, and quietly doing
        // it anyway is how somebody hands out a curse believing it was a gift.
        if (boon.type() != type) {
            String actual = boon.isCurse() ? "curse" : "blessing";
            sender.sendMessage(Component.text(id + " is a " + actual + ", not a " + kind
                    + ". Use /liminalis " + actual + " give.", BAD));
            return null;
        }
        return boon;
    }

    private PlayerProfile profile(CommandSender sender, CommandContext<CommandSourceStack> context) {
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

    private Predicate<CommandSourceStack> permission(String node) {
        return source -> source.getSender().hasPermission(node);
    }

    private static String describe(PlayerProfile profile) {
        return profile.lastKnownName() + "(" + profile.id() + ")";
    }

    private static String name(CommandSender sender) {
        return sender.getName();
    }
}
