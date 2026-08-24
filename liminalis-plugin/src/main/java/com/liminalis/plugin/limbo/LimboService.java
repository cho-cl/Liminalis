package com.liminalis.plugin.limbo;

import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the dead in Limbo, and keeps Limbo from hurting them.
 *
 * <p>Two jobs that sound opposed but are the same one. Limbo is described to players as the
 * place between life and death where nothing can kill you and there is no way out, and both
 * halves of that have to be true or the whole thing reads as a bug. So damage is refused,
 * hunger is refused, and every route out of the world is refused - portals, teleports, other
 * plugins' warps, and simply logging out and back in somewhere else.
 *
 * <p>The one sanctioned way out is a ghost visit, and even that is a spectator with a timer.
 */
public final class LimboService implements Listener {

    private final JavaPlugin plugin;
    private final ProfileManager profiles;
    private final LimboWorld limboWorld;
    private final ModifierService modifiers;
    private final Messages messages;
    private final Debug debug;

    /**
     * Teleports this plugin is performing itself, which must not be blocked by our own
     * containment. Without this, sending someone to Limbo would be refused by the rule that
     * stops them leaving it.
     */
    private final Set<UUID> sanctionedTeleports = ConcurrentHashMap.newKeySet();

    /** Dead players currently out among the living on a ghost visit. */
    private final Set<UUID> ghosting = ConcurrentHashMap.newKeySet();

    public LimboService(JavaPlugin plugin,
                        ProfileManager profiles,
                        LimboWorld limboWorld,
                        ModifierService modifiers,
                        Messages messages,
                        Debug debug) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.limboWorld = Objects.requireNonNull(limboWorld, "limboWorld");
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.debug = Objects.requireNonNull(debug, "debug");
    }

    // ------------------------------------------------------------------- state changes

    /**
     * Sends a player to Limbo.
     *
     * <p>Assumes the profile already says they belong there - this moves the body, it does
     * not decide the fate. {@code LifeRules} decides.
     */
    public boolean sendToLimbo(Player player) {
        Location arrival = limboWorld.arrivalPoint();
        if (arrival == null) {
            plugin.getLogger().severe("Cannot send " + player.getName()
                    + " to Limbo: the world is not open.");
            return false;
        }

        ghosting.remove(player.getUniqueId());
        teleportSanctioned(player, arrival);

        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0f);
        player.setFireTicks(0);
        restoreComfort(player);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        debug.log(() -> player.getName() + " sent to Limbo");
        return true;
    }

    /** Brings a player back out of Limbo into the living world. */
    public boolean returnToLiving(Player player) {
        World overworld = plugin.getServer().getWorlds().stream()
                .filter(world -> !limboWorld.is(world))
                .findFirst()
                .orElse(null);
        if (overworld == null) {
            plugin.getLogger().severe("Cannot return " + player.getName()
                    + " to the living: no other world exists.");
            return false;
        }

        ghosting.remove(player.getUniqueId());
        Location destination = player.getRespawnLocation() != null
                ? player.getRespawnLocation()
                : overworld.getSpawnLocation();

        teleportSanctioned(player, destination);
        player.setGameMode(GameMode.SURVIVAL);
        restoreComfort(player);
        player.setHealth(Math.min(player.getHealth() + 6.0,
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));

        debug.log(() -> player.getName() + " returned to the living");
        return true;
    }

    /** Full food and saturation, which is the state Limbo permanently holds people in. */
    public void restoreComfort(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
    }

    /**
     * Performs a teleport that our own containment rules must not block.
     *
     * <p>The marker is cleared in a finally block rather than by the teleport listener, so a
     * cancelled or failed teleport cannot leave a player permanently exempt from containment.
     */
    private void teleportSanctioned(Player player, Location destination) {
        UUID id = player.getUniqueId();
        sanctionedTeleports.add(id);
        try {
            player.teleport(destination);
        } finally {
            sanctionedTeleports.remove(id);
        }
    }

    // ------------------------------------------------------------------- ghost bookkeeping

    void markGhosting(UUID id) {
        ghosting.add(id);
    }

    void clearGhosting(UUID id) {
        ghosting.remove(id);
    }

    public boolean isGhosting(UUID id) {
        return ghosting.contains(id);
    }

    public LimboWorld world() {
        return limboWorld;
    }

    // ------------------------------------------------------------------------ enforcement

    /**
     * Puts anyone who belongs in Limbo back in it on login.
     *
     * <p>This is what makes Limbo inescapable rather than merely hard to leave. Without it,
     * logging out in Limbo and back in would be enough, and so would any admin who moved a
     * player while they were offline.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = profiles.of(player);

        if (!profile.inLimbo()) {
            // The mirror case, and easy to miss: somebody revived while they were offline is
            // no longer condemned, but their body is still standing in the fog. Without this
            // they would log in to a world with no exits and no rule keeping them there.
            if (limboWorld.is(player.getWorld())) {
                returnToLiving(player);
                messages.send(player, "limbo.revived-while-away");
            }
            return;
        }
        // A visit never survives a logout: they come back to the fog.
        ghosting.remove(player.getUniqueId());
        if (!limboWorld.is(player.getWorld())) {
            sendToLimbo(player);
            messages.send(player, "limbo.returned-on-login");
        } else {
            restoreComfort(player);
        }
    }

    /** Nothing in Limbo can hurt anyone. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && limboWorld.is(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Hunger never falls in Limbo. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !limboWorld.is(player.getWorld())) {
            return;
        }
        event.setCancelled(true);
        restoreComfort(player);
    }

    /**
     * Stops exhaustion accumulating at all.
     *
     * <p>Cancelling the food level change alone is not enough: saturation drains silently
     * first and only fires an event once it has run out. Refusing the exhaustion means
     * saturation never moves in the first place, which is what was asked for.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent event) {
        if (event.getEntity() instanceof Player player && limboWorld.is(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Nothing spawns in Limbo, including the Creaking that pale gardens grow hearts for. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (limboWorld.is(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** There are no doors out. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (limboWorld.is(event.getPlayer().getWorld())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "limbo.contained");
        }
    }

    /**
     * Refuses any teleport that would take a Limbo player out of Limbo.
     *
     * <p>Deliberately broad: it catches other plugins' warps, homes and spawn commands
     * without needing to know about any of them.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (sanctionedTeleports.contains(id) || ghosting.contains(id)) {
            return;
        }
        if (profiles.resident(id).filter(PlayerProfile::inLimbo).isEmpty()) {
            return;
        }
        Location to = event.getTo();
        if (to == null || limboWorld.is(to.getWorld())) {
            return;
        }
        event.setCancelled(true);
        messages.send(player, "limbo.contained");
        debug.log(() -> "blocked " + player.getName() + " teleporting out of Limbo");
    }

    /** If they somehow die, they respawn in Limbo rather than in the world of the living. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (profiles.resident(player.getUniqueId())
                .filter(PlayerProfile::inLimbo).isEmpty()) {
            refuseBed(event, player);
            return;
        }
        Location arrival = limboWorld.arrivalPoint();
        if (arrival != null) {
            event.setRespawnLocation(arrival);
        }
    }

    /**
     * Sends the Untethered back to world spawn however far away they set a bed.
     *
     * <p>The cost half of that curse, and enforced here because respawn location is decided
     * in exactly one place. Half of them is already somewhere else, so nowhere is home - and
     * on a survival server where everyone builds a base an hour out, losing your bed is a far
     * heavier price than any number would be.
     *
     * <p>The bed still works as a bed: they can sleep in it and skip the night. It simply
     * will not catch them.
     */
    private void refuseBed(PlayerRespawnEvent event, Player player) {
        if (!modifiers.carries(player, UNTETHERED)) {
            return;
        }
        World overworld = plugin.getServer().getWorlds().stream()
                .filter(world -> !limboWorld.is(world))
                .findFirst()
                .orElse(null);
        if (overworld == null) {
            return;
        }
        event.setRespawnLocation(overworld.getSpawnLocation());
        messages.send(player, "curse.untethered.no-bed");
    }

    /** The curse that costs a player their bed. */
    private static final String UNTETHERED = "untethered";
}
