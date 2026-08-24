package com.liminalis.plugin.command;

import com.liminalis.core.ability.AbilityProgression;
import com.liminalis.core.ability.TierRequirement;
import com.liminalis.core.command.ConfirmationTracker;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.ability.Ability;
import com.liminalis.plugin.ability.AbilityFocus;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.modifier.ModifierType;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The {@code ability} branch of the admin tree - the one that gets used most.
 *
 * <p>Abilities are commissioned one at a time over a season, usually in response to a Discord
 * message and usually while the person who asked is asleep. So every command here works on
 * offline players, and granting one takes effect the moment they next log in.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AbilityCommands {

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
    private final JavaPlugin plugin;

    public AbilityCommands(JavaPlugin plugin,
                           ProfileManager profiles,
                           ModifierRegistry registry,
                           ModifierService modifiers,
                           Messages messages,
                           AuditLog audit,
                           ConfirmationTracker confirmations,
                           SuggestionProvider<CommandSourceStack> playerNames) {
        this.plugin = Objects.requireNonNull(plugin);
        this.profiles = Objects.requireNonNull(profiles);
        this.registry = Objects.requireNonNull(registry);
        this.modifiers = Objects.requireNonNull(modifiers);
        this.messages = Objects.requireNonNull(messages);
        this.audit = Objects.requireNonNull(audit);
        this.confirmations = Objects.requireNonNull(confirmations);
        this.playerNames = Objects.requireNonNull(playerNames);
    }

    public LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("ability")
                .requires(permission("liminalis.admin.ability"))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("progress")
                        .then(playerArgument().executes(this::progress)))
                .then(Commands.literal("set")
                        .then(playerArgument().then(abilityArgument().executes(this::set))))
                .then(Commands.literal("clear")
                        .then(playerArgument().executes(this::clear)))
                .then(Commands.literal("tier")
                        .then(playerArgument().then(Commands.argument("tier",
                                        IntegerArgumentType.integer(1, 20))
                                .executes(this::setTier))));
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        List<Ability> abilities = allAbilities();

        sender.sendMessage(Component.text("-- Abilities (" + abilities.size() + ") --", ACCENT));
        for (Ability ability : abilities) {
            sender.sendMessage(Component.text("  " + ability.id(), VALUE)
                    .append(Component.text("  " + ability.maxTier() + " tiers", LABEL)));
            for (TierRequirement tier : ability.tiers()) {
                sender.sendMessage(Component.text("      tier " + tier.tier() + ": ", LABEL)
                        .append(Component.text(tier.required() == 0 ? "granted with the ability"
                                : tier.required() + " " + tier.counterKey(), VALUE)));
            }
        }
        if (abilities.isEmpty()) {
            sender.sendMessage(Component.text("  none written yet", LABEL));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** What a player has, what tier they are on, and exactly what the next one wants. */
    private int progress(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (profile.abilityId() == null) {
            sender.sendMessage(Component.text(profile.lastKnownName()
                    + " has no ability yet.", LABEL));
            return Command.SINGLE_SUCCESS;
        }

        Ability ability = ability(profile.abilityId());
        sender.sendMessage(Component.text("-- " + profile.lastKnownName() + " --", ACCENT));
        sender.sendMessage(Component.text("  ability: ", LABEL)
                .append(Component.text(profile.abilityId()
                        + (ability == null ? "  (not in this build)" : ""), VALUE)));
        sender.sendMessage(Component.text("  tier: ", LABEL)
                .append(Component.text(profile.abilityTier()
                        + (ability == null ? "" : " / " + ability.maxTier()), VALUE)));

        if (ability != null) {
            Optional<TierRequirement> next = AbilityProgression.nextRequirement(
                    ability.tiers(), profile.abilityProgress());
            next.ifPresentOrElse(requirement -> sender.sendMessage(
                            Component.text("  next: ", LABEL)
                                    .append(Component.text(
                                            profile.abilityProgress().getOrDefault(
                                                    requirement.counterKey(), 0)
                                            + " / " + requirement.required() + " "
                                            + requirement.counterKey(), VALUE))),
                    () -> sender.sendMessage(Component.text("  next: ", LABEL)
                            .append(Component.text("complete", GOOD))));
        }

        if (!profile.abilityProgress().isEmpty()) {
            sender.sendMessage(Component.text("  counters:", LABEL));
            profile.abilityProgress().forEach((key, value) ->
                    sender.sendMessage(Component.text("    " + key + ": " + value, VALUE)));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Grants an ability.
     *
     * <p>Replacing one someone already has wipes their progress and needs confirming - the
     * counters belong to the old ability and would be meaningless attached to the new one,
     * and silently keeping them would put somebody at tier three of a power they have never
     * used.
     */
    private int set(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        Ability ability = abilityArg(sender, context);
        if (profile == null || ability == null) {
            return Command.SINGLE_SUCCESS;
        }

        String before = profile.abilityId() == null ? "none" : profile.abilityId();
        boolean replacing = profile.abilityId() != null
                && !profile.abilityId().equals(ability.id());

        if (replacing && !confirmations.submit(name(sender), "ability.set:" + profile.id())) {
            sender.sendMessage(Component.text(profile.lastKnownName() + " already has "
                    + before + ". Replacing it wipes their progress.", WARN));
            sender.sendMessage(Component.text("Run the same command again within "
                    + confirmations.windowSeconds() + "s to confirm.", WARN));
            return Command.SINGLE_SUCCESS;
        }

        if (replacing) {
            profile.clearAbilityProgress();
        }
        profile.setAbilityId(ability.id());
        profile.setAbilityTier(1);
        persist(profile);

        sender.sendMessage(Component.text(profile.lastKnownName() + ": " + before
                + " -> " + ability.id() + " (tier 1)", GOOD));
        Player online = Bukkit.getPlayer(profile.id());
        if (online != null) {
            // Handed the focus with the ability. Granting a power somebody cannot use until
            // they work out they need an item would be a poor way to receive a gift.
            giveFocus(online, ability);
            messages.send(online, "ability.granted",
                    Messages.placeholder("ability", messages.get(ability.nameKey())),
                    Messages.placeholder("description",
                            (Component) messages.get(ability.descriptionKey())));
        } else {
            sender.sendMessage(Component.text(
                    "They are offline; it will be waiting when they log in.", LABEL));
        }
        audit.record(name(sender), "ability.set", describe(profile), before, ability.id());
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (profile.abilityId() == null) {
            sender.sendMessage(Component.text(profile.lastKnownName()
                    + " has no ability to take.", WARN));
            return Command.SINGLE_SUCCESS;
        }
        if (!confirmations.submit(name(sender), "ability.clear:" + profile.id())) {
            sender.sendMessage(Component.text("This takes " + profile.abilityId()
                    + " from " + profile.lastKnownName() + " and wipes their progress.", WARN));
            sender.sendMessage(Component.text("Run the same command again within "
                    + confirmations.windowSeconds() + "s to confirm.", WARN));
            return Command.SINGLE_SUCCESS;
        }

        String before = profile.abilityId();
        profile.setAbilityId(null);
        profile.setAbilityTier(0);
        profile.clearAbilityProgress();
        persist(profile);

        sender.sendMessage(Component.text("Took " + before + " from "
                + profile.lastKnownName() + ".", GOOD));
        audit.record(name(sender), "ability.clear", describe(profile), before, "none");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Forces a tier.
     *
     * <p>Worth knowing: this sticks only until the player next earns progress, because the
     * service recomputes tier from counters. That is deliberate - it stops a stored tier
     * drifting away from the work behind it - but it means this is for testing and for
     * making good on a promise, not for permanent gifts. Use {@code progress} to see why.
     */
    private int setTier(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        int tier = IntegerArgumentType.getInteger(context, "tier");
        int before = profile.abilityTier();
        profile.setAbilityTier(tier);
        persist(profile);

        sender.sendMessage(Component.text(profile.lastKnownName() + ": tier "
                + before + " -> " + tier, GOOD));
        sender.sendMessage(Component.text(
                "This is recomputed from their counters next time they earn progress.", LABEL));
        audit.record(name(sender), "ability.tier", describe(profile),
                Integer.toString(before), Integer.toString(tier));
        return Command.SINGLE_SUCCESS;
    }

    // --------------------------------------------------------------------------- helpers

    /**
     * Puts the ability's focus in their hands, or at their feet if they are full.
     *
     * <p>Only for the Priest so far because it is the only ability written; the material is
     * chosen per ability, so the next one picks its own.
     */
    private void giveFocus(Player player, Ability ability) {
        ItemStack focus = AbilityFocus.create(plugin, messages, ability.id(),
                org.bukkit.Material.STICK);
        player.getInventory().addItem(focus).values().forEach(leftover ->
                player.getWorld().dropItem(player.getLocation(), leftover));
    }

    private void persist(PlayerProfile profile) {
        profiles.saveNow(profile);
        Player online = Bukkit.getPlayer(profile.id());
        if (online != null) {
            modifiers.applyFromProfile(online);
        }
    }

    private List<Ability> allAbilities() {
        return registry.ofType(ModifierType.ABILITY).stream()
                .filter(Ability.class::isInstance)
                .map(Ability.class::cast)
                .toList();
    }

    private Ability ability(String id) {
        Modifier modifier = registry.find(id).orElse(null);
        return modifier instanceof Ability ability ? ability : null;
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return Commands.argument("player", StringArgumentType.word()).suggests(playerNames);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> abilityArgument() {
        return Commands.argument("ability", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String typed = builder.getRemainingLowerCase();
                    allAbilities().stream()
                            .map(Ability::id)
                            .filter(id -> id.startsWith(typed))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    private Ability abilityArg(CommandSender sender, CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "ability");
        Ability ability = ability(id);
        if (ability == null) {
            sender.sendMessage(Component.text("No ability called '" + id
                    + "'. Try /liminalis ability list.", BAD));
        }
        return ability;
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
