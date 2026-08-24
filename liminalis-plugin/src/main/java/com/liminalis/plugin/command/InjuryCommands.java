package com.liminalis.plugin.command;

import com.liminalis.core.injury.ActiveInjury;
import com.liminalis.core.injury.InjurySeverity;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.injury.Injuries;
import com.liminalis.plugin.injury.Injury;
import com.liminalis.plugin.injury.InjuryService;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
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

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The {@code injury} branch of the admin tree.
 *
 * <p>{@code injury give} matters more than it looks. It is how every wound in the roster gets
 * tested without hunting down a netherite axe and a willing volunteer, and it is the reason
 * this phase could be verified at all.
 */
@SuppressWarnings("UnstableApiUsage")
public final class InjuryCommands {

    private static final NamedTextColor ACCENT = NamedTextColor.AQUA;
    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;
    private static final NamedTextColor GOOD = NamedTextColor.GREEN;
    private static final NamedTextColor BAD = NamedTextColor.RED;
    private static final NamedTextColor WARN = NamedTextColor.YELLOW;

    private final ProfileManager profiles;
    private final ModifierRegistry registry;
    private final ModifierService modifiers;
    private final InjuryService injuries;
    private final Messages messages;
    private final AuditLog audit;
    private final SuggestionProvider<CommandSourceStack> playerNames;

    public InjuryCommands(ProfileManager profiles,
                          ModifierRegistry registry,
                          ModifierService modifiers,
                          InjuryService injuries,
                          Messages messages,
                          AuditLog audit,
                          SuggestionProvider<CommandSourceStack> playerNames) {
        this.profiles = Objects.requireNonNull(profiles);
        this.registry = Objects.requireNonNull(registry);
        this.modifiers = Objects.requireNonNull(modifiers);
        this.injuries = Objects.requireNonNull(injuries);
        this.messages = Objects.requireNonNull(messages);
        this.audit = Objects.requireNonNull(audit);
        this.playerNames = Objects.requireNonNull(playerNames);
    }

    public LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("injury")
                .requires(permission("liminalis.admin.injury"))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("info")
                        .then(injuryArgument().executes(this::info)))
                .then(Commands.literal("of")
                        .then(playerArgument().executes(this::carried)))
                .then(Commands.literal("give")
                        .then(playerArgument().then(injuryArgument().executes(this::give))))
                .then(Commands.literal("heal").then(healArguments()))
                // "remove" as well as "heal", because heal is the word for what it does to a
                // player and remove is the word you reach for when you are testing. Costing
                // one line to not have to remember which one this plugin chose is worth it.
                .then(Commands.literal("remove").then(healArguments()));
    }

    /** Shared by heal and remove: a player, and optionally one wound rather than all. */
    private RequiredArgumentBuilder<CommandSourceStack, String> healArguments() {
        return playerArgument()
                .executes(context -> heal(context, null))
                .then(injuryArgument().executes(context ->
                        heal(context, StringArgumentType.getString(context, "injury"))));
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        for (InjurySeverity severity : List.of(
                InjurySeverity.INJURY, InjurySeverity.MORTAL_WOUND)) {
            List<Injury> inTier = injuries.allInjuries().stream()
                    .filter(injury -> injury.severity() == severity)
                    .toList();
            if (inTier.isEmpty()) {
                continue;
            }
            sender.sendMessage(Component.text("-- "
                    + severity.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    + " --", ACCENT));
            for (Injury injury : inTier) {
                sender.sendMessage(Component.text("  " + injury.id(), VALUE)
                        .append(Component.text("  " + causes(injury)
                                + (injury.decays() ? ", " + injury.durationSeconds() + "s"
                                : ", permanent"), LABEL)));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Injury injury = injury(sender, context);
        if (injury == null) {
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text("-- " + injury.id() + " --", ACCENT));
        sender.sendMessage(Component.text("  name: ", LABEL)
                .append(messages.get(injury.nameKey())));
        sender.sendMessage(Component.text("  description: ", LABEL)
                .append(messages.get(injury.descriptionKey())));
        sender.sendMessage(Component.text("  severity: ", LABEL)
                .append(Component.text(injury.severity().name().toLowerCase(Locale.ROOT), VALUE)));
        sender.sendMessage(Component.text("  caused by: ", LABEL)
                .append(Component.text(causes(injury), VALUE)));
        sender.sendMessage(Component.text("  lasts: ", LABEL)
                .append(Component.text(injury.decays()
                        ? injury.durationSeconds() + "s" : "until death or treatment", VALUE)));
        return Command.SINGLE_SUCCESS;
    }

    private int carried(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (profile.injuries().isEmpty()) {
            sender.sendMessage(Component.text(profile.lastKnownName()
                    + " is unhurt.", LABEL));
            return Command.SINGLE_SUCCESS;
        }
        long now = System.currentTimeMillis();
        sender.sendMessage(Component.text("-- " + profile.lastKnownName() + " --", ACCENT));
        for (ActiveInjury injury : profile.injuries()) {
            String left = injury.permanent()
                    ? "permanent" : describe(injury.remainingMillis(now)) + " left";
            sender.sendMessage(Component.text("  " + injury.id(), VALUE)
                    .append(Component.text("  (" + left + ")", LABEL)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int give(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        Injury injury = injury(sender, context);
        if (profile == null || injury == null) {
            return Command.SINGLE_SUCCESS;
        }

        long expiresAt = injury.decays()
                ? System.currentTimeMillis() + (injury.durationSeconds() * 1000L) : 0L;
        profile.addInjury(new ActiveInjury(injury.id(), expiresAt));
        persist(profile);

        sender.sendMessage(Component.text("Inflicted " + injury.id() + " on "
                + profile.lastKnownName() + ".", GOOD));
        audit.record(name(sender), "injury.give", describe(profile), null, injury.id());
        return Command.SINGLE_SUCCESS;
    }

    /** Heals one wound, or every wound if none is named. */
    private int heal(CommandContext<CommandSourceStack> context, String injuryId) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }

        String before = describeCarried(profile);
        if (injuryId == null) {
            if (profile.injuries().isEmpty()) {
                sender.sendMessage(Component.text(profile.lastKnownName()
                        + " is already unhurt.", WARN));
                return Command.SINGLE_SUCCESS;
            }
            profile.clearInjuries();
        } else if (!profile.removeInjury(injuryId)) {
            sender.sendMessage(Component.text(profile.lastKnownName()
                    + " does not have " + injuryId + ".", WARN));
            return Command.SINGLE_SUCCESS;
        }
        persist(profile);

        sender.sendMessage(Component.text("Healed " + profile.lastKnownName()
                + ": " + before + " -> " + describeCarried(profile), GOOD));
        audit.record(name(sender), "injury.heal", describe(profile),
                before, describeCarried(profile));
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

    private static String describeCarried(PlayerProfile profile) {
        return profile.injuries().isEmpty() ? "unhurt"
                : profile.injuries().stream().map(ActiveInjury::id).toList().toString();
    }

    private static String causes(Injury injury) {
        return injury.causes().stream()
                .map(cause -> cause.name().toLowerCase(Locale.ROOT))
                .sorted()
                .reduce((a, b) -> a + "/" + b)
                .orElse("nothing");
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return Commands.argument("player", StringArgumentType.word()).suggests(playerNames);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> injuryArgument() {
        return Commands.argument("injury", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String typed = builder.getRemainingLowerCase();
                    for (Injury injury : injuries.allInjuries()) {
                        if (injury.id().startsWith(typed)) {
                            builder.suggest(injury.id());
                        }
                    }
                    return builder.buildFuture();
                });
    }

    private Injury injury(CommandSender sender, CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "injury");
        Modifier modifier = registry.find(id).orElse(null);
        if (!(modifier instanceof Injury injury)) {
            sender.sendMessage(Component.text("No injury called '" + id
                    + "'. Try /liminalis injury list.", BAD));
            return null;
        }
        return injury;
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

    private static String describe(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0, millis));
        long minutes = duration.toMinutes();
        long seconds = duration.toSeconds() % 60;
        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }

    private static String name(CommandSender sender) {
        return sender.getName();
    }
}
