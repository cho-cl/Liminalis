package com.liminalis.plugin.command;

import com.liminalis.core.command.ConfirmationTracker;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.core.roll.RollCandidate;
import com.liminalis.core.roll.TraitRoller;
import com.liminalis.core.roll.TraitTier;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.modifier.ModifierType;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import com.liminalis.plugin.trait.Trait;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The {@code trait} branch of the admin tree.
 *
 * <p>Trait ids are suggested straight from the registry, so an operator never has to remember
 * one and cannot type one that does not exist. That is the pattern every later phase reuses
 * for blessings, curses, injuries and abilities.
 */
@SuppressWarnings("UnstableApiUsage")
public final class TraitCommands {

    private static final NamedTextColor ACCENT = NamedTextColor.AQUA;
    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;
    private static final NamedTextColor GOOD = NamedTextColor.GREEN;
    private static final NamedTextColor BAD = NamedTextColor.RED;
    private static final NamedTextColor WARN = NamedTextColor.YELLOW;

    private final ConfigService config;
    private final ProfileManager profiles;
    private final ModifierRegistry registry;
    private final ModifierService modifiers;
    private final Messages messages;
    private final AuditLog audit;
    private final ConfirmationTracker confirmations;
    private final SuggestionProvider<CommandSourceStack> playerNames;
    private final Random random = new Random();

    public TraitCommands(ConfigService config,
                         ProfileManager profiles,
                         ModifierRegistry registry,
                         ModifierService modifiers,
                         Messages messages,
                         AuditLog audit,
                         ConfirmationTracker confirmations,
                         SuggestionProvider<CommandSourceStack> playerNames) {
        this.config = Objects.requireNonNull(config);
        this.profiles = Objects.requireNonNull(profiles);
        this.registry = Objects.requireNonNull(registry);
        this.modifiers = Objects.requireNonNull(modifiers);
        this.messages = Objects.requireNonNull(messages);
        this.audit = Objects.requireNonNull(audit);
        this.confirmations = Objects.requireNonNull(confirmations);
        this.playerNames = Objects.requireNonNull(playerNames);
    }

    public LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("trait")
                .requires(permission("liminalis.admin.trait"))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("info")
                        .then(traitArgument().executes(this::info)))
                .then(Commands.literal("give")
                        .then(playerArgument()
                                .then(traitArgument().executes(this::give))))
                .then(Commands.literal("remove")
                        .then(playerArgument()
                                .then(traitArgument().executes(this::remove))))
                .then(Commands.literal("reroll")
                        .then(playerArgument().executes(this::reroll)));
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        List<Trait> traits = allTraits();

        sender.sendMessage(Component.text("-- Traits (" + traits.size() + ") --", ACCENT));
        for (TraitTier tier : TraitTier.values()) {
            List<Trait> inTier = traits.stream().filter(t -> t.tier() == tier).toList();
            if (inTier.isEmpty()) {
                continue;
            }
            sender.sendMessage(Component.text("  " + tier.name().toLowerCase(Locale.ROOT)
                    + ":", LABEL));
            for (Trait trait : inTier) {
                sender.sendMessage(Component.text("    " + trait.id(), VALUE)
                        .append(Component.text(" (weight " + trait.weight() + ")", LABEL)));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Trait trait = trait(sender, context);
        if (trait == null) {
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text("-- " + trait.id() + " --", ACCENT));
        sender.sendMessage(Component.text("  name: ", LABEL)
                .append(messages.get(trait.nameKey())));
        sender.sendMessage(Component.text("  description: ", LABEL)
                .append(messages.get(trait.descriptionKey())));
        sender.sendMessage(Component.text("  tier: ", LABEL)
                .append(Component.text(trait.tier().name().toLowerCase(Locale.ROOT), VALUE)));
        sender.sendMessage(Component.text("  weight: ", LABEL)
                .append(Component.text(Double.toString(trait.weight()), VALUE)));
        return Command.SINGLE_SUCCESS;
    }

    private int give(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        Trait trait = trait(sender, context);
        if (profile == null || trait == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (!profile.addTrait(trait.id())) {
            sender.sendMessage(Component.text(profile.lastKnownName()
                    + " already has " + trait.id() + ".", WARN));
            return Command.SINGLE_SUCCESS;
        }
        persist(profile);
        sender.sendMessage(Component.text("Gave " + trait.id() + " to "
                + profile.lastKnownName() + ".", GOOD));
        audit.record(name(sender), "trait.give", describe(profile), null, trait.id());
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        Trait trait = trait(sender, context);
        if (profile == null || trait == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (!profile.removeTrait(trait.id())) {
            sender.sendMessage(Component.text(profile.lastKnownName()
                    + " does not have " + trait.id() + ".", WARN));
            return Command.SINGLE_SUCCESS;
        }
        persist(profile);
        sender.sendMessage(Component.text("Removed " + trait.id() + " from "
                + profile.lastKnownName() + ".", GOOD));
        audit.record(name(sender), "trait.remove", describe(profile), trait.id(), null);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Throws away a player's traits and rolls fresh ones.
     *
     * <p>The most destructive command in the plugin: it replaces who somebody is, and there
     * is no undo. Hence the confirmation, and hence the before/after in the audit log being
     * the actual trait ids rather than a count.
     */
    private int reroll(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (!confirmations.submit(name(sender), "trait.reroll:" + profile.id())) {
            sender.sendMessage(Component.text("This replaces " + profile.lastKnownName()
                    + "'s traits permanently. They currently have: "
                    + String.join(", ", profile.traitIds()), WARN));
            sender.sendMessage(Component.text("Run the same command again within "
                    + confirmations.windowSeconds() + "s to confirm.", WARN));
            return Command.SINGLE_SUCCESS;
        }

        String before = String.join(", ", profile.traitIds());
        List<String> old = List.copyOf(profile.traitIds());
        old.forEach(profile::removeTrait);

        List<RollCandidate> pool = allTraits().stream().map(Trait::asCandidate).toList();
        List<String> rolled = new TraitRoller(pool).roll(config.get().traits().roll(), random);
        rolled.forEach(profile::addTrait);
        persist(profile);

        sender.sendMessage(Component.text(profile.lastKnownName() + ": "
                + (before.isEmpty() ? "nothing" : before) + " -> "
                + String.join(", ", rolled), GOOD));
        audit.record(name(sender), "trait.reroll", describe(profile),
                before, String.join(", ", rolled));
        return Command.SINGLE_SUCCESS;
    }

    // --------------------------------------------------------------------------- helpers

    /** Saves, and re-applies live so the change is felt immediately rather than next login. */
    private void persist(PlayerProfile profile) {
        profiles.saveNow(profile);
        Player online = Bukkit.getPlayer(profile.id());
        if (online != null) {
            modifiers.applyFromProfile(online);
        }
    }

    private List<Trait> allTraits() {
        return registry.ofType(ModifierType.TRAIT).stream()
                .filter(Trait.class::isInstance)
                .map(Trait.class::cast)
                .toList();
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return Commands.argument("player", StringArgumentType.word()).suggests(playerNames);
    }

    /** Suggestions come from the registry, so an id can be neither forgotten nor mistyped. */
    private RequiredArgumentBuilder<CommandSourceStack, String> traitArgument() {
        return Commands.argument("trait", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String typed = builder.getRemainingLowerCase();
                    for (Trait trait : allTraits()) {
                        if (trait.id().startsWith(typed)) {
                            builder.suggest(trait.id());
                        }
                    }
                    return builder.buildFuture();
                });
    }

    private Trait trait(CommandSender sender, CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "trait");
        Modifier modifier = registry.find(id).orElse(null);
        if (!(modifier instanceof Trait trait)) {
            sender.sendMessage(Component.text("No trait called '" + id
                    + "'. Try /liminalis trait list.", BAD));
            return null;
        }
        return trait;
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
