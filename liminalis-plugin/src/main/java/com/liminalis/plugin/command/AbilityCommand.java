package com.liminalis.plugin.command;

import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.ability.Ability;
import com.liminalis.plugin.ability.AbilityService;
import com.liminalis.plugin.ability.Power;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;

/**
 * {@code /ability} - how every power in the game is used.
 *
 * <p>Numbered slots rather than click gestures, because there are only so many distinct
 * gestures and asking a player to remember whether a power is a sneak-click or a bare-handed
 * click stops scaling at about two. Five numbered powers per ability is only possible at all
 * because the trigger is a command.
 *
 * <p>Bare {@code /ability} lists what you have, what each slot does, and which are still
 * locked - so nobody has to remember anything except the word "ability".
 */
@SuppressWarnings("UnstableApiUsage")
public final class AbilityCommand {

    private static final NamedTextColor ACCENT = NamedTextColor.AQUA;
    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;
    private static final NamedTextColor LOCKED = NamedTextColor.DARK_GRAY;
    private static final NamedTextColor BAD = NamedTextColor.RED;

    /** How far ahead we look for who you meant. Beyond this, name them. */
    private static final int AIM_RANGE = 6;

    private final ProfileManager profiles;
    private final AbilityService abilities;
    private final Messages messages;

    public AbilityCommand(ProfileManager profiles, AbilityService abilities, Messages messages) {
        this.profiles = Objects.requireNonNull(profiles);
        this.abilities = Objects.requireNonNull(abilities);
        this.messages = Objects.requireNonNull(messages);
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ability")
                .requires(source -> source.getSender() instanceof Player)
                .executes(this::listPowers)
                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 9))
                        .executes(context -> use(context, null))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(this::suggestNearby)
                                .executes(context -> use(context,
                                        StringArgumentType.getString(context, "player")))))
                .build();
    }

    // ---------------------------------------------------------------------------- using

    private int use(CommandContext<CommandSourceStack> context, String targetName) {
        Player user = (Player) context.getSource().getSender();
        int slot = IntegerArgumentType.getInteger(context, "slot");

        Ability ability = abilities.abilityOf(user).orElse(null);
        if (ability == null) {
            messages.send(user, "ability.none");
            return Command.SINGLE_SUCCESS;
        }

        Power power = ability.power(slot).orElse(null);
        if (power == null) {
            messages.send(user, "ability.no-such-power",
                    Messages.placeholder("slot", slot));
            return Command.SINGLE_SUCCESS;
        }

        int tier = profiles.resident(user.getUniqueId())
                .map(PlayerProfile::abilityTier).orElse(1);
        if (tier < power.unlockedAt()) {
            messages.send(user, "ability.locked",
                    Messages.placeholder("slot", slot),
                    Messages.placeholder("tier", power.unlockedAt()));
            return Command.SINGLE_SUCCESS;
        }

        LivingEntity target = null;
        if (power.needsTarget()) {
            target = resolveTarget(user, targetName);
            if (target == null) {
                // resolveTarget has already said what went wrong.
                return Command.SINGLE_SUCCESS;
            }
        }

        abilities.fire(user, ability, power, target);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Works out who a power was aimed at.
     *
     * <p>Looks at whoever you are pointing at first, so the common case needs no typing at
     * all. A name is only necessary when they are behind you, out of reach, or standing in a
     * crowd.
     */
    private LivingEntity resolveTarget(Player user, String named) {
        if (named != null) {
            Player found = Bukkit.getPlayerExact(named);
            if (found == null) {
                messages.send(user, "ability.no-such-player",
                        Messages.placeholder("player", named));
                return null;
            }
            if (found.getWorld() != user.getWorld()
                    || found.getLocation().distance(user.getLocation()) > AIM_RANGE * 4) {
                messages.send(user, "ability.too-far",
                        Messages.placeholder("player", found.getName()));
                return null;
            }
            return found;
        }

        // Anything alive, not only a player. Healing was unusable on a wolf, a villager
        // or a horse for no reason anybody could have explained, and the refusal looked
        // identical to aiming at nothing at all.
        Entity aimed = user.getTargetEntity(AIM_RANGE);
        if (aimed instanceof LivingEntity living && !living.equals(user)) {
            return living;
        }
        messages.send(user, "ability.no-target");
        return null;
    }

    // --------------------------------------------------------------------------- listing

    /** Bare /ability: what you are, what each number does, and what is still shut. */
    private int listPowers(CommandContext<CommandSourceStack> context) {
        Player user = (Player) context.getSource().getSender();

        Ability ability = abilities.abilityOf(user).orElse(null);
        if (ability == null) {
            messages.send(user, "ability.none");
            return Command.SINGLE_SUCCESS;
        }
        int tier = profiles.resident(user.getUniqueId())
                .map(PlayerProfile::abilityTier).orElse(1);

        user.sendMessage(Component.empty());
        user.sendMessage(messages.get(ability.nameKey()).color(ACCENT)
                .append(Component.text("   level " + tier + " / "
                        + ability.maxLevel(), LABEL)));

        for (Power power : ability.powers()) {
            boolean open = tier >= power.unlockedAt();
            Component line = Component.text("  " + power.slot() + "  ", open ? LABEL : LOCKED)
                    .append(messages.get("ability." + ability.id() + "." + power.id() + ".name")
                            .color(open ? VALUE : LOCKED));
            if (!open) {
                line = line.append(Component.text("   locked", LOCKED));
            } else if (power.cooldownSeconds() > 0) {
                long left = abilities.cooldownRemaining(user, power);
                line = line.append(Component.text(left > 0
                        ? "   " + left + "s" : "   ready", LABEL));
            }
            user.sendMessage(line);
            if (open) {
                user.sendMessage(Component.text("     ", LOCKED).append(
                        messages.get("ability." + ability.id() + "." + power.id()
                                + ".description")));
            }
        }
        // How to get the next one. A ladder nobody is told the rungs of is indistinguishable
        // from no ladder - and the two Singularity drops that feed it were being kept as
        // trophies by players who had no way of knowing they were currency.
        int toNext = abilities.usesToNextLevel(user);
        user.sendMessage(Component.text(toNext > 0
                ? "  " + toNext + " more use(s) for the next power"
                : "  every power is yours", LABEL));
        if (toNext > 0) {
            user.sendMessage(messages.get("ability.how-to-level"));
        }
        user.sendMessage(Component.text("  /ability <1-" + ability.powers().size()
                + "> [player]", LOCKED));
        return Command.SINGLE_SUCCESS;
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestNearby(CommandContext<CommandSourceStack> context,
                          com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (context.getSource().getSender() instanceof Player user) {
            String typed = builder.getRemainingLowerCase();
            for (Entity nearby : user.getNearbyEntities(24, 24, 24)) {
                if (nearby instanceof Player other
                        && other.getName().toLowerCase(java.util.Locale.ROOT).startsWith(typed)) {
                    builder.suggest(other.getName());
                }
            }
        }
        return builder.buildFuture();
    }

    /** Kept for symmetry with the other command classes. */
    static Component error(String text) {
        return Component.text(text, BAD);
    }

    /** Exposed so callers can check a slot exists before offering it. */
    public Optional<Power> powerAt(Ability ability, int slot) {
        return ability.power(slot);
    }
}
