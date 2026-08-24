package com.liminalis.plugin.modifier;

import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.core.profile.ProfileModifierIds;
import com.liminalis.plugin.modifier.capability.AttributeContribution;
import com.liminalis.plugin.modifier.capability.AttributeSource;
import com.liminalis.plugin.modifier.capability.DynamicAttributeSource;
import com.liminalis.plugin.modifier.capability.Ticking;
import com.liminalis.plugin.profile.ProfileManager;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Attaches the modifiers a player's profile calls for, and drives all of them from one place.
 *
 * <p>This class owns the only listener and the only repeating task in the modifier system.
 * Individual modifiers declare what they want through capability interfaces and are dispatched
 * to from here. The alternative - each trait registering its own listener and its own timer -
 * scales badly and makes ordering between two traits a matter of registration luck.
 *
 * <p>Attributes are recomputed wholesale rather than adjusted. Every apply clears everything
 * in this plugin's namespace first, so a modifier can never leave a stale bonus behind.
 */
public final class ModifierService implements Listener {

    /** How often ticking modifiers run and dynamic attributes are recomputed. */
    private static final long TICK_INTERVAL = 10L;

    private final JavaPlugin plugin;
    private final ModifierRegistry registry;
    private final ProfileManager profiles;
    private final String namespace;

    private final Map<UUID, List<Modifier>> attached = new ConcurrentHashMap<>();

    /** Ids referenced by a profile that this build has no code for - warned about once each. */
    private final Set<String> warnedUnknownIds = ConcurrentHashMap.newKeySet();

    private BukkitTask tickTask;

    public ModifierService(JavaPlugin plugin, ModifierRegistry registry, ProfileManager profiles) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.namespace = plugin.getName().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------------- lifecycle

    public void start() {
        tickTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tickEveryone, TICK_INTERVAL, TICK_INTERVAL);
    }

    /**
     * Detaches everything from everyone.
     *
     * <p>Not optional on shutdown: attribute modifiers are written into player.dat, so a
     * plugin that disables without cleaning up leaves its bonuses on players permanently -
     * and adds a fresh set on top next time it starts.
     */
    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            detachAll(player);
        }
        attached.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        applyFromProfile(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        detachAll(event.getPlayer());
    }

    // ---------------------------------------------------------------------------- apply

    /**
     * Rebuilds a player's modifiers from their profile.
     *
     * <p>Safe to call at any time - after a roll, after an admin grant, after a reload.
     */
    public void applyFromProfile(Player player) {
        PlayerProfile profile = profiles.of(player);

        detachAll(player);

        List<Modifier> resolved = new ArrayList<>();
        for (String id : ProfileModifierIds.referencedBy(profile)) {
            Modifier modifier = registry.find(id).orElse(null);
            if (modifier == null) {
                warnUnknown(id, profile);
                continue;
            }
            resolved.add(modifier);
        }

        attached.put(player.getUniqueId(), List.copyOf(resolved));
        for (Modifier modifier : resolved) {
            try {
                modifier.onAttach(player);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Modifier '" + modifier.id() + "' failed to attach to "
                                + player.getName(), e);
            }
        }
        refreshAttributes(player);
    }

    /** Recomputes every attribute contribution for a player from scratch. */
    public void refreshAttributes(Player player) {
        clearOurAttributeModifiers(player);

        List<Modifier> current = attached.getOrDefault(player.getUniqueId(), List.of());
        for (Modifier modifier : current) {
            if (!(modifier instanceof AttributeSource source)) {
                continue;
            }
            List<AttributeContribution> contributions;
            try {
                contributions = source.attributeContributions(player);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Modifier '" + modifier.id() + "' failed to compute attributes for "
                                + player.getName(), e);
                continue;
            }
            applyContributions(player, modifier, contributions);
        }
    }

    private void applyContributions(Player player, Modifier modifier,
                                    List<AttributeContribution> contributions) {
        for (int i = 0; i < contributions.size(); i++) {
            AttributeContribution contribution = contributions.get(i);
            AttributeInstance instance = player.getAttribute(contribution.attribute());
            if (instance == null) {
                // Players do not carry every attribute in the registry; skipping is correct.
                continue;
            }
            instance.addModifier(new AttributeModifier(
                    keyFor(modifier, contribution.attribute(), i),
                    contribution.amount(),
                    contribution.operation()));
        }
    }

    private void detachAll(Player player) {
        List<Modifier> current = attached.remove(player.getUniqueId());
        clearOurAttributeModifiers(player);
        if (current == null) {
            return;
        }
        for (Modifier modifier : current) {
            try {
                modifier.onDetach(player);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Modifier '" + modifier.id() + "' failed to detach from "
                                + player.getName(), e);
            }
        }
    }

    /**
     * Strips every attribute modifier this plugin owns.
     *
     * <p>Sweeps the whole attribute registry rather than only the attributes touched this
     * session, so a modifier that existed in a previous build - or one left behind by a
     * crash before the quit handler ran - is still cleaned up.
     */
    private void clearOurAttributeModifiers(Player player) {
        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                if (namespace.equals(modifier.getKey().getNamespace())) {
                    instance.removeModifier(modifier);
                }
            }
        }
    }

    // ----------------------------------------------------------------------------- tick

    private void tickEveryone() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            List<Modifier> current = attached.get(player.getUniqueId());
            if (current == null || current.isEmpty()) {
                continue;
            }
            boolean needsAttributeRefresh = false;
            for (Modifier modifier : current) {
                if (modifier instanceof Ticking ticking) {
                    try {
                        ticking.tick(player);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.SEVERE,
                                "Modifier '" + modifier.id() + "' threw while ticking "
                                        + player.getName(), e);
                    }
                }
                if (modifier instanceof DynamicAttributeSource) {
                    needsAttributeRefresh = true;
                }
            }
            if (needsAttributeRefresh) {
                refreshAttributes(player);
            }
        }
    }

    // ---------------------------------------------------------------------------- misc

    public List<Modifier> attachedTo(Player player) {
        return attached.getOrDefault(player.getUniqueId(), List.of());
    }

    private NamespacedKey keyFor(Modifier modifier, Attribute attribute, int index) {
        return new NamespacedKey(plugin,
                "mod." + modifier.id() + "." + attribute.getKey().getKey() + "." + index);
    }

    /**
     * An id in a profile that this build cannot resolve.
     *
     * <p>Deliberately left in the profile rather than stripped out. A trait might be absent
     * because a build is mid-rollout or was rolled back, and quietly deleting it would turn a
     * temporary mismatch into permanent data loss.
     */
    private void warnUnknown(String id, PlayerProfile profile) {
        if (warnedUnknownIds.add(id)) {
            plugin.getLogger().warning("Profile for " + profile.lastKnownName()
                    + " references unknown modifier '" + id + "'."
                    + " Leaving it in place in case it returns in a later build.");
        }
    }
}
