package com.liminalis.plugin.command;

import com.liminalis.core.ability.AbilityLevels;
import com.liminalis.core.command.ConfirmationTracker;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.ability.Ability;
import com.liminalis.plugin.ability.AbilityService;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.limbo.LimboService;
import com.liminalis.plugin.limbo.LimboWorld;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.modifier.ModifierType;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.singularity.SingularityMob;
import com.liminalis.plugin.singularity.SingularityService;
import com.liminalis.plugin.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * {@code /lim} - everything an admin does, one word deep.
 *
 * <p>This replaces thirteen subtrees of four and five words each. Handing somebody a trait was
 * {@code /liminalis trait give Bryce short}; a curse was {@code /liminalis curse give}; a wound
 * was {@code /liminalis injury give}; an ability was {@code /liminalis ability set}. Four
 * different paths, four different verbs, and the operator had to know which noun the thing
 * they wanted was filed under before they could begin typing it.
 *
 * <p>It is one verb now. {@code /lim give Bryce short} works, and so does
 * {@code /lim give Bryce hollow} and {@code /lim give Bryce bleeding} and
 * {@code /lim give Bryce priest}, because the id says what kind of thing it is and there is
 * no reason the operator should have to say it twice.
 *
 * <p><strong>What the split-out subtrees were protecting is kept in a better place.</strong>
 * Blessings and curses were separated so nobody could hand out a curse believing it was a
 * gift. One completion list brings that risk back, so the protection moved into the result:
 * every grant states the kind in as many words - <em>gave Bryce the CURSE Hollow</em> - and
 * the granular permissions moved from the command path onto the resolved type, so a moderator
 * trusted with wounds and not with abilities is still exactly that.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AdminCommand {

    private static final NamedTextColor GOOD = NamedTextColor.GREEN;
    private static final NamedTextColor BAD = NamedTextColor.RED;
    private static final NamedTextColor WARN = NamedTextColor.YELLOW;
    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;
    private static final NamedTextColor ACCENT = NamedTextColor.AQUA;

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final ConfigService config;
    private final Messages messages;
    private final ProfileManager profiles;
    private final ModifierRegistry registry;
    private final ModifierService modifiers;
    private final AbilityService abilities;
    private final LimboService limbo;
    private final LimboWorld limboWorld;
    private final SingularityService singularity;
    private final ItemsMenu items;
    private final Grants grants;
    private final AuditLog audit;
    private final ConfirmationTracker confirmations;
    private final Debug debug;
    private final Supplier<Optional<java.nio.file.Path>> backup;

    public AdminCommand(org.bukkit.plugin.java.JavaPlugin plugin,
                        ConfigService config,
                        Messages messages,
                        ProfileManager profiles,
                        ModifierRegistry registry,
                        ModifierService modifiers,
                        AbilityService abilities,
                        LimboService limbo,
                        LimboWorld limboWorld,
                        SingularityService singularity,
                        ItemsMenu items,
                        AuditLog audit,
                        ConfirmationTracker confirmations,
                        Debug debug,
                        Supplier<Optional<java.nio.file.Path>> backup) {
        this.plugin = Objects.requireNonNull(plugin);
        this.config = Objects.requireNonNull(config);
        this.messages = Objects.requireNonNull(messages);
        this.profiles = Objects.requireNonNull(profiles);
        this.registry = Objects.requireNonNull(registry);
        this.modifiers = Objects.requireNonNull(modifiers);
        this.abilities = Objects.requireNonNull(abilities);
        this.limbo = Objects.requireNonNull(limbo);
        this.limboWorld = Objects.requireNonNull(limboWorld);
        this.singularity = Objects.requireNonNull(singularity);
        this.items = Objects.requireNonNull(items);
        this.audit = Objects.requireNonNull(audit);
        this.confirmations = Objects.requireNonNull(confirmations);
        this.debug = Objects.requireNonNull(debug);
        this.backup = Objects.requireNonNull(backup);
        this.grants = new Grants(registry);
    }

    // ---------------------------------------------------------------------------- tree

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("liminalis")
                .requires(permission("liminalis.admin"))
                .executes(this::help)

                .then(Commands.literal("give").then(player().then(anyModifier()
                        .executes(this::give))))
                .then(Commands.literal("take").then(player().then(carriedModifier()
                        .executes(this::take))))

                .then(Commands.literal("list").executes(context -> list(context, null))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests(kinds())
                                .executes(context -> list(context,
                                        StringArgumentType.getString(context, "kind")))))
                .then(Commands.literal("info").then(anyModifier().executes(this::info)))
                .then(Commands.literal("who").then(player().executes(this::who)))

                .then(Commands.literal("level").then(player().then(amount()
                        .executes(this::level))))
                .then(Commands.literal("lives").then(player().then(amount()
                        .executes(this::lives))))

                .then(Commands.literal("limbo").then(player().executes(this::sendToLimbo)))
                .then(Commands.literal("revive").then(player().executes(this::revive)))
                .then(Commands.literal("tp").executes(this::teleportToLimbo))

                .then(Commands.literal("spawn").then(Commands.argument(
                        "type", StringArgumentType.word()).suggests(creatureTypes())
                        .executes(context -> spawn(context, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                .executes(context -> spawn(context,
                                        IntegerArgumentType.getInteger(context, "count"))))))
                .then(Commands.literal("wave").executes(this::wave))

                .then(Commands.literal("reroll").then(player().executes(this::reroll)))
                .then(Commands.literal("items").executes(this::openItems))
                .then(Commands.literal("reload").executes(this::reload))
                .then(dataTree())
                .build();
    }

    /**
     * The rarely-used half, kept in one bucket.
     *
     * <p>Everything above is something an operator does while running a session. Everything
     * here is something they do when something has gone wrong, which is a different kind of
     * command and does not deserve a place at the top level competing for the same tab
     * completion.
     */
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> dataTree() {
        return Commands.literal("data")
                .requires(permission("liminalis.admin.data"))
                .then(Commands.literal("save").executes(this::saveAll))
                .then(Commands.literal("backup").executes(this::runBackup))
                .then(Commands.literal("inspect").then(player().executes(this::inspect)))
                .then(Commands.literal("debug")
                        .then(Commands.literal("on")
                                .executes(context -> setDebug(context, true)))
                        .then(Commands.literal("off")
                                .executes(context -> setDebug(context, false))))
                .then(Commands.literal("pvp")
                        .then(Commands.literal("on").executes(context -> pvp(context, true)))
                        .then(Commands.literal("off").executes(context -> pvp(context, false))))
                .then(Commands.literal("ghostreset")
                        .then(player().executes(this::ghostReset)));
    }

    private int help(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("-- Liminalis --", ACCENT));
        for (String line : List.of(
                "give <player> <id>     a trait, boon, wound or ability",
                "take <player> <id>     ...and off again. 'all' clears every wound",
                "list [kind]            every id, or one kind of them",
                "info <id>              what it does",
                "who <player>           their whole state",
                "level <player> <n>     ability level, or up / down",
                "lives <player> <n>     lives, or up / down",
                "limbo <player>         send them to the grey",
                "revive <player>        bring them back",
                "tp                     go to the grey yourself",
                "spawn <type> [n]       a Singularity creature",
                "wave                   force a Singularity wave",
                "reroll <player>        new traits",
                "items                  the item menu",
                "reload                 config and messages",
                "data ...               save, backup, inspect, debug, pvp, ghostreset")) {
            sender.sendMessage(Component.text("  " + line, LABEL));
        }
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------------ give/take

    /**
     * Puts anything on anybody.
     *
     * <p>The kind is named back in the reply on purpose. It is the last line of defence
     * against handing somebody a curse in the belief it was a blessing, now that both live in
     * the same completion list - and it costs nothing to say.
     */
    private int give(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        Modifier modifier = modifier(sender, context);
        if (profile == null || modifier == null || !allowed(sender, modifier)) {
            return Command.SINGLE_SUCCESS;
        }

        if (!grants.give(profile, modifier)) {
            sender.sendMessage(Component.text(profile.lastKnownName()
                    + " already has " + modifier.id() + ".", WARN));
            return Command.SINGLE_SUCCESS;
        }
        persist(profile);

        sender.sendMessage(Component.text("Gave ", GOOD)
                .append(Component.text(profile.lastKnownName(), VALUE))
                .append(Component.text(" the " + kindOf(modifier) + " ", GOOD))
                .append(messages.get(modifier.nameKey()))
                .append(Component.text(" (" + modifier.id() + ")", LABEL)));
        audit.record(sender.getName(), "give", profile.lastKnownName(),
                "-", modifier.type() + "/" + modifier.id());
        return Command.SINGLE_SUCCESS;
    }

    private int take(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        String id = StringArgumentType.getString(context, "id");

        // "all" is the one operation with no single id behind it, and the one most often
        // wanted: a test subject covered in wounds, cleaned up in one line.
        if (id.equalsIgnoreCase("all")) {
            int cleared = profile.injuries().size();
            profile.clearInjuries();
            persist(profile);
            sender.sendMessage(Component.text("Cleared " + cleared + " wound(s) from "
                    + profile.lastKnownName() + ".", GOOD));
            audit.record(sender.getName(), "take.all", profile.lastKnownName(),
                    cleared + " wounds", "none");
            return Command.SINGLE_SUCCESS;
        }

        Modifier modifier = modifier(sender, context);
        if (modifier == null || !allowed(sender, modifier)) {
            return Command.SINGLE_SUCCESS;
        }
        if (!grants.take(profile, modifier)) {
            sender.sendMessage(Component.text(profile.lastKnownName()
                    + " does not have " + modifier.id() + ".", WARN));
            return Command.SINGLE_SUCCESS;
        }
        persist(profile);

        sender.sendMessage(Component.text("Took the " + kindOf(modifier) + " "
                + modifier.id() + " from " + profile.lastKnownName() + ".", GOOD));
        audit.record(sender.getName(), "take", profile.lastKnownName(),
                modifier.type() + "/" + modifier.id(), "-");
        return Command.SINGLE_SUCCESS;
    }

    // ----------------------------------------------------------------------- list/info

    private int list(CommandContext<CommandSourceStack> context, String kind) {
        CommandSender sender = context.getSource().getSender();
        ModifierType only = kind == null ? null : typeNamed(kind);
        if (kind != null && only == null) {
            sender.sendMessage(Component.text("No such kind: " + kind + ". Try one of "
                    + kindNames() + ".", BAD));
            return Command.SINGLE_SUCCESS;
        }

        for (ModifierType type : ModifierType.values()) {
            if (only != null && type != only) {
                continue;
            }
            List<Modifier> of = registry.ofType(type);
            if (of.isEmpty()) {
                continue;
            }
            sender.sendMessage(Component.text(
                    "-- " + type.id() + " (" + of.size() + ") --", ACCENT));
            for (Modifier modifier : of) {
                sender.sendMessage(Component.text("  " + modifier.id() + "  ", VALUE)
                        .append(messages.get(modifier.descriptionKey())));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Modifier modifier = modifier(sender, context);
        if (modifier == null) {
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text("-- " + modifier.id() + " --", ACCENT));
        sender.sendMessage(Component.text("  kind: ", LABEL)
                .append(Component.text(kindOf(modifier), VALUE)));
        sender.sendMessage(Component.text("  name: ", LABEL)
                .append(messages.get(modifier.nameKey())));
        sender.sendMessage(Component.text("  ", LABEL)
                .append(messages.get(modifier.descriptionKey())));
        return Command.SINGLE_SUCCESS;
    }

    private int who(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        boolean online = Bukkit.getPlayer(profile.id()) != null;

        sender.sendMessage(Component.text("-- " + profile.lastKnownName() + " --", ACCENT));
        field(sender, "status", online ? "online" : "offline");
        field(sender, "lives", profile.livesRemaining() + " (" + profile.totalDeaths()
                + " death(s))");
        field(sender, "in limbo", Boolean.toString(profile.inLimbo()));
        field(sender, "traits", profile.traitIds().isEmpty()
                ? "none" : String.join(", ", profile.traitIds()));
        field(sender, "blessing", orNone(profile.blessingId()));
        field(sender, "curse", orNone(profile.curseId()));
        field(sender, "marks", profile.markIds().isEmpty()
                ? "none" : String.join(", ", profile.markIds()));
        field(sender, "ability", describeAbility(profile));
        field(sender, "wounds", profile.injuries().isEmpty() ? "none"
                : profile.injuries().stream().map(i -> i.id()).reduce(
                        (a, b) -> a + ", " + b).orElse("none"));
        return Command.SINGLE_SUCCESS;
    }

    /** Ability, level and how far off the next power is - the whole progression in a line. */
    private String describeAbility(PlayerProfile profile) {
        if (profile.abilityId() == null) {
            return "none";
        }
        Ability ability = registry.find(profile.abilityId())
                .filter(Ability.class::isInstance).map(Ability.class::cast).orElse(null);
        int uses = profile.abilityProgress().getOrDefault(AbilityLevels.USES, 0);
        if (ability == null) {
            return profile.abilityId() + " (unknown to this build)";
        }
        List<Integer> ladder = abilities.ladderFor(ability);
        int toNext = AbilityLevels.usesToNext(uses, ladder);
        return profile.abilityId() + "  level " + profile.abilityTier()
                + "/" + ability.maxLevel() + "  " + uses + " use(s)"
                + (toNext > 0 ? ", " + toNext + " to next" : ", complete");
    }

    // -------------------------------------------------------------------- level/lives

    /**
     * Sets an ability level, and the use count that earns it.
     *
     * <p>Both, always. Setting the number alone would leave somebody level four by decree and
     * level one by arithmetic, and the next power they used would drop them straight back -
     * which is the sort of thing that gets reported as the plugin eating a level.
     */
    private int level(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        Ability ability = registry.find(String.valueOf(profile.abilityId()))
                .filter(Ability.class::isInstance).map(Ability.class::cast).orElse(null);
        if (ability == null) {
            sender.sendMessage(Component.text(profile.lastKnownName()
                    + " has no ability to level. Give them one first.", WARN));
            return Command.SINGLE_SUCCESS;
        }

        List<Integer> ladder = abilities.ladderFor(ability);
        int before = profile.abilityTier();
        Integer wanted = resolveAmount(context, before, 1, ability.maxLevel());
        if (wanted == null) {
            sender.sendMessage(Component.text(
                    "Give a number, or 'up' or 'down'.", BAD));
            return Command.SINGLE_SUCCESS;
        }

        profile.setAbilityTier(wanted);
        profile.setAbilityProgress(AbilityLevels.USES,
                AbilityLevels.usesForLevel(wanted, ladder));
        persist(profile);

        sender.sendMessage(Component.text(profile.lastKnownName() + ": level "
                + before + " -> " + wanted, GOOD));
        audit.record(sender.getName(), "level", profile.lastKnownName(),
                String.valueOf(before), String.valueOf(wanted));
        return Command.SINGLE_SUCCESS;
    }

    private int lives(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        int before = profile.livesRemaining();
        Integer wanted = resolveAmount(context, before, 0, 64);
        if (wanted == null) {
            sender.sendMessage(Component.text("Give a number, or 'up' or 'down'.", BAD));
            return Command.SINGLE_SUCCESS;
        }
        // Zero lives is the state that means "in Limbo", so setting it there is destructive
        // in a way no other number is.
        if (wanted == 0 && !confirmations.submit(sender.getName(), "lives0:" + profile.id())) {
            sender.sendMessage(Component.text("That condemns " + profile.lastKnownName()
                    + ". Run it again within " + confirmations.windowSeconds()
                    + "s to confirm.", WARN));
            return Command.SINGLE_SUCCESS;
        }

        profile.setLivesRemaining(wanted);
        persist(profile);
        sender.sendMessage(Component.text(profile.lastKnownName() + ": lives "
                + before + " -> " + wanted, GOOD));
        audit.record(sender.getName(), "lives", profile.lastKnownName(),
                String.valueOf(before), String.valueOf(wanted));
        return Command.SINGLE_SUCCESS;
    }

    /** A number, or {@code up} / {@code down} relative to what they have. */
    private Integer resolveAmount(CommandContext<CommandSourceStack> context,
                                  int current, int min, int max) {
        String raw = StringArgumentType.getString(context, "amount");
        int wanted;
        if (raw.equalsIgnoreCase("up")) {
            wanted = current + 1;
        } else if (raw.equalsIgnoreCase("down")) {
            wanted = current - 1;
        } else {
            try {
                wanted = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return Math.max(min, Math.min(max, wanted));
    }

    // ---------------------------------------------------------------------------- limbo

    private int sendToLimbo(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (profile.inLimbo()) {
            sender.sendMessage(Component.text(profile.lastKnownName()
                    + " is already there.", WARN));
            return Command.SINGLE_SUCCESS;
        }
        profile.setInLimbo(true);
        profile.setLimboSince(System.currentTimeMillis());
        profile.setLivesRemaining(0);
        persist(profile);

        Player online = Bukkit.getPlayer(profile.id());
        if (online != null) {
            limbo.sendToLimbo(online);
        }
        sender.sendMessage(Component.text("Sent " + profile.lastKnownName()
                + " to Limbo.", GOOD));
        audit.record(sender.getName(), "limbo.send", profile.lastKnownName());
        return Command.SINGLE_SUCCESS;
    }

    private int revive(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (!profile.inLimbo()) {
            sender.sendMessage(Component.text(profile.lastKnownName()
                    + " is not in Limbo.", WARN));
            return Command.SINGLE_SUCCESS;
        }
        int lives = config.get().limbo().revivalLives();
        com.liminalis.core.lives.LifeRules.revive(profile, lives, "mark_of_return");
        persist(profile);

        Player online = Bukkit.getPlayer(profile.id());
        if (online != null) {
            limbo.returnToLiving(online);
        }
        sender.sendMessage(Component.text("Returned " + profile.lastKnownName()
                + " with " + lives + " life/lives and the Mark of Return.", GOOD));
        audit.record(sender.getName(), "limbo.revive", profile.lastKnownName());
        return Command.SINGLE_SUCCESS;
    }

    private int teleportToLimbo(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can go there.", BAD));
            return Command.SINGLE_SUCCESS;
        }
        var arrival = limboWorld.arrivalPoint();
        if (arrival == null) {
            sender.sendMessage(Component.text("Limbo is not open.", BAD));
            return Command.SINGLE_SUCCESS;
        }
        player.teleport(arrival);
        sender.sendMessage(Component.text("The grey.", LABEL));
        return Command.SINGLE_SUCCESS;
    }

    private int ghostReset(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        profile.setGhostVisitCooldownUntil(0L);
        profile.setCrossingCooldownUntil(0L);
        persist(profile);
        sender.sendMessage(Component.text("Cleared both crossing cooldowns for "
                + profile.lastKnownName() + ".", GOOD));
        return Command.SINGLE_SUCCESS;
    }

    // --------------------------------------------------------------------- singularity

    private int spawn(CommandContext<CommandSourceStack> context, int count) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Stand somewhere first.", BAD));
            return Command.SINGLE_SUCCESS;
        }
        String wanted = StringArgumentType.getString(context, "type");
        SingularityMob type = SingularityMob.all().stream()
                .filter(mob -> mob.id().equalsIgnoreCase(wanted))
                .findFirst().orElse(null);
        if (type == null) {
            sender.sendMessage(Component.text("No such creature: " + wanted, BAD));
            return Command.SINGLE_SUCCESS;
        }
        int placed = 0;
        for (int i = 0; i < count; i++) {
            if (singularity.spawnNear(player, type).isPresent()) {
                placed++;
            }
        }
        sender.sendMessage(Component.text("Placed " + placed + " of " + count
                + " " + type.id() + ".", placed == count ? GOOD : WARN));
        audit.record(sender.getName(), "spawn", type.id());
        return Command.SINGLE_SUCCESS;
    }

    private int wave(CommandContext<CommandSourceStack> context) {
        int placed = singularity.runWave();
        context.getSource().getSender().sendMessage(
                Component.text("Wave placed " + placed + " creature(s).",
                        placed > 0 ? GOOD : WARN));
        audit.record(context.getSource().getSender().getName(), "wave", String.valueOf(placed));
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------------- upkeep

    private int reroll(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (!confirmations.submit(sender.getName(), "reroll:" + profile.id())) {
            sender.sendMessage(Component.text("This replaces " + profile.lastKnownName()
                    + "'s traits permanently. Run it again within "
                    + confirmations.windowSeconds() + "s to confirm.", WARN));
            return Command.SINGLE_SUCCESS;
        }
        String before = String.join(", ", profile.traitIds());
        List.copyOf(profile.traitIds()).forEach(profile::removeTrait);
        profile.setFirstJoinComplete(false);
        persist(profile);

        sender.sendMessage(Component.text("Cleared " + profile.lastKnownName()
                + "'s traits. They roll again on next join.", GOOD));
        audit.record(sender.getName(), "reroll", profile.lastKnownName(), before, "pending");
        return Command.SINGLE_SUCCESS;
    }

    private int openItems(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            context.getSource().getSender().sendMessage(
                    Component.text("Only a player can open a menu.", BAD));
            return Command.SINGLE_SUCCESS;
        }
        items.openFor(player);
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        List<String> errors = config.reload();
        if (!errors.isEmpty()) {
            sender.sendMessage(Component.text("config.yml is invalid; keeping the old one:",
                    BAD));
            errors.forEach(error -> sender.sendMessage(Component.text("  " + error, BAD)));
            return Command.SINGLE_SUCCESS;
        }
        messages.reload();
        debug.set(config.get().debug());
        Bukkit.getOnlinePlayers().forEach(modifiers::applyFromProfile);
        sender.sendMessage(Component.text("Reloaded config and messages.", GOOD));
        audit.record(sender.getName(), "reload", "-");
        return Command.SINGLE_SUCCESS;
    }

    private int saveAll(CommandContext<CommandSourceStack> context) {
        profiles.residentProfiles().forEach(profiles::saveNow);
        context.getSource().getSender().sendMessage(
                Component.text("Saved every loaded profile.", GOOD));
        return Command.SINGLE_SUCCESS;
    }

    private int runBackup(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        backup.get().ifPresentOrElse(
                path -> sender.sendMessage(Component.text(
                        "Backed up to " + path.getFileName() + ".", GOOD)),
                () -> sender.sendMessage(Component.text("Backup failed; see console.", BAD)));
        return Command.SINGLE_SUCCESS;
    }

    private int inspect(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerProfile profile = profile(sender, context);
        if (profile == null) {
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text("-- raw: " + profile.lastKnownName() + " --",
                ACCENT));
        for (String line : new com.liminalis.core.profile.ProfileCodec()
                .toJson(profile).split("\n")) {
            sender.sendMessage(Component.text(line, VALUE));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int setDebug(CommandContext<CommandSourceStack> context, boolean on) {
        debug.set(on);
        context.getSource().getSender().sendMessage(
                Component.text("Debug " + (on ? "on" : "off") + ".", GOOD));
        return Command.SINGLE_SUCCESS;
    }

    private int pvp(CommandContext<CommandSourceStack> context, boolean on) {
        CommandSender sender = context.getSource().getSender();
        List<String> errors = config.setAndSave("lives.pvp-deaths-count", on);
        if (!errors.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Could not apply that - config.yml has other problems:", BAD));
            errors.forEach(error -> sender.sendMessage(Component.text("  " + error, BAD)));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text(
                "Player kills now " + (on ? "cost a life." : "cost nothing."), GOOD));
        audit.record(sender.getName(), "pvpcounts", String.valueOf(on));
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------------ arguments

    private RequiredArgumentBuilder<CommandSourceStack, String> player() {
        return Commands.argument("player", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String typed = builder.getRemainingLowerCase();
                    for (String name : profiles.knownNames()) {
                        if (name.toLowerCase(Locale.ROOT).startsWith(typed)) {
                            builder.suggest(name);
                        }
                    }
                    return builder.buildFuture();
                });
    }

    /** Every id in the registry, whoever it is for. */
    private RequiredArgumentBuilder<CommandSourceStack, String> anyModifier() {
        return Commands.argument("id", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String typed = builder.getRemainingLowerCase();
                    for (Modifier modifier : registry.all()) {
                        if (modifier.id().startsWith(typed)) {
                            builder.suggest(modifier.id());
                        }
                    }
                    return builder.buildFuture();
                });
    }

    /**
     * Only what this player is actually carrying, plus {@code all}.
     *
     * <p>Filtering {@code take} to what they have is the single biggest thing that makes one
     * shared list usable. Forty ids to scroll past when three of them can possibly apply is
     * exactly the problem separate subtrees were solving, and this solves it better: the list
     * is not merely narrower, it is the correct list.
     */
    private RequiredArgumentBuilder<CommandSourceStack, String> carriedModifier() {
        return Commands.argument("id", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String typed = builder.getRemainingLowerCase();
                    if ("all".startsWith(typed)) {
                        builder.suggest("all");
                    }
                    PlayerProfile profile = quietProfile(context);
                    if (profile != null) {
                        for (Modifier modifier : registry.all()) {
                            if (modifier.id().startsWith(typed)
                                    && grants.has(profile, modifier)) {
                                builder.suggest(modifier.id());
                            }
                        }
                    }
                    return builder.buildFuture();
                });
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> amount() {
        return Commands.argument("amount", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (String option : List.of("up", "down", "1", "2", "3", "4", "5")) {
                        if (option.startsWith(builder.getRemainingLowerCase())) {
                            builder.suggest(option);
                        }
                    }
                    return builder.buildFuture();
                });
    }

    private SuggestionProvider<CommandSourceStack> kinds() {
        return (context, builder) -> {
            for (ModifierType type : ModifierType.values()) {
                if (type.id().startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(type.id());
                }
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> creatureTypes() {
        return (context, builder) -> {
            for (SingularityMob mob : SingularityMob.all()) {
                if (mob.id().startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(mob.id());
                }
            }
            return builder.buildFuture();
        };
    }

    // -------------------------------------------------------------------------- helpers

    private PlayerProfile profile(CommandSender sender,
                                  CommandContext<CommandSourceStack> context) {
        String requested = StringArgumentType.getString(context, "player");
        Optional<UUID> id = profiles.resolve(requested);
        if (id.isEmpty()) {
            sender.sendMessage(Component.text("Liminalis has never seen '" + requested
                    + "'. Use a name it knows, or a raw UUID.", BAD));
            return null;
        }
        try {
            return profiles.lookup(id.get()).orElseGet(() -> {
                sender.sendMessage(Component.text("No stored profile for "
                        + requested + ".", BAD));
                return null;
            });
        } catch (RuntimeException e) {
            sender.sendMessage(Component.text("Profile for " + requested
                    + " could not be read: " + e.getMessage(), BAD));
            return null;
        }
    }

    /** The same lookup, for tab completion, where an error message would be noise. */
    private PlayerProfile quietProfile(CommandContext<CommandSourceStack> context) {
        try {
            String requested = StringArgumentType.getString(context, "player");
            return profiles.resolve(requested).flatMap(profiles::lookup).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Modifier modifier(CommandSender sender,
                              CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        Modifier modifier = registry.find(id).orElse(null);
        if (modifier == null) {
            sender.sendMessage(Component.text("Nothing called '" + id
                    + "'. Try /lim list.", BAD));
        }
        return modifier;
    }

    /**
     * Whether this operator may touch this kind of thing.
     *
     * <p>The granular permissions used to come from the command path, which a single shared
     * verb no longer has. Checking the resolved type instead keeps exactly the same
     * distinctions - a moderator trusted with wounds and not with abilities is still that -
     * without putting the distinction back into what has to be typed.
     */
    private boolean allowed(CommandSender sender, Modifier modifier) {
        String node = "liminalis.admin." + modifier.type().id();
        if (sender.hasPermission(node) || sender.hasPermission("liminalis.admin.*")) {
            return true;
        }
        sender.sendMessage(Component.text("You are not allowed to hand out a "
                + kindOf(modifier) + " (" + node + ").", BAD));
        return false;
    }

    private void persist(PlayerProfile profile) {
        profiles.saveNow(profile);
        Player online = Bukkit.getPlayer(profile.id());
        if (online != null) {
            modifiers.applyFromProfile(online);
        }
    }

    private static String kindOf(Modifier modifier) {
        return modifier.type() == ModifierType.INJURY ? "wound" : modifier.type().id();
    }

    private static ModifierType typeNamed(String kind) {
        for (ModifierType type : ModifierType.values()) {
            if (type.id().equalsIgnoreCase(kind) || (type.id() + "s").equalsIgnoreCase(kind)) {
                return type;
            }
        }
        return null;
    }

    private static String kindNames() {
        List<String> names = new ArrayList<>();
        for (ModifierType type : ModifierType.values()) {
            names.add(type.id());
        }
        return String.join(", ", names);
    }

    private static void field(CommandSender sender, String label, String value) {
        sender.sendMessage(Component.text("  " + label + ": ", LABEL)
                .append(Component.text(value, VALUE)));
    }

    private static String orNone(String value) {
        return value == null ? "none" : value;
    }

    private Predicate<CommandSourceStack> permission(String node) {
        return source -> source.getSender().hasPermission(node);
    }
}
