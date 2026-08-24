package com.liminalis.plugin.rescue;

import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.core.rescue.CrossingOutcome;
import com.liminalis.core.rescue.RescueRules;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.limbo.LimboService;
import com.liminalis.plugin.limbo.LimboWorld;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The expedition: a living player crosses into the grey, finds someone, and carries them out.
 *
 * <p>Nothing done from the living world reaches into Limbo. Someone has to go. That single
 * commitment is what makes rescue an expedition rather than a rite, and it is why the fifth
 * book insists on it at length.
 *
 * <p>The way out is the way in. A crossing opens at a fixed point and that same point is the
 * only exit, so a rescuer wandering fog with no landmarks has to keep track of where the door
 * was - which is the entire difficulty of the trip, and the reason the grey needed no monsters
 * in it to be frightening.
 */
public final class RescueService implements Listener {

    /** How often crossings are checked, and how often a rescuer is told the time. */
    private static final long SWEEP_TICKS = 20L;

    /** How close to the door you have to be to step back through it. */
    private static final double EXIT_RADIUS = 4.0;

    private final JavaPlugin plugin;
    private final ConfigService config;
    private final ProfileManager profiles;
    private final LimboService limbo;
    private final LimboWorld limboWorld;
    private final Messages messages;
    private final Debug debug;

    private final Map<UUID, Crossing> crossings = new ConcurrentHashMap<>();

    private BukkitTask sweepTask;

    public RescueService(JavaPlugin plugin,
                         ConfigService config,
                         ProfileManager profiles,
                         LimboService limbo,
                         LimboWorld limboWorld,
                         Messages messages,
                         Debug debug) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.limbo = Objects.requireNonNull(limbo, "limbo");
        this.limboWorld = Objects.requireNonNull(limboWorld, "limboWorld");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.debug = Objects.requireNonNull(debug, "debug");
    }

    public void start() {
        sweepTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::sweep, SWEEP_TICKS, SWEEP_TICKS);
    }

    /** Pulls everyone back on shutdown, free of charge. Nobody is stranded by a restart. */
    public void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        for (UUID id : Set.copyOf(crossings.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && player.isOnline()) {
                complete(player, CrossingOutcome.RETURNED);
            } else {
                crossings.remove(id);
            }
        }
    }

    public boolean isCrossed(UUID id) {
        return crossings.containsKey(id);
    }

    // -------------------------------------------------------------------------- crossing

    /** Uses a Threshold Stone. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUseStone(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                    && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        if (!ThresholdStone.is(plugin, event.getItem())) {
            return;
        }
        event.setCancelled(true);
        cross(player);
    }

    private void cross(Player player) {
        PlayerProfile profile = profiles.of(player);

        if (!RescueRules.mayCross(profile)) {
            messages.send(player, "rescue.already-lost");
            return;
        }
        if (crossings.containsKey(player.getUniqueId())) {
            messages.send(player, "rescue.already-crossed");
            return;
        }
        if (!limboWorld.isReady()) {
            messages.send(player, "rescue.no-way-through");
            return;
        }

        Location door = limboWorld.arrivalPoint();
        Location home = player.getLocation().clone();

        player.getInventory().getItemInMainHand().subtract();
        crossings.put(player.getUniqueId(), new Crossing(home, door,
                System.currentTimeMillis() + crossingMillis()));

        player.teleport(door);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.playSound(door, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
        messages.send(player, "rescue.crossed");

        // Warned explicitly rather than left to be discovered. The cost of staying too long
        // is severe enough that finding out by experiencing it would be unfair.
        messages.send(player, "rescue.warning");
        debug.log(() -> player.getName() + " crossed into Limbo");
    }

    private long crossingMillis() {
        return config.get().rescue().crossingSeconds() * 1000L;
    }

    // ---------------------------------------------------------------------------- anchor

    /**
     * Takes hold of someone in the grey.
     *
     * <p>Once taken, they are coming out with you. Requiring them to stay close would turn a
     * rescue into an escort mission through featureless fog, which is tedious rather than
     * tense.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTakeHand(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player rescuer = event.getPlayer();
        Crossing crossing = crossings.get(rescuer.getUniqueId());
        if (crossing == null || !(event.getRightClicked() instanceof Player found)) {
            return;
        }

        PlayerProfile theirs = profiles.resident(found.getUniqueId()).orElse(null);
        if (theirs == null || !theirs.inLimbo()) {
            return;
        }
        event.setCancelled(true);

        if (!crossing.carried.add(found.getUniqueId())) {
            messages.send(rescuer, "rescue.already-holding",
                    Messages.placeholder("player", found.getName()));
            return;
        }
        messages.send(rescuer, "rescue.took-hold",
                Messages.placeholder("player", found.getName()));
        messages.send(found, "rescue.taken-hold",
                Messages.placeholder("player", rescuer.getName()));
        found.playSound(found.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
        debug.log(() -> rescuer.getName() + " took hold of " + found.getName());
    }

    // ----------------------------------------------------------------------------- sweep

    private void sweep() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Crossing> entry : Map.copyOf(crossings).entrySet()) {
            Player rescuer = plugin.getServer().getPlayer(entry.getKey());
            Crossing crossing = entry.getValue();

            if (rescuer == null || !rescuer.isOnline()) {
                continue;
            }
            markDoor(crossing.door);

            if (now >= crossing.deadline) {
                complete(rescuer, RescueRules.expire(profiles.of(rescuer), now));
                continue;
            }
            if (rescuer.getWorld().equals(crossing.door.getWorld())
                    && rescuer.getLocation().distanceSquared(crossing.door)
                        <= EXIT_RADIUS * EXIT_RADIUS) {
                complete(rescuer, RescueRules.returnFrom(profiles.of(rescuer)));
                continue;
            }
            tellTime(rescuer, crossing, now);
        }
    }

    /** A column of light at the door, so it can be found again across featureless ground. */
    private void markDoor(Location door) {
        World world = door.getWorld();
        for (int y = 0; y < 24; y += 2) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                    door.clone().add(0, y, 0), 2, 0.15, 0.2, 0.15, 0.0);
        }
    }

    private void tellTime(Player rescuer, Crossing crossing, long now) {
        long secondsLeft = Math.max(0, (crossing.deadline - now) / 1000L);
        double distance = rescuer.getWorld().equals(crossing.door.getWorld())
                ? rescuer.getLocation().distance(crossing.door) : -1;

        NamedTextColor colour = secondsLeft <= 30 ? NamedTextColor.RED
                : secondsLeft <= 60 ? NamedTextColor.GOLD : NamedTextColor.GRAY;

        String door = distance < 0 ? "?" : Math.round(distance) + "m";
        rescuer.sendActionBar(Component.text(
                secondsLeft + "s   door " + door
                        + (crossing.carried.isEmpty() ? ""
                            : "   holding " + crossing.carried.size()), colour));
    }

    // -------------------------------------------------------------------------- finishing

    private void complete(Player rescuer, CrossingOutcome outcome) {
        Crossing crossing = crossings.remove(rescuer.getUniqueId());
        if (crossing == null) {
            return;
        }
        PlayerProfile profile = profiles.of(rescuer);

        if (outcome == CrossingOutcome.STRANDED) {
            // They are not coming out. Everyone they were holding stays too - being carried
            // by someone who never made it is not a rescue.
            profiles.saveNow(profile);
            limbo.sendToLimbo(rescuer);
            messages.send(rescuer, "rescue.stranded");
            plugin.getServer().sendMessage(messages.get("rescue.stranded-broadcast",
                    Messages.placeholder("player", rescuer.getName())));
            debug.log(() -> rescuer.getName() + " was stranded in Limbo");
            return;
        }

        retrieveCarried(rescuer, crossing);

        rescuer.teleport(crossing.home);
        rescuer.playSound(crossing.home, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.7f);
        profiles.saveNow(profile);

        messages.send(rescuer, outcome == CrossingOutcome.RETURNED_DIMINISHED
                ? "rescue.returned-diminished" : "rescue.returned");
        debug.log(() -> rescuer.getName() + " crossing ended: " + outcome);
    }

    private void retrieveCarried(Player rescuer, Crossing crossing) {
        int lives = config.get().limbo().revivalLives();

        for (UUID id : crossing.carried) {
            PlayerProfile theirs = profiles.lookup(id).orElse(null);
            if (theirs == null || !RescueRules.retrieve(theirs, lives, MARK_OF_RETURN)) {
                continue;
            }
            profiles.saveNow(theirs);

            Player carried = plugin.getServer().getPlayer(id);
            if (carried != null && carried.isOnline()) {
                limbo.returnToLiving(carried);
                messages.send(carried, "rescue.brought-back",
                        Messages.placeholder("player", rescuer.getName()));
            }
            plugin.getServer().sendMessage(messages.get("rescue.brought-back-broadcast",
                    Messages.placeholder("player", theirs.lastKnownName()),
                    Messages.placeholder("rescuer", rescuer.getName())));
            debug.log(() -> theirs.lastKnownName() + " was carried out by " + rescuer.getName());
        }
    }

    /**
     * Someone logging out mid-crossing is pulled back for free.
     *
     * <p>Deliberately generous. Charging a life for a dropped connection would make the most
     * frightening thing in the server also the most unfair, and the risk is meant to come
     * from the grey rather than from the network.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Crossing crossing = crossings.remove(event.getPlayer().getUniqueId());
        if (crossing == null) {
            return;
        }
        // Their profile still says they are alive, so the login handler will not send them
        // to Limbo. Move the body back so they do not wake up in the fog.
        event.getPlayer().teleport(crossing.home);
        debug.log(() -> event.getPlayer().getName() + " quit mid-crossing; returned home");
    }

    private static final String MARK_OF_RETURN = "mark_of_return";

    /** One living player's trip into the grey. */
    private static final class Crossing {

        private final Location home;
        private final Location door;
        private final long deadline;
        private final Set<UUID> carried = new LinkedHashSet<>();

        private Crossing(Location home, Location door, long deadline) {
            this.home = home;
            this.door = door;
            this.deadline = deadline;
        }
    }
}
