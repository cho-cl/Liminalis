package com.liminalis.plugin.limbo;

import com.liminalis.core.limbo.GhostVisitRules;
import com.liminalis.core.limbo.GhostVisitSettings;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets the dead walk among the living for five minutes at a time.
 *
 * <p>They go as spectators, so they can see everything and touch nothing - which is close to
 * the point. The cooldown is written to the profile the moment a visit begins rather than when
 * it ends, so quitting mid-visit does not refund it; otherwise logging out and back in would
 * be a way to haunt the world without pause.
 */
public final class GhostVisitService implements Listener {

    private final JavaPlugin plugin;
    private final ConfigService config;
    private final ProfileManager profiles;
    private final LimboService limbo;
    private final Messages messages;
    private final Debug debug;

    /** The pending "your time is up" task for each player currently out visiting. */
    private final Map<UUID, BukkitTask> returns = new ConcurrentHashMap<>();

    public GhostVisitService(JavaPlugin plugin,
                             ConfigService config,
                             ProfileManager profiles,
                             LimboService limbo,
                             Messages messages,
                             Debug debug) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.limbo = Objects.requireNonNull(limbo, "limbo");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.debug = Objects.requireNonNull(debug, "debug");
    }

    /**
     * Sends a Limbo player out among the living, if they are allowed to go.
     *
     * @return true if the visit began; false if they were refused, having been told why
     */
    public boolean begin(Player player) {
        PlayerProfile profile = profiles.of(player);
        if (!profile.inLimbo()) {
            messages.send(player, "ghost.only-the-dead");
            return false;
        }
        if (returns.containsKey(player.getUniqueId())) {
            messages.send(player, "ghost.already-visiting");
            return false;
        }

        long now = System.currentTimeMillis();
        if (!GhostVisitRules.canVisit(profile, now)) {
            messages.send(player, "ghost.cooldown", Messages.placeholder("time",
                    describe(GhostVisitRules.cooldownRemainingMillis(profile, now))));
            return false;
        }

        GhostVisitSettings settings = config.get().limbo().ghostVisit();
        GhostVisitRules.beginVisit(profile, settings, now);
        // Written immediately: the cooldown must survive a crash or a logout, or the visit
        // was effectively free.
        profiles.saveNow(profile);

        Location destination = livingDestination(player);
        if (destination == null) {
            messages.send(player, "ghost.nowhere-to-go");
            return false;
        }

        limbo.markGhosting(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(destination);
        messages.send(player, "ghost.begin",
                Messages.placeholder("time", describe(settings.visitMillis())));

        long ticks = Math.max(1L, settings.visitMillis() / 50L);
        returns.put(player.getUniqueId(), plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> recall(player, true), ticks));

        debug.log(() -> player.getName() + " began a ghost visit for " + ticks + " ticks");
        return true;
    }

    /** Ends a visit early, at the player's request. */
    public void end(Player player) {
        if (!returns.containsKey(player.getUniqueId())) {
            messages.send(player, "ghost.not-visiting");
            return;
        }
        recall(player, false);
    }

    private void recall(Player player, boolean timeExpired) {
        UUID id = player.getUniqueId();
        BukkitTask task = returns.remove(id);
        if (task != null) {
            task.cancel();
        }
        limbo.clearGhosting(id);

        if (!player.isOnline()) {
            return;
        }
        limbo.sendToLimbo(player);
        messages.send(player, timeExpired ? "ghost.expired" : "ghost.ended");
        debug.log(() -> player.getName() + " returned to Limbo"
                + (timeExpired ? " (time up)" : " (early)"));
    }

    /**
     * Cleans up a visit when the visitor disconnects.
     *
     * <p>No refund: the cooldown was armed when the visit started. On rejoin
     * {@code LimboService} puts them back in the fog.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        BukkitTask task = returns.remove(id);
        if (task != null) {
            task.cancel();
        }
        limbo.clearGhosting(id);
    }

    /** Ends every visit in progress. Called on disable so nobody is left stranded. */
    public void shutdown() {
        for (UUID id : Set.copyOf(returns.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            BukkitTask task = returns.remove(id);
            if (task != null) {
                task.cancel();
            }
            limbo.clearGhosting(id);
            if (player != null && player.isOnline()) {
                limbo.sendToLimbo(player);
            }
        }
    }

    /** Where a ghost appears: at their bed if they had one, otherwise at world spawn. */
    private Location livingDestination(Player player) {
        Location bed = player.getRespawnLocation();
        if (bed != null && !limbo.world().is(bed.getWorld())) {
            return bed;
        }
        World overworld = plugin.getServer().getWorlds().stream()
                .filter(world -> !limbo.world().is(world))
                .findFirst()
                .orElse(null);
        return overworld == null ? null : overworld.getSpawnLocation();
    }

    private static String describe(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).toSeconds();
        if (minutes <= 0) {
            return seconds + "s";
        }
        return seconds == 0 ? minutes + "m" : minutes + "m " + seconds + "s";
    }
}
