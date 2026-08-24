package com.liminalis.plugin.profile;

import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.core.profile.ProfileStore;
import com.liminalis.plugin.config.ConfigService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Owns the in-memory copy of every online player's profile, and is the only place that
 * decides when one is read or written.
 *
 * <p>Loading happens on the async pre-login thread, so a disk read never stalls the server.
 * If a profile cannot be read, the login is <em>refused</em>. That is deliberate and it is
 * the most important decision in this class: letting someone in with a blank profile would
 * silently hand them a fresh three lives and erase their ability, and by the time anyone
 * noticed, the blank would have been saved over the original.
 */
public final class ProfileManager implements Listener {

    private final JavaPlugin plugin;
    private final ProfileStore store;
    private final ConfigService config;

    /** Profiles for players who are connected, or in the middle of connecting. */
    private final Map<UUID, PlayerProfile> resident = new ConcurrentHashMap<>();

    /**
     * Lowercased last-known name to id, for every profile on disk.
     *
     * <p>This is what lets admin commands target offline players. Resolving names through
     * Bukkit instead would mean a blocking Mojang lookup on the main thread for anyone not
     * currently online - on a command that gets tab-completed keystroke by keystroke.
     */
    private final Map<String, UUID> idsByName = new ConcurrentHashMap<>();

    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Liminalis-profile-writer");
        thread.setDaemon(true);
        return thread;
    });

    public ProfileManager(JavaPlugin plugin, ProfileStore store, ConfigService config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.store = Objects.requireNonNull(store, "store");
        this.config = Objects.requireNonNull(config, "config");
    }

    // ---------------------------------------------------------------- lifecycle events

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID id = event.getUniqueId();
        String name = event.getName();
        try {
            PlayerProfile profile = store.load(id)
                    .orElseGet(() -> createFor(id, name));
            profile.setLastKnownName(name);
            resident.put(id, profile);
            index(profile);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Refusing login for " + name + " (" + id + "): profile could not be read", e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(
                    "Your profile could not be read, so you have not been let in.\n"
                            + "This is protecting your lives and your ability - nothing has been lost.\n"
                            + "Please contact the Creator.", NamedTextColor.RED));
        }
    }

    /**
     * Drops the pre-loaded profile if something else refused the login after we loaded it.
     * Without this, a denied join would leave a resident profile that never gets evicted.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            resident.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = resident.get(player.getUniqueId());
        if (profile == null) {
            // Should be unreachable: pre-login either loads a profile or refuses entry.
            plugin.getLogger().severe("No profile resident for " + player.getName()
                    + " on join; loading synchronously as a fallback.");
            profile = store.load(player.getUniqueId())
                    .orElseGet(() -> createFor(player.getUniqueId(), player.getName()));
            resident.put(player.getUniqueId(), profile);
        }
        profile.setLastKnownName(player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        PlayerProfile profile = resident.remove(id);
        if (profile == null) {
            return;
        }
        profile.setLastSeenAt(System.currentTimeMillis());
        saveAsync(profile);
    }

    // -------------------------------------------------------------------- offline index

    /**
     * Builds the name index by reading every stored profile once at startup.
     *
     * <p>A profile that cannot be read is logged and skipped rather than aborting startup:
     * one damaged file must not stop the server, and the player it belongs to will still be
     * refused entry with a clear message if they try to join.
     */
    public void indexStoredProfiles() {
        idsByName.clear();
        int failed = 0;
        for (UUID id : store.knownIds()) {
            try {
                store.load(id).ifPresent(this::index);
            } catch (RuntimeException e) {
                failed++;
                plugin.getLogger().log(Level.SEVERE,
                        "Could not read stored profile " + id + " while indexing", e);
            }
        }
        plugin.getLogger().info("Indexed " + idsByName.size() + " player profile(s)"
                + (failed > 0 ? ", " + failed + " unreadable" : "") + ".");
    }

    private void index(PlayerProfile profile) {
        idsByName.put(profile.lastKnownName().toLowerCase(Locale.ROOT), profile.id());
    }

    /** Resolves a player name or a raw UUID to an id, without contacting Mojang. */
    public Optional<UUID> resolve(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) {
            return Optional.empty();
        }
        UUID byName = idsByName.get(nameOrId.toLowerCase(Locale.ROOT));
        if (byName != null) {
            return Optional.of(byName);
        }
        try {
            return Optional.of(UUID.fromString(nameOrId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Every name Liminalis knows, online or not, for command tab-completion.
     *
     * <p>Prefers the live profile's name where the player is connected, so someone who has
     * changed their name completes under the new one rather than the indexed one.
     */
    public Collection<String> knownNames() {
        SortedSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, UUID> entry : idsByName.entrySet()) {
            PlayerProfile online = resident.get(entry.getValue());
            names.add(online != null ? online.lastKnownName() : entry.getKey());
        }
        for (PlayerProfile profile : resident.values()) {
            names.add(profile.lastKnownName());
        }
        return names;
    }

    // ------------------------------------------------------------------------ accessors

    /** The profile of an online player. Never null while they are connected. */
    public PlayerProfile of(Player player) {
        PlayerProfile profile = resident.get(player.getUniqueId());
        if (profile == null) {
            throw new IllegalStateException(
                    "No resident profile for online player " + player.getName());
        }
        return profile;
    }

    /**
     * The profile of a connected player, or empty if they are not resident.
     *
     * <p>Touches no disk and never throws, so it is safe from async contexts such as chat,
     * where the player may be part-way through disconnecting.
     */
    public Optional<PlayerProfile> resident(UUID id) {
        return Optional.ofNullable(resident.get(id));
    }

    /**
     * The profile of anyone the server knows about, online or not.
     *
     * <p>Returns the live resident object when the player is connected, so an admin edit and
     * gameplay are never working on two different copies of the same person.
     */
    public Optional<PlayerProfile> lookup(UUID id) {
        PlayerProfile cached = resident.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        return store.load(id);
    }

    /** Every player id with a stored profile, including those who have never been online. */
    public Set<UUID> knownIds() {
        return store.knownIds();
    }

    public Collection<PlayerProfile> residentProfiles() {
        return resident.values();
    }

    // -------------------------------------------------------------------------- writing

    /**
     * Writes immediately on the calling thread.
     *
     * <p>Used for admin edits. These are rare, they touch one small file, and the admin is
     * already waiting on the command's reply - so paying a sub-millisecond write here buys
     * the guarantee that the change is on disk before anything else can read it.
     */
    public void saveNow(PlayerProfile profile) {
        store.save(profile);
        index(profile);
    }

    /** Queues a write on the background writer. Used for routine gameplay saves. */
    public void saveAsync(PlayerProfile profile) {
        writer.execute(() -> {
            try {
                store.save(profile);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to save profile for " + profile.lastKnownName(), e);
            }
        });
    }

    /** Flushes every resident profile and stops the writer. Called on plugin disable. */
    public void shutdown() {
        long now = System.currentTimeMillis();
        for (PlayerProfile profile : resident.values()) {
            profile.setLastSeenAt(now);
            try {
                store.save(profile);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to flush profile for " + profile.lastKnownName(), e);
            }
        }
        resident.clear();

        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Profile writer did not finish within 10s.");
                writer.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    private PlayerProfile createFor(UUID id, String name) {
        PlayerProfile profile = PlayerProfile.createNew(id, name, config.get().lives().startingLives());
        long now = System.currentTimeMillis();
        profile.setFirstJoinedAt(now);
        profile.setLastSeenAt(now);
        return profile;
    }
}
