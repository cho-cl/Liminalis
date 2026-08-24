package com.liminalis.plugin.limbo;

import com.liminalis.plugin.config.ConfigService;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Creates and holds Limbo: an endless pale garden where nothing lives and nothing happens.
 *
 * <p>Everything that could make Limbo feel like a place to survive in is switched off. There
 * is no night, no weather, no fire, no hunger pressure and nothing to fight. What is left is
 * grey trees, grey moss, grey fog, and however many other people are trapped there.
 *
 * <p>Time is fixed at noon rather than midnight, which is the less atmospheric choice made
 * deliberately: players arrive in Limbo having just died, so their torches are on the ground
 * in the overworld. Perpetual darkness would mean arriving blind with no way to fix it.
 */
public final class LimboWorld {

    /** Noon. Bright enough to see by with nothing in your pockets. */
    private static final long FIXED_TIME = 6000L;

    private final JavaPlugin plugin;
    private final ConfigService config;

    private World world;

    public LimboWorld(JavaPlugin plugin, ConfigService config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Creates Limbo if it does not exist, loads it if it does, and applies its rules either
     * way. Safe to call on every startup.
     *
     * @return the world, or null if it could not be created
     */
    public World createOrLoad() {
        String name = config.get().limbo().worldName();

        World existing = plugin.getServer().getWorld(name);
        if (existing != null) {
            this.world = existing;
            applyRules(existing);
            return existing;
        }

        plugin.getLogger().info("Opening Limbo (" + name + ")...");
        WorldCreator creator = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .generator(new LimboGenerator())
                .biomeProvider(new PaleGardenBiomeProvider())
                .generateStructures(false);

        try {
            this.world = creator.createWorld();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Limbo could not be created", e);
            return null;
        }

        if (world == null) {
            plugin.getLogger().severe("Limbo could not be created; the server returned no world.");
            return null;
        }
        applyRules(world);
        plugin.getLogger().info("Limbo is open.");
        return world;
    }

    /**
     * Applied on every startup rather than only at creation, so changing the config or
     * editing the world by hand cannot leave Limbo in a state where someone can be hurt.
     */
    private void applyRules(World world) {
        // Nothing is alive here. Peaceful is the belt: it stops hostile spawning outright,
        // including the Creaking that pale gardens grow their own hearts for.
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setSpawnFlags(false, false);
        set(world, GameRule.DO_MOB_SPAWNING, false);
        set(world, GameRule.DO_PATROL_SPAWNING, false);
        set(world, GameRule.DO_TRADER_SPAWNING, false);
        set(world, GameRule.DO_INSOMNIA, false);

        // Nothing changes here, and it never gets dark.
        set(world, GameRule.DO_DAYLIGHT_CYCLE, false);
        set(world, GameRule.DO_WEATHER_CYCLE, false);
        set(world, GameRule.DO_FIRE_TICK, false);
        set(world, GameRule.MOB_GRIEFING, false);
        world.setTime(FIXED_TIME);
        world.setStorm(false);
        world.setThundering(false);

        // Nothing can hurt you. The damage listener is the real guarantee; these stop the
        // server even trying, which avoids the screen shake and the hurt sound.
        set(world, GameRule.FALL_DAMAGE, false);
        set(world, GameRule.DROWNING_DAMAGE, false);
        set(world, GameRule.FIRE_DAMAGE, false);
        set(world, GameRule.FREEZE_DAMAGE, false);
        set(world, GameRule.KEEP_INVENTORY, true);
        set(world, GameRule.DO_IMMEDIATE_RESPAWN, true);
        set(world, GameRule.NATURAL_REGENERATION, true);
        set(world, GameRule.SPAWN_RADIUS, 0);

        applyBorder(world);
    }

    /**
     * Limbo is described as endless, and on foot it is: the default border is five thousand
     * blocks from the centre in every direction. The border exists so that one player walking
     * in a straight line for a week cannot generate chunks without limit.
     */
    private void applyBorder(World world) {
        int radius = config.get().limbo().borderRadius();
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(radius * 2.0);
        border.setWarningDistance(0);
        border.setWarningTime(0);
        border.setDamageAmount(0.0);
        border.setDamageBuffer(0.0);
    }

    private <T> void set(World world, GameRule<T> rule, T value) {
        world.setGameRule(rule, value);
    }

    /** The Limbo world, or null if it failed to open. */
    public World world() {
        return world;
    }

    public boolean isReady() {
        return world != null;
    }

    /** Whether the given world is Limbo. */
    public boolean is(World candidate) {
        return world != null && candidate != null && world.getUID().equals(candidate.getUID());
    }

    /**
     * Where the newly dead arrive.
     *
     * <p>A single shared point rather than a scattering, because Limbo is shared and the
     * people in it are supposed to find each other.
     */
    public Location arrivalPoint() {
        if (world == null) {
            return null;
        }
        Location spawn = world.getSpawnLocation();
        int x = spawn.getBlockX();
        int z = spawn.getBlockZ();
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }
}
