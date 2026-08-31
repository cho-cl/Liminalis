package com.liminalis.plugin.ability.drones;

import com.liminalis.core.ability.Aggression;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import com.liminalis.plugin.trait.TraitTuning;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps everybody's drones flying, working and out of trouble.
 *
 * <p>A service rather than part of the ability, for the reason every service in this plugin
 * exists: modifiers are forbidden from owning listeners or timers, and drones need five
 * listeners and a loop. The ability declares six powers and calls in here; everything about
 * what a bee is actually doing lives on this side of the line.
 *
 * <p>The loop runs four times a second. Slower and a drone hauling something visibly stutters;
 * faster buys nothing, because a bee's own movement is smoothed by the client anyway.
 */
public final class DroneService implements Listener {

    private static final long TICK_INTERVAL = 5L;

    /** Close enough to a job to do it. */
    private static final double ARRIVED = 2.5;

    private final JavaPlugin plugin;
    private final TraitTuning tuning;
    private final ProfileManager profiles;
    private final Messages messages;
    private final Debug debug;
    private final DroneControl control;

    private final Map<UUID, DroneFleet> fleets = new ConcurrentHashMap<>();

    private BukkitTask loop;

    public DroneService(JavaPlugin plugin, TraitTuning tuning, ProfileManager profiles,
                        Messages messages, Debug debug) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.debug = Objects.requireNonNull(debug, "debug");
        this.control = new DroneControl(plugin, tuning, messages);
    }

    public void start() {
        loop = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tickAll, TICK_INTERVAL, TICK_INTERVAL);
    }

    /**
     * Puts every drone away and lets go of everybody.
     *
     * <p>Not optional. A player left mid-control on shutdown would come back flying with two
     * usable inventory slots and no way to explain it, and the bees would still be there.
     */
    public void stop() {
        if (loop != null) {
            loop.cancel();
            loop = null;
        }
        for (UUID id : List.copyOf(fleets.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                control.release(player, fleetOf(player));
            }
            dismissAll(id);
        }
        fleets.clear();
    }

    public DroneFleet fleetOf(Player player) {
        return fleets.computeIfAbsent(player.getUniqueId(), id -> new DroneFleet());
    }

    public DroneControl control() {
        return control;
    }

    // ------------------------------------------------------------------------ summoning

    /**
     * Adds one drone, up to the number their level allows.
     *
     * @return the new drone, or null if the fleet is already full
     */
    public Bee summon(Player owner, int allowed) {
        DroneFleet fleet = fleetOf(owner);
        if (fleet.size() >= allowed) {
            return null;
        }
        Location at = owner.getLocation().add(owner.getLocation().getDirection().multiply(1.5))
                .add(0, 1.4, 0);

        Bee bee = owner.getWorld().spawn(at, Bee.class, spawned -> {
            Drone.adopt(plugin, spawned, owner, fleetOf(owner).size());
            double health = tuning.get("drones.health", 20.0);
            var attribute = spawned.getAttribute(Attribute.MAX_HEALTH);
            if (attribute != null) {
                attribute.setBaseValue(health);
                spawned.setHealth(health);
            }
            var damage = spawned.getAttribute(Attribute.ATTACK_DAMAGE);
            if (damage != null) {
                damage.setBaseValue(tuning.get("drones.attack-damage", 3.0));
            }
        });
        fleet.add(bee);

        bee.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 20, 0.3, 0.3, 0.3, 0.05);
        owner.playSound(at, Sound.BLOCK_BEEHIVE_EXIT, 0.8f, 1.4f);
        return bee;
    }

    /** Sends every drone away, and stops looking through one if that was happening. */
    public int dismissAll(UUID ownerId) {
        DroneFleet fleet = fleets.get(ownerId);
        if (fleet == null) {
            return 0;
        }
        Player owner = plugin.getServer().getPlayer(ownerId);
        if (owner != null) {
            control.release(owner, fleet);
        }
        int count = fleet.size();
        for (Bee bee : List.copyOf(fleet.drones())) {
            bee.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    bee.getLocation(), 16, 0.3, 0.3, 0.3, 0.05);
            bee.remove();
        }
        fleet.clear();
        return count;
    }

    // ---------------------------------------------------------------------------- loop

    private void tickAll() {
        for (Player owner : plugin.getServer().getOnlinePlayers()) {
            DroneFleet fleet = fleets.get(owner.getUniqueId());
            if (fleet == null || fleet.isEmpty()) {
                continue;
            }
            try {
                tick(owner, fleet);
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Drone tick failed for " + owner.getName(), e);
            }
        }
    }

    private void tick(Player owner, DroneFleet fleet) {
        List<Bee> drones = fleet.drones();
        control.tick(owner, fleet);

        double follow = tuning.get("drones.follow-radius", 2.4);
        double leash = tuning.get("drones.leash-distance", 24.0);
        double speed = tuning.get("drones.speed", 1.2);

        for (int i = 0; i < drones.size(); i++) {
            Bee bee = drones.get(i);
            Drone.keepAlive(bee);

            if (bee.equals(fleet.controlled())) {
                continue;
            }
            if (!bee.getWorld().equals(owner.getWorld())
                    || bee.getLocation().distance(owner.getLocation()) > leash) {
                // Recalled rather than left behind: a drone lost across a portal is a bee
                // nobody owns, wandering somebody else's base forever.
                bee.teleport(Drone.idlePosition(owner, i, drones.size(), follow));
                fleet.finish(bee);
                continue;
            }

            DroneFleet.Job job = fleet.jobOf(bee);
            if (job != null) {
                workOn(owner, fleet, bee, job, speed);
                continue;
            }
            if (bee.getTarget() != null && bee.getTarget().isValid()) {
                continue;
            }
            seekTrouble(owner, fleet, bee);
            follow(owner, bee, i, drones.size(), follow, speed);
        }
    }

    private void follow(Player owner, Bee bee, int index, int total,
                        double radius, double speed) {
        Location home = Drone.idlePosition(owner, index, total, radius);
        if (bee.getLocation().distanceSquared(home) < 1.5) {
            return;
        }
        bee.getPathfinder().moveTo(home, speed);
    }

    // ----------------------------------------------------------------------------- work

    private void workOn(Player owner, DroneFleet fleet, Bee bee,
                        DroneFleet.Job job, double speed) {
        if (job instanceof DroneFleet.Job.Mine mine) {
            flyToAnd(bee, mine.block(), speed, () -> {
                Block block = mine.block().getBlock();
                if (canBreak(block)) {
                    block.breakNaturally();
                    bee.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                            mine.block(), 12, 0.3, 0.3, 0.3, 0.05);
                    bee.getWorld().playSound(mine.block(), Sound.BLOCK_STONE_BREAK, 0.8f, 1.6f);
                }
                fleet.finish(bee);
            });
        } else if (job instanceof DroneFleet.Job.Place place) {
            flyToAnd(bee, place.against(), speed, () -> {
                placeHeld(bee, place);
                fleet.finish(bee);
            });
        } else if (job instanceof DroneFleet.Job.Carry carry) {
            haul(owner, fleet, bee, carry, speed);
        }
    }

    private void flyToAnd(Bee bee, Location target, double speed, Runnable onArrival) {
        if (!bee.getWorld().equals(target.getWorld())) {
            return;
        }
        if (bee.getLocation().distance(target) <= ARRIVED) {
            onArrival.run();
            return;
        }
        bee.getPathfinder().moveTo(target.clone().add(0.5, 1.0, 0.5), speed);
    }

    /**
     * Puts the block a drone is wearing against the face the player picked.
     *
     * <p>Only ever one, and only if the drone is still carrying it. A drone that placed
     * blocks it did not have would be a way to build out of nothing.
     */
    private void placeHeld(Bee bee, DroneFleet.Job.Place place) {
        var equipment = bee.getEquipment();
        if (equipment == null) {
            return;
        }
        ItemStack held = equipment.getHelmet();
        if (held == null || held.getType().isAir() || !held.getType().isBlock()) {
            return;
        }
        Block target = place.against().getBlock().getRelative(place.face());
        if (!target.getType().isAir() && !target.isLiquid()) {
            return;
        }
        target.setType(held.getType());
        held.subtract();
        equipment.setHelmet(held.getAmount() <= 0 ? null : held);

        bee.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                target.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.04);
        bee.getWorld().playSound(target.getLocation(),
                target.getBlockData().getSoundGroup().getPlaceSound(), 0.8f, 1.2f);
    }

    /**
     * Drags something back to the player.
     *
     * <p>Velocity rather than making it a passenger. Riding a bee is a vanilla behaviour with
     * its own rules about what can sit on what, and half the things worth fetching - an item,
     * a boat, another player - cannot. Pulling works on all of them and looks like a swarm
     * carrying something, which is what was wanted.
     */
    private void haul(Player owner, DroneFleet fleet, Bee bee,
                      DroneFleet.Job.Carry carry, double speed) {
        Entity cargo = plugin.getServer().getEntity(carry.target());
        if (cargo == null || !cargo.isValid()
                || !cargo.getWorld().equals(owner.getWorld())) {
            fleet.finish(bee);
            return;
        }
        double distance = cargo.getLocation().distance(owner.getLocation());
        if (distance < tuning.get("drones.carry-delivered", 3.0)) {
            fleet.finish(bee);
            cargo.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    cargo.getLocation(), 12, 0.3, 0.3, 0.3, 0.05);
            return;
        }

        bee.getPathfinder().moveTo(cargo.getLocation().add(0, 1.2, 0), speed);
        Vector pull = owner.getLocation().toVector()
                .subtract(cargo.getLocation().toVector()).normalize()
                .multiply(tuning.get("drones.carry-pull", 0.28))
                .setY(tuning.get("drones.carry-lift", 0.12));
        cargo.setVelocity(cargo.getVelocity().multiply(0.6).add(pull));
        cargo.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                cargo.getLocation().add(0, 1.0, 0), 2, 0.2, 0.2, 0.2, 0.0);
    }

    /** Blocks a drone is allowed to take down. */
    private boolean canBreak(Block block) {
        Material type = block.getType();
        return !type.isAir()
                && type != Material.BEDROCK
                && type != Material.END_PORTAL_FRAME
                && type != Material.BARRIER
                && block.getType().getHardness() >= 0;
    }

    // ----------------------------------------------------------------------- targeting

    /** Picks a fight, but only if the owner said drones may. */
    private void seekTrouble(Player owner, DroneFleet fleet, Bee bee) {
        if (!fleet.aggression().initiates()) {
            return;
        }
        double range = tuning.get("drones.hunt-range", 12.0);
        for (Entity nearby : bee.getNearbyEntities(range, range, range)) {
            if (!(nearby instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            if (nearby instanceof Player other) {
                if (!fleet.aggression().allowsPlayers() || other.equals(owner)) {
                    continue;
                }
            } else if (!(nearby instanceof Monster)) {
                continue;
            }
            if (fleet.owns(nearby)) {
                continue;
            }
            bee.setTarget(living);
            return;
        }
    }

    /**
     * Answers anything that hits the owner, and refuses to let drones hit their own.
     *
     * <p>Two jobs in one handler because they are the same question asked from both ends:
     * who a drone may fight. The friendly-fire half is not a nicety - a swarm that could sting
     * its owner would be a trap rather than an ability.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            defend(victim, event.getDamager());
        }
    }

    private void defend(Player victim, Entity attacker) {
        DroneFleet fleet = fleets.get(victim.getUniqueId());
        if (fleet == null || fleet.isEmpty() || !fleet.aggression().retaliates()) {
            return;
        }
        if (!(attacker instanceof LivingEntity living) || attacker.equals(victim)) {
            return;
        }
        if (attacker instanceof Player && !fleet.aggression().allowsPlayers()) {
            return;
        }
        if (fleet.owns(attacker)) {
            return;
        }
        for (Bee bee : fleet.drones()) {
            if (!bee.equals(fleet.controlled())) {
                bee.setTarget(living);
            }
        }
    }

    /** A drone never picks its own owner, whatever else it has been told. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        UUID owner = Drone.ownerOf(plugin, event.getEntity());
        if (owner == null) {
            return;
        }
        Entity target = event.getTarget();
        if (target == null) {
            return;
        }
        if (owner.equals(target.getUniqueId()) || Drone.isDrone(plugin, target)) {
            event.setCancelled(true);
            return;
        }
        DroneFleet fleet = fleets.get(owner);
        if (fleet != null && target instanceof Player && !fleet.aggression().allowsPlayers()) {
            event.setCancelled(true);
        }
    }

    /** Drones never sting the person they belong to. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDroneAttack(EntityDamageByEntityEvent event) {
        UUID owner = Drone.ownerOf(plugin, event.getDamager());
        if (owner == null) {
            return;
        }
        if (owner.equals(event.getEntity().getUniqueId())
                || Drone.isDrone(plugin, event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------------ handling

    /**
     * Right-clicking a drone with something puts it on its head.
     *
     * <p>Which is both how you load one with blocks to place and how you get them back:
     * clicking again with an empty hand takes whatever it was wearing.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHandOver(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!Drone.belongsTo(plugin, event.getRightClicked(), player)) {
            return;
        }
        event.setCancelled(true);

        Bee bee = (Bee) event.getRightClicked();
        var equipment = bee.getEquipment();
        if (equipment == null) {
            return;
        }
        ItemStack holding = player.getInventory().getItemInMainHand();
        ItemStack worn = equipment.getHelmet();

        if (holding.getType().isAir()) {
            if (worn == null || worn.getType().isAir()) {
                messages.send(player, "ability.drones.empty-handed");
                return;
            }
            equipment.setHelmet(null);
            player.getInventory().addItem(worn).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
            messages.send(player, "ability.drones.took-back");
            return;
        }

        if (worn != null && !worn.getType().isAir()) {
            player.getInventory().addItem(worn).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        }
        equipment.setHelmet(holding.clone());
        player.getInventory().setItemInMainHand(null);
        bee.getWorld().playSound(bee.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.6f);
        messages.send(player, "ability.drones.now-holding");
    }

    /** A drone that dies is forgotten rather than left in the list as a corpse. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDroneDeath(EntityDeathEvent event) {
        UUID owner = Drone.ownerOf(plugin, event.getEntity());
        if (owner == null) {
            return;
        }
        event.getDrops().clear();
        DroneFleet fleet = fleets.get(owner);
        if (fleet != null) {
            fleet.forget((Bee) event.getEntity());
        }
        Player player = plugin.getServer().getPlayer(owner);
        if (player != null) {
            messages.send(player, "ability.drones.lost");
        }
    }

    /** Logging out sends them away. Bees with no owner online are nobody's problem to have. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        control.release(event.getPlayer(), fleetOf(event.getPlayer()));
        dismissAll(event.getPlayer().getUniqueId());
        fleets.remove(event.getPlayer().getUniqueId());
    }

    // -------------------------------------------------------------------------- lookup

    /** Every drone belonging to a player, for the ability to hand orders to. */
    public List<Bee> dronesOf(Player player) {
        DroneFleet fleet = fleets.get(player.getUniqueId());
        return fleet == null ? new ArrayList<>() : fleet.drones();
    }

    public void setAggression(Player player, Aggression mode) {
        fleetOf(player).setAggression(mode);
        if (!mode.retaliates()) {
            fleetOf(player).drones().forEach(bee -> bee.setTarget(null));
        }
    }

    /**
     * How many drones this player has earned, which is simply their ability level.
     *
     * <p>One per level and no separate number to tune. It means a player can count their own
     * progress by looking up, and it means the level display and the swarm can never disagree
     * about how far along somebody is.
     */
    public int levelOf(Player player) {
        return profiles.resident(player.getUniqueId())
                .map(profile -> Math.max(1, profile.abilityTier()))
                .orElse(1);
    }

    Debug debug() {
        return debug;
    }
}
