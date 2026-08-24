package com.liminalis.plugin.command;

import com.liminalis.core.limbo.GhostVisitRules;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.limbo.GhostVisitService;
import com.liminalis.plugin.limbo.LimboService;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code /limbo} - the only command the dead have.
 *
 * <p>Separate from the admin tree because this belongs to players, and specifically to
 * players who have lost everything. It is deliberately tiny: see who else is here, and go
 * out among the living for a few minutes.
 */
@SuppressWarnings("UnstableApiUsage")
public final class LimboPlayerCommand {

    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;
    private static final NamedTextColor BAD = NamedTextColor.RED;

    private final ProfileManager profiles;
    private final LimboService limbo;
    private final GhostVisitService ghosts;
    private final Messages messages;

    public LimboPlayerCommand(ProfileManager profiles,
                              LimboService limbo,
                              GhostVisitService ghosts,
                              Messages messages) {
        this.profiles = Objects.requireNonNull(profiles);
        this.limbo = Objects.requireNonNull(limbo);
        this.ghosts = Objects.requireNonNull(ghosts);
        this.messages = Objects.requireNonNull(messages);
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("limbo")
                .requires(source -> source.getSender() instanceof Player)
                .executes(this::status)
                .then(Commands.literal("visit").executes(this::visit))
                .then(Commands.literal("return").executes(this::returnEarly))
                .then(Commands.literal("who").executes(this::who))
                .build();
    }

    private int status(CommandContext<CommandSourceStack> context) {
        Player player = player(context);
        PlayerProfile profile = profiles.of(player);
        if (!profile.inLimbo()) {
            messages.send(player, "limbo.not-dead");
            return Command.SINGLE_SUCCESS;
        }

        long now = System.currentTimeMillis();
        long here = profile.limboSince() > 0 ? now - profile.limboSince() : 0;
        long cooldown = GhostVisitRules.cooldownRemainingMillis(profile, now);

        player.sendMessage(Component.text("You have been in Limbo for ", LABEL)
                .append(Component.text(describe(here), VALUE)).append(Component.text(".", LABEL)));
        player.sendMessage(cooldown <= 0
                ? Component.text("You may walk among the living. /limbo visit", VALUE)
                : Component.text("You may walk again in " + describe(cooldown) + ".", LABEL));
        return Command.SINGLE_SUCCESS;
    }

    private int visit(CommandContext<CommandSourceStack> context) {
        ghosts.begin(player(context));
        return Command.SINGLE_SUCCESS;
    }

    private int returnEarly(CommandContext<CommandSourceStack> context) {
        ghosts.end(player(context));
        return Command.SINGLE_SUCCESS;
    }

    /** Who else is trapped here. Limbo is shared, and knowing that is half the comfort. */
    private int who(CommandContext<CommandSourceStack> context) {
        Player player = player(context);
        if (!profiles.of(player).inLimbo()) {
            messages.send(player, "limbo.not-dead");
            return Command.SINGLE_SUCCESS;
        }

        int found = 0;
        player.sendMessage(Component.text("-- Also here --", LABEL));
        for (Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
            Optional<PlayerProfile> profile = profiles.resident(other.getUniqueId());
            if (profile.isEmpty() || !profile.get().inLimbo()
                    || other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            found++;
            boolean away = limbo.isGhosting(other.getUniqueId());
            player.sendMessage(Component.text("  " + other.getName(), VALUE)
                    .append(Component.text(away ? "  (among the living)" : "", LABEL)));
        }
        if (found == 0) {
            player.sendMessage(Component.text("  no one. You are alone here.", LABEL));
        }
        return Command.SINGLE_SUCCESS;
    }

    private Player player(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text("Only a player can use this.", BAD));
        throw new IllegalStateException("non-player reached a player-only command");
    }

    private static String describe(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0, millis));
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.toSeconds() % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }
}
