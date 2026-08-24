package com.liminalis.plugin.command;

import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierType;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * {@code /profile} - what a player is.
 *
 * <p>Players are told everything on their first join, but a join message scrolls away. This
 * is where they come back to read it properly, and where they will keep coming back as
 * blessings, curses, injuries and an ability accumulate on top.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ProfileCommand {

    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;
    private static final NamedTextColor FAINT = NamedTextColor.DARK_GRAY;

    private final ConfigService config;
    private final ProfileManager profiles;
    private final ModifierRegistry registry;
    private final Messages messages;

    public ProfileCommand(ConfigService config,
                          ProfileManager profiles,
                          ModifierRegistry registry,
                          Messages messages) {
        this.config = Objects.requireNonNull(config);
        this.profiles = Objects.requireNonNull(profiles);
        this.registry = Objects.requireNonNull(registry);
        this.messages = Objects.requireNonNull(messages);
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("profile")
                .requires(source -> source.getSender() instanceof Player)
                .executes(this::show)
                .build();
    }

    private int show(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerProfile profile = profiles.of(player);

        player.sendMessage(Component.text("-- " + player.getName() + " --", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  Lives: ", LABEL)
                .append(Component.text(profile.livesRemaining() + " / "
                        + config.get().lives().startingLives(), VALUE))
                .append(Component.text(profile.inLimbo() ? "   (in Limbo)" : "", FAINT)));

        describe(player, "Traits", profile.traitIds());
        describeOne(player, "Blessing", profile.blessingId());
        describeOne(player, "Curse", profile.curseId());
        describe(player, "Marks", profile.markIds());
        describeOne(player, "Ability", profile.abilityId());

        return Command.SINGLE_SUCCESS;
    }

    private void describe(Player player, String label, Set<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        player.sendMessage(Component.text("  " + label + ":", LABEL));
        for (String id : ids) {
            line(player, id);
        }
    }

    private void describeOne(Player player, String label, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        player.sendMessage(Component.text("  " + label + ":", LABEL));
        line(player, id);
    }

    /**
     * One modifier, named and explained.
     *
     * <p>Falls back to the raw id when a modifier is not registered in this build, rather
     * than hiding it. A player seeing an id they do not recognise is a far better outcome
     * than a trait silently vanishing from their own profile screen.
     */
    private void line(Player player, String id) {
        Modifier modifier = registry.find(id).orElse(null);
        if (modifier == null) {
            player.sendMessage(Component.text("    " + id, VALUE)
                    .append(Component.text("  (not in this build)", FAINT)));
            return;
        }
        player.sendMessage(Component.text("    ", VALUE)
                .append(messages.get(modifier.nameKey())));
        player.sendMessage(Component.text("      ", FAINT)
                .append(messages.get(modifier.descriptionKey())));
    }

    /** Registered trait ids, used by the admin subtree for tab completion. */
    public List<String> traitIds() {
        return registry.ofType(ModifierType.TRAIT).stream().map(Modifier::id).toList();
    }
}
