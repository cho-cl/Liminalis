package com.liminalis.plugin.ability.drones;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * One drone: a bee that belongs to somebody.
 *
 * <p>A bee rather than a bespoke entity because a bee already flies, already navigates in
 * three dimensions, and already looks like a small machine somebody built. Everything below
 * is about taking the parts of being a bee that get in the way and turning them off.
 *
 * <p>Ownership lives in persistent data on the entity rather than in a map in memory. Maps
 * are lost on a restart and drones are not - a bee that survived a reload with no owner would
 * be a hostile insect with a name tag wandering somebody's base forever.
 */
public final class Drone {

    private static final String OWNER = "drone_owner";
    private static final String INDEX = "drone_index";

    private Drone() {
    }

    public static NamespacedKey ownerKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, OWNER);
    }

    public static NamespacedKey indexKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, INDEX);
    }

    /**
     * Turns a freshly spawned bee into somebody's drone.
     *
     * <p>The list of things switched off here is the whole trick. A bee looks for flowers,
     * looks for a hive, gets angry on its own schedule, dies a few seconds after stinging
     * anybody, and despawns when its owner walks away. A drone does none of that, and every
     * one of those is a separate switch.
     */
    public static void adopt(JavaPlugin plugin, Bee bee, Player owner, int index) {
        bee.getPersistentDataContainer().set(
                ownerKey(plugin), PersistentDataType.STRING, owner.getUniqueId().toString());
        bee.getPersistentDataContainer().set(indexKey(plugin), PersistentDataType.INTEGER, index);

        bee.customName(net.kyori.adventure.text.Component.text(
                owner.getName() + "'s Drone " + (index + 1)));
        bee.setCustomNameVisible(false);

        // Never despawns and never wanders off looking for the countryside.
        bee.setRemoveWhenFarAway(false);
        bee.setPersistent(true);
        bee.setHasNectar(false);
        bee.setHive(null);
        bee.setFlower(null);
        bee.setCannotEnterHiveTicks(Integer.MAX_VALUE);
        bee.setAnger(0);
        bee.setAdult();
        bee.setAgeLock(true);
        bee.setBreed(false);
        bee.setCollidable(false);
    }

    /** The player this belongs to, or null if the entity is an ordinary bee. */
    public static UUID ownerOf(JavaPlugin plugin, Entity entity) {
        if (!(entity instanceof Bee)) {
            return null;
        }
        String raw = entity.getPersistentDataContainer()
                .get(ownerKey(plugin), PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isDrone(JavaPlugin plugin, Entity entity) {
        return ownerOf(plugin, entity) != null;
    }

    public static boolean belongsTo(JavaPlugin plugin, Entity entity, Player player) {
        return player.getUniqueId().equals(ownerOf(plugin, entity));
    }

    /**
     * Keeps a drone from dying of its own biology.
     *
     * <p>A vanilla bee that stings a player dies shortly afterwards. A drone that its owner
     * pointed at somebody would therefore be a single-use weapon, which is not what any of
     * this is - so the sting is un-stung every tick and the anger timer held at zero.
     */
    public static void keepAlive(Bee bee) {
        if (bee.hasStung()) {
            bee.setHasStung(false);
        }
        bee.setHasNectar(false);
    }

    /** Where a drone should hover relative to its owner, fanned out so they do not stack. */
    public static Location idlePosition(Player owner, int index, int total, double radius) {
        double angle = total <= 1 ? 0 : (Math.PI * 2 / total) * index;
        return owner.getLocation().add(
                Math.cos(angle) * radius, 1.8 + (index % 2) * 0.4, Math.sin(angle) * radius);
    }
}
