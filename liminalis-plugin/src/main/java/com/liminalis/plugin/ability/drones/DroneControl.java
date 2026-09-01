package com.liminalis.plugin.ability.drones;

import com.liminalis.plugin.text.Messages;
import com.liminalis.plugin.trait.TraitTuning;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looking through a drone.
 *
 * <p>The player is not really made into a bee - they are flown to where the bee is, given
 * flight, slowed at digging, and cut down to two usable slots, while the drone itself is
 * hidden and kept at their position so it reads as the thing being piloted. Everything the
 * brief asked for follows from being genuinely present rather than spectating: blocks can be
 * broken and placed, chests open, and the world behaves normally, none of which a spectator
 * can do.
 *
 * <p><strong>You look like a bee, not like a person wearing armour.</strong> The first
 * version hid the drone and showed the player, which meant everybody watching saw somebody in
 * netherite hovering in mid-air. It is the other way round now: the pilot is made invisible
 * and the real bee is kept at their position, so to every observer - and to the pilot in
 * third person - there is a bee there and nothing else.
 *
 * <p>The pack is genuinely emptied rather than merely restricted, which is what was asked
 * for, and {@link PilotKit} is where all the care about that lives: the items are written to
 * the profile before anything is taken, so there is no moment at which they exist nowhere.
 */
public final class DroneControl implements Listener {

    /** Hotbar slots a pilot may use. Two, as the brief asked. */
    private static final int USABLE_SLOTS = 2;

    private final JavaPlugin plugin;
    private final TraitTuning tuning;
    private final Messages messages;
    private final PilotKit kit;

    /** Where each pilot was standing when they left, so they can be put back. */
    private final Map<UUID, Location> bodies = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> couldFly = new ConcurrentHashMap<>();

    public DroneControl(JavaPlugin plugin, TraitTuning tuning, Messages messages,
                        PilotKit kit) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.kit = Objects.requireNonNull(kit, "kit");
    }

    public PilotKit kit() {
        return kit;
    }

    public boolean isPiloting(Player player) {
        return bodies.containsKey(player.getUniqueId());
    }

    /**
     * Takes hold of a drone.
     *
     * <p>Refused in creative and spectator, where flight and reach already do everything this
     * does and the restore afterwards would fight whatever the player had set.
     */
    public boolean take(Player player, DroneFleet fleet, int index) {
        if (player.getGameMode() != GameMode.SURVIVAL
                && player.getGameMode() != GameMode.ADVENTURE) {
            messages.send(player, "ability.drones.not-in-this-mode");
            return false;
        }
        if (index < 0 || index >= fleet.size()) {
            return false;
        }
        if (!isPiloting(player)) {
            // Everything that has to be put back, recorded before anything is changed - and
            // the pack put aside before the flight begins, so a refusal here costs nothing.
            if (!kit.putAside(player)) {
                messages.send(player, "ability.drones.cannot-stow");
                return false;
            }
            bodies.put(player.getUniqueId(), player.getLocation().clone());
            couldFly.put(player.getUniqueId(), player.getAllowFlight());
        }
        fleet.control(index);

        Bee bee = fleet.controlled();
        player.setAllowFlight(true);
        player.setFlying(true);
        player.teleport(bee.getLocation().clone().add(0, 0.4, 0));
        player.getInventory().setHeldItemSlot(0);

        // The player disappears and the bee stays. Anybody watching sees a drone, which is
        // the whole idea - the previous arrangement showed a person in full armour hovering
        // in mid-air while the actual bee was hidden somewhere inside them.
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION, 0, true, false, false));
        bee.setInvisible(false);
        bee.setSilent(true);
        bee.setTarget(null);

        player.playSound(player.getLocation(), Sound.BLOCK_BEEHIVE_ENTER, 0.9f, 1.5f);
        messages.send(player, "ability.drones.piloting",
                Messages.placeholder("drone", index + 1),
                Messages.placeholder("total", fleet.size()));
        return true;
    }

    /**
     * Lets go, and puts everything back the way it was.
     *
     * <p>Safe to call on somebody who is not piloting, and called from every exit there is -
     * the power, logging out, the drone dying, the server stopping. A restore that only ran on
     * the tidy path would leave somebody flying.
     */
    public void release(Player player, DroneFleet fleet) {
        Location body = bodies.remove(player.getUniqueId());
        Bee bee = fleet == null ? null : fleet.controlled();
        if (fleet != null) {
            fleet.release();
        }
        if (bee != null && bee.isValid()) {
            bee.setInvisible(false);
            bee.setSilent(false);
        }
        if (body == null) {
            return;
        }
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        kit.giveBack(player);

        // Read before removing. Removing and then reading gives null every time, which would
        // have quietly taken flight away from anybody who legitimately had it.
        boolean flew = Boolean.TRUE.equals(couldFly.remove(player.getUniqueId()));
        player.setFlying(false);
        player.setAllowFlight(flew);
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        if (body.getWorld() != null) {
            player.teleport(body);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BEEHIVE_EXIT, 0.9f, 0.9f);
        messages.send(player, "ability.drones.released");
    }

    /**
     * Holds the illusion together, four times a second.
     *
     * <p>The drone is dragged to the pilot rather than the other way around. Steering a bee
     * by pathfinding and following it with the camera was the alternative and it feels like
     * watching an animal, not flying a machine - a player wants to go where they are looking.
     */
    public void tick(Player player, DroneFleet fleet) {
        if (!isPiloting(player)) {
            return;
        }
        Bee bee = fleet.controlled();
        if (bee == null || !bee.isValid()) {
            release(player, fleet);
            return;
        }
        // Held just behind and below the camera so the pilot's own view is not filled with
        // their drone, while everybody else sees it exactly where the player is.
        bee.teleport(player.getLocation().clone().subtract(
                player.getLocation().getDirection().multiply(0.4)).add(0, -0.3, 0));
        bee.setInvisible(false);
        bee.setTarget(null);
        Drone.keepAlive(bee);

        if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    PotionEffect.INFINITE_DURATION, 0, true, false, false));
        }

        // Flight is re-asserted because the server takes it away on its own whenever the
        // player lands or changes world, and a pilot dropping out of the sky mid-flight would
        // read as the ability breaking.
        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
        if (!player.isFlying()) {
            player.setFlying(true);
        }
        player.setFallDistance(0f);

        int slowness = (int) tuning.get("drones.control-mining-fatigue", 1.0);
        if (slowness > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
                    40, slowness - 1, true, false, false));
        }

        double range = tuning.get("drones.control-range", 48.0);
        Location body = bodies.get(player.getUniqueId());
        if (body != null && body.getWorld() != null
                && (!player.getWorld().equals(body.getWorld())
                    || player.getLocation().distance(body) > range)) {
            // Snapped back rather than blocked: a pilot cannot be stopped mid-air without it
            // looking like a bug, and a drone flown across the map is a scouting tool that
            // makes the rest of the world small.
            messages.send(player, "ability.drones.too-far");
            release(player, fleet);
        }
    }

    // ---------------------------------------------------------------------- the pack

    /**
     * Two slots, and they are the only two there are.
     *
     * <p>Nothing has to be blocked any more. The pilot's own pack was put aside on the way
     * in, so what they are carrying is whatever they have picked up since - and a drone that
     * can hold two things is a drone that can fetch two things out of a chest, which is what
     * the two slots were always for. Anything past the second is refused so that a full
     * inventory cannot be flown home in one trip.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isPiloting(player)) {
            return;
        }
        boolean ownPack = event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory());
        if (!ownPack || event.getSlot() < USABLE_SLOTS) {
            return;
        }
        event.setCancelled(true);
        messages.send(player, "ability.drones.two-slots");
    }

    /** And nothing can be picked up into a slot that does not exist. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickUp(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isPiloting(player)) {
            return;
        }
        if (player.getInventory().firstEmpty() < 0
                || player.getInventory().firstEmpty() >= USABLE_SLOTS) {
            event.setCancelled(true);
        }
    }
}
