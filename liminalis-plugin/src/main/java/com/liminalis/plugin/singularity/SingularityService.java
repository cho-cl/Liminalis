package com.liminalis.plugin.singularity;

import com.liminalis.core.roll.WeightedPool;
import com.liminalis.core.singularity.SingularityRules;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.limbo.LimboWorld;
import com.liminalis.plugin.boon.Curses;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Drops creatures into the world, and hands out what they were carrying.
 *
 * <p>The wave is rolled per player rather than once for the server, so the world gets busier
 * as more people are in it rather than thinner. Placement is deliberately close enough to be
 * found and far enough not to appear on top of anyone - a creature that materialised in
 * somebody's base would read as a bug rather than as a visitation.
 */
public final class SingularityService implements Listener {

    /** Marks an entity as ours, so its drops can be identified when it dies. */
    private final NamespacedKey markerKey;

    private final JavaPlugin plugin;
    private final ConfigService config;
    private final ProfileManager profiles;
    private final LimboWorld limbo;
    private final ModifierRegistry registry;
    private final ModifierService modifiers;
    private final Messages messages;
    private final Debug debug;
    private final Random random = new Random();

    private BukkitTask waveTask;

    public SingularityService(JavaPlugin plugin,
                              ConfigService config,
                              ProfileManager profiles,
                              LimboWorld limbo,
                              ModifierRegistry registry,
                              ModifierService modifiers,
                              Messages messages,
                              Debug debug) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.limbo = Objects.requireNonNull(limbo, "limbo");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.debug = Objects.requireNonNull(debug, "debug");
        this.markerKey = new NamespacedKey(plugin, "singularity_mob");
    }

    public void start() {
        long ticks = Math.max(20L, config.get().singularity().intervalSeconds() * 20L);
        waveTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::runWave, ticks, ticks);
    }

    public void stop() {
        if (waveTask != null) {
            waveTask.cancel();
            waveTask = null;
        }
    }

    // ---------------------------------------------------------------------------- waves

    /** Rolls a wave and places whatever it produces. Also the target of {@code forcewave}. */
    public int runWave() {
        List<Player> candidates = eligiblePlayers();
        int count = SingularityRules.spawnCountFor(candidates.size(),
                config.get().singularity().chancePerPlayer(), random);
        if (count == 0) {
            return 0;
        }

        int placed = 0;
        for (int i = 0; i < count; i++) {
            Player near = chooseTarget(candidates);
            if (spawnNear(near, randomType()).isPresent()) {
                placed++;
            }
        }
        int total = placed;
        debug.log(() -> "singularity wave: " + total + " of " + count + " placed near "
                + candidates.size() + " eligible player(s)");
        return placed;
    }

    /**
     * Picks who a creature is sent to, weighted toward the Marked.
     *
     * <p>The cost half of that curse, and the half that makes it a curse at all. Everyone
     * else is an equal draw; a Marked player is worth several entries in the same hat, so
     * over a season the thing that arrives every half hour arrives near them far more often
     * than chance would ever explain. They will notice, and so will everyone standing next
     * to them.
     */
    private Player chooseTarget(List<Player> candidates) {
        double total = 0;
        for (Player candidate : candidates) {
            total += weightOf(candidate);
        }
        double roll = random.nextDouble() * total;
        for (Player candidate : candidates) {
            roll -= weightOf(candidate);
            if (roll <= 0) {
                return candidate;
            }
        }
        // Floating-point drift only; the last candidate is as good an answer as any.
        return candidates.get(candidates.size() - 1);
    }

    private double weightOf(Player player) {
        Curses.Marked marked = markedCurse();
        return marked != null && modifiers.carries(player, marked.id())
                ? Math.max(1.0, marked.spawnWeight()) : 1.0;
    }

    private Curses.Marked markedCurse() {
        return registry.find("marked")
                .filter(Curses.Marked.class::isInstance)
                .map(Curses.Marked.class::cast)
                .orElse(null);
    }

    /**
     * Players a creature could be sent to.
     *
     * <p>Excludes anyone in Limbo, who is beyond its reach, and anyone spectating, who cannot
     * be threatened and would only get a creature wandering an empty field.
     */
    private List<Player> eligiblePlayers() {
        List<Player> eligible = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (limbo.is(player.getWorld())
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || profiles.resident(player.getUniqueId())
                            .map(profile -> profile.inLimbo()).orElse(true)) {
                continue;
            }
            eligible.add(player);
        }
        return eligible;
    }

    /**
     * Picks what arrives.
     *
     * <p>Weighted rather than uniform, so the Herald stays the thing you tell people about
     * and the Mote stays the thing you stop noticing. A uniform draw across eight creatures
     * would make a Warden as common as a zombie, which would be a different server.
     */
    private SingularityMob randomType() {
        List<SingularityMob> types = SingularityMob.all();
        return WeightedPool.pick(types.stream().map(SingularityMob::asEntry).toList(),
                        java.util.Set.of(), random)
                .flatMap(id -> types.stream().filter(t -> t.id().equals(id)).findFirst())
                .orElse(types.get(0));
    }

    // -------------------------------------------------------------------------- placing

    /**
     * Places one creature in the world near a player.
     *
     * @return the entity, or empty if nowhere suitable was found
     */
    public Optional<LivingEntity> spawnNear(Player player, SingularityMob type) {
        Location spot = findSpot(player);
        if (spot == null) {
            debug.log(() -> "no valid spot found near " + player.getName());
            return Optional.empty();
        }
        return Optional.of(spawnAt(spot, type));
    }

    /** Creates and dresses a creature at an exact location. Used by the admin command too. */
    public LivingEntity spawnAt(Location location, SingularityMob type) {
        World world = location.getWorld();
        LivingEntity entity = (LivingEntity) world.spawnEntity(location, type.base());

        entity.getPersistentDataContainer().set(
                markerKey, PersistentDataType.STRING, type.id());
        entity.customName(messages.get("singularity." + type.id() + ".name"));
        entity.setCustomNameVisible(false);
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        entity.setGlowing(true);

        set(entity, Attribute.MAX_HEALTH, type.maxHealth());
        entity.setHealth(type.maxHealth());
        set(entity, Attribute.ATTACK_DAMAGE, type.attackDamage());
        set(entity, Attribute.FOLLOW_RANGE, type.followRange());

        AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * type.speedScalar());
        }

        // Size is a first-class part of what a creature is here, not decoration. Applied as
        // a base value so it survives anything else touching the attribute.
        AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
        if (scale != null && type.scale() != 1.0) {
            scale.setBaseValue(type.scale());
        }

        // These do not burn away at dawn. Something that only exists at night is a mob;
        // something that stands in the sunlight waiting is not.
        entity.setCanPickupItems(false);
        equipAura(entity, type);

        debug.log(() -> "spawned " + type.id() + " at "
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        return entity;
    }

    private void equipAura(LivingEntity entity, SingularityMob type) {
        // One task per creature would be exactly the thing the modifier system exists to
        // avoid, so the aura rides the wave loop instead - drawn for everything alive when
        // the next wave is rolled would be too sparse, so it is drawn on a short repeat that
        // cancels itself with the entity.
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!entity.isValid() || entity.isDead()) {
                task.cancel();
                return;
            }
            entity.getWorld().spawnParticle(type.aura(),
                    entity.getLocation().add(0, 1.0, 0), 4, 0.25, 0.5, 0.25, 0.0);
        }, 20L, 20L);
    }

    /**
     * Finds ground near a player to put something on.
     *
     * <p>Tries a handful of random bearings and gives up rather than searching exhaustively.
     * A wave that quietly skips a player standing in a cave is much better than one that
     * spends a tick scanning for somewhere to put a zombie.
     */
    private Location findSpot(Player player) {
        var settings = config.get().singularity();
        World world = player.getWorld();

        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = settings.minDistance()
                    + random.nextDouble() * (settings.maxDistance() - settings.minDistance());

            Vector offset = new Vector(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            Location candidate = player.getLocation().add(offset);

            int x = candidate.getBlockX();
            int z = candidate.getBlockZ();
            if (!world.getChunkAt(candidate).isLoaded()) {
                continue;
            }
            int y = world.getHighestBlockYAt(x, z);
            Location ground = new Location(world, x + 0.5, y + 1, z + 0.5);

            if (ground.getBlock().isPassable() && ground.clone().add(0, 1, 0).getBlock().isPassable()
                    && !ground.clone().subtract(0, 1, 0).getBlock().isPassable()
                    && !ground.clone().subtract(0, 1, 0).getBlock().isLiquid()) {
                return ground;
            }
        }
        return null;
    }

    private static void set(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    // ---------------------------------------------------------------------------- drops

    /**
     * Hands over what a dead creature was carrying.
     *
     * <p>Vanilla drops are cleared first. These are not zombies, and rotten flesh would say
     * otherwise louder than any amount of naming and particles.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        String typeId = typeIdOf(event.getEntity());
        if (typeId == null) {
            return;
        }
        event.getDrops().clear();

        var settings = config.get().singularity();
        Player killer = event.getEntity().getKiller();

        // The Marked are given the book every time. The books are the only source of
        // knowledge about any of this - what the grey is, how revival works - so a player
        // who always gets one becomes the reason the server understands anything at all.
        // That is the gift they are paying for by being hunted.
        double bookChance = killer != null && modifiers.carries(killer, "marked")
                ? 1.0 : settings.bookDropChance();
        SingularityRules.rollBook(LoreBooks.asEntries(), bookChance, random)
                .map(LoreBooks::byId)
                .ifPresent(book -> event.getDrops().add(book.toItem(plugin)));

        int residue = settings.minResidue()
                + random.nextInt(Math.max(1, settings.maxResidue() - settings.minResidue() + 1));
        if (residue > 0) {
            event.getDrops().add(SingularityResidue.create(plugin, messages, residue));
        }

        if (killer != null) {
            messages.send(killer, "singularity.slain",
                    Messages.placeholder("creature",
                            (Component) messages.get("singularity." + typeId + ".name")));
        }
        debug.log(() -> typeId + " killed, dropped " + event.getDrops().size() + " stack(s)");
    }

    /** The id of the creature type, or null if this was an ordinary mob. */
    public String typeIdOf(Entity entity) {
        return entity.getPersistentDataContainer()
                .get(markerKey, PersistentDataType.STRING);
    }

    public NamespacedKey markerKey() {
        return markerKey;
    }

    /** Diagnostic colour for the admin command. */
    static Component label(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }
}
