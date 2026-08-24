package com.liminalis.plugin.command;

import com.liminalis.plugin.singularity.LoreBooks;
import com.liminalis.plugin.singularity.SingularityMob;
import com.liminalis.plugin.singularity.SingularityResidue;
import com.liminalis.plugin.singularity.SingularityService;
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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The {@code singularity} branch of the admin tree.
 *
 * <p>Almost entirely a testing tool, and unapologetically so. A wave rolls once every thirty
 * minutes at fifty percent per player, which means verifying anything about the creatures or
 * their drops by waiting would take an evening. {@code forcewave} and {@code spawn} turn that
 * into a keystroke.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SingularityCommands {

    private static final NamedTextColor ACCENT = NamedTextColor.AQUA;
    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;
    private static final NamedTextColor GOOD = NamedTextColor.GREEN;
    private static final NamedTextColor BAD = NamedTextColor.RED;

    private final JavaPlugin plugin;
    private final SingularityService singularity;
    private final Messages messages;
    private final AuditLog audit;
    private final SuggestionProvider<CommandSourceStack> playerNames;

    public SingularityCommands(JavaPlugin plugin,
                               SingularityService singularity,
                               Messages messages,
                               AuditLog audit,
                               SuggestionProvider<CommandSourceStack> playerNames) {
        this.plugin = Objects.requireNonNull(plugin);
        this.singularity = Objects.requireNonNull(singularity);
        this.messages = Objects.requireNonNull(messages);
        this.audit = Objects.requireNonNull(audit);
        this.playerNames = Objects.requireNonNull(playerNames);
    }

    public LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("singularity")
                .requires(permission("liminalis.admin.singularity"))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("forcewave").executes(this::forceWave))
                .then(Commands.literal("spawn")
                        .then(mobArgument()
                                .executes(context -> spawn(context, 1, null))
                                .then(Commands.argument("count",
                                                IntegerArgumentType.integer(1, 20))
                                        .executes(context -> spawn(context,
                                                IntegerArgumentType.getInteger(context, "count"),
                                                null))
                                        .then(Commands.argument("near",
                                                        StringArgumentType.word())
                                                .suggests(playerNames)
                                                .executes(context -> spawn(context,
                                                        IntegerArgumentType.getInteger(
                                                                context, "count"),
                                                        StringArgumentType.getString(
                                                                context, "near")))))))
                .then(Commands.literal("book")
                        .then(bookArgument().executes(this::giveBook)))
                .then(Commands.literal("residue")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(this::giveResidue)));
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("-- Creatures --", ACCENT));
        for (SingularityMob mob : SingularityMob.all()) {
            // Scale and weight shown because they are what you actually reach for when
            // something is appearing too often or looming too large.
            sender.sendMessage(Component.text("  " + mob.id(), VALUE)
                    .append(Component.text("  " + mob.base()
                            + "  x" + mob.scale() + " scale"
                            + "  " + mob.maxHealth() + "hp"
                            + "  " + mob.attackDamage() + "dmg"
                            + "  weight " + mob.weight(), LABEL)));
        }
        sender.sendMessage(Component.text("-- Books --", ACCENT));
        for (LoreBooks.LoreBook book : LoreBooks.all()) {
            sender.sendMessage(Component.text("  " + book.id(), VALUE)
                    .append(Component.text("  \"" + book.title() + "\"", LABEL)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int forceWave(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        int placed = singularity.runWave();
        sender.sendMessage(placed == 0
                ? Component.text("The wave rolled nothing, or found nowhere to put it.", LABEL)
                : Component.text("Placed " + placed + " creature(s).", GOOD));
        audit.record(name(sender), "singularity.forcewave", "-", null, placed + " placed");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Puts creatures in the world.
     *
     * <p>Takes a count and an optional target so a fight can be staged without running the
     * command eight times, and so an operator can drop something on somebody else without
     * standing next to them. Both were sorely missed while testing the roster.
     */
    private int spawn(CommandContext<CommandSourceStack> context, int count, String targetName) {
        CommandSender sender = context.getSource().getSender();
        SingularityMob mob = mob(sender, context);
        if (mob == null) {
            return Command.SINGLE_SUCCESS;
        }

        Player target;
        if (targetName != null) {
            target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage(Component.text(targetName + " is not online.", BAD));
                return Command.SINGLE_SUCCESS;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(Component.text(
                    "From console, name the player it should appear near.", BAD));
            return Command.SINGLE_SUCCESS;
        }

        int placed = 0;
        for (int i = 0; i < count; i++) {
            if (singularity.spawnNear(target, mob).isPresent()) {
                placed++;
            }
        }

        if (placed == 0) {
            sender.sendMessage(Component.text(
                    "Found nowhere suitable near " + target.getName() + ".", BAD));
        } else {
            sender.sendMessage(Component.text(placed + "x " + mob.id()
                    + " near " + target.getName()
                    + (placed < count ? "  (" + (count - placed) + " had nowhere to go)" : ""),
                    GOOD));
        }
        audit.record(name(sender), "singularity.spawn", target.getName(),
                null, placed + "x " + mob.id());
        return Command.SINGLE_SUCCESS;
    }

    private int giveBook(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can be handed a book.", BAD));
            return Command.SINGLE_SUCCESS;
        }
        String id = StringArgumentType.getString(context, "book");
        LoreBooks.LoreBook book = LoreBooks.byId(id);
        if (book == null) {
            sender.sendMessage(Component.text("No book called '" + id
                    + "'. Try /liminalis singularity list.", BAD));
            return Command.SINGLE_SUCCESS;
        }
        give(player, book.toItem());
        sender.sendMessage(Component.text("Gave \"" + book.title() + "\".", GOOD));
        audit.record(name(sender), "singularity.book", name(sender), null, id);
        return Command.SINGLE_SUCCESS;
    }

    private int giveResidue(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can be handed residue.", BAD));
            return Command.SINGLE_SUCCESS;
        }
        int amount = IntegerArgumentType.getInteger(context, "amount");
        give(player, SingularityResidue.create(plugin, messages, amount));
        sender.sendMessage(Component.text("Gave " + amount + " residue.", GOOD));
        audit.record(name(sender), "singularity.residue", name(sender), null,
                Integer.toString(amount));
        return Command.SINGLE_SUCCESS;
    }

    /** Drops at the player's feet rather than vanishing if their inventory is full. */
    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(leftover ->
                player.getWorld().dropItem(player.getLocation(), leftover));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> mobArgument() {
        return Commands.argument("creature", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String typed = builder.getRemainingLowerCase();
                    SingularityMob.all().stream()
                            .map(SingularityMob::id)
                            .filter(id -> id.startsWith(typed))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> bookArgument() {
        return Commands.argument("book", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String typed = builder.getRemainingLowerCase();
                    LoreBooks.all().stream()
                            .map(LoreBooks.LoreBook::id)
                            .filter(id -> id.startsWith(typed))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    private SingularityMob mob(CommandSender sender, CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "creature");
        return SingularityMob.all().stream()
                .filter(mob -> mob.id().equals(id))
                .findFirst()
                .orElseGet(() -> {
                    sender.sendMessage(Component.text("No creature called '" + id
                            + "'. Try /liminalis singularity list.", BAD));
                    return null;
                });
    }

    private Predicate<CommandSourceStack> permission(String node) {
        return source -> source.getSender().hasPermission(node);
    }

    private static String name(CommandSender sender) {
        return sender.getName();
    }
}
