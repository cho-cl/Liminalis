package com.liminalis.plugin.modifier;

import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.core.profile.ProfileModifierIds;
import com.liminalis.plugin.modifier.capability.AttributeContribution;
import com.liminalis.plugin.modifier.capability.AttributeSource;
import com.liminalis.plugin.modifier.capability.DamageDealer;
import com.liminalis.plugin.modifier.capability.DamageTaker;
import com.liminalis.plugin.modifier.capability.DynamicAttributeSource;
import com.liminalis.plugin.modifier.capability.HealingRule;
import com.liminalis.plugin.modifier.capability.Restriction;
import com.liminalis.plugin.modifier.capability.Slayer;
import com.liminalis.plugin.modifier.capability.Ticking;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
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

    /** One heart. No combination of wounds may take a player below this. */
    private static final double MINIMUM_MAX_HEALTH = 2.0;

    private final JavaPlugin plugin;
    private final ModifierRegistry registry;
    private final ProfileManager profiles;
    private final Messages messages;
    private final String namespace;

    private final Map<UUID, List<Modifier>> attached = new ConcurrentHashMap<>();

    /** Ids referenced by a profile that this build has no code for - warned about once each. */
    private final Set<String> warnedUnknownIds = ConcurrentHashMap.newKeySet();

    private BukkitTask tickTask;

    public ModifierService(JavaPlugin plugin, ModifierRegistry registry,
                           ProfileManager profiles, Messages messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.messages = Objects.requireNonNull(messages, "messages");
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
        enforceHealthFloor(player);
    }

    /**
     * Stops the wounds a player is carrying from adding up to death.
     *
     * <p>Max-health penalties are the one contribution that can kill on its own, and they
     * stack: Burns and Charred and a Rotting Wound at once is -16 before anything else, and
     * the roster has more of them than that. Each wound clamping its own penalty is not
     * enough - four penalties that are individually survivable are collectively fatal, and
     * the player dies the instant the last one lands, which looks exactly like the plugin
     * murdering them.
     *
     * <p>So the floor is enforced here, once, on the total. This runs after every
     * contribution has been applied, reads what they actually came to, and gives back
     * whatever it takes to leave one heart standing.
     */
    private void enforceHealthFloor(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double shortfall = MINIMUM_MAX_HEALTH - maxHealth.getValue();
        if (shortfall > 0) {
            maxHealth.addModifier(new AttributeModifier(
                    new NamespacedKey(plugin, "health-floor"),
                    shortfall, AttributeModifier.Operation.ADD_NUMBER));
        }
        // Current health above the new maximum is refused by the server, so bring it down
        // ourselves rather than letting the next setHealth call throw.
        if (player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
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

    /**
     * Refuses armour that an attached {@link Restriction} forbids.
     *
     * <p>Handled here rather than by each curse registering its own listener, for the same
     * reason as everything else in this class: one handler, deterministic order, and no
     * accumulation of listeners as the roster grows.
     *
     * <p>The piece is put back into the inventory rather than dropped or destroyed. A curse
     * should cost a player their Protection IV, not their diamonds.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        ItemStack worn = event.getNewItem();
        if (worn == null || worn.getType().isAir()) {
            return;
        }
        Player player = event.getPlayer();
        for (Modifier modifier : attachedTo(player)) {
            if (!(modifier instanceof Restriction restriction)) {
                continue;
            }
            boolean forbidden;
            try {
                forbidden = restriction.forbidsWearing(player, worn);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Restriction '" + modifier.id() + "' threw for " + player.getName(), e);
                continue;
            }
            if (forbidden) {
                refuse(player, worn, restriction);
                return;
            }
        }
    }

    private void refuse(Player player, ItemStack worn, Restriction restriction) {
        ItemStack removed = worn.clone();
        // Next tick: the equip is still being applied as this event fires, and clearing the
        // slot inside the event would be undone by the change that triggered it.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.getInventory().remove(worn);
            player.getInventory().addItem(removed).values()
                    .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
            messages.send(player, restriction.refusalKey());
        });
        debugRefusal(player, restriction);
    }

    private void debugRefusal(Player player, Restriction restriction) {
        plugin.getLogger().fine(() -> "refused armour for " + player.getName()
                + " due to " + restriction.id());
    }

    // ---------------------------------------------------------------------------- damage

    /**
     * Lets attached modifiers rewrite damage in both directions.
     *
     * <p>Priority is load-bearing in both directions. {@code HIGHEST} rather than
     * {@code HIGH} so it runs strictly after {@code CombatListener}, which halves PvP damage
     * at {@code HIGH} - two handlers at the same priority run in registration order, which is
     * not a thing to build a rule on. And well before {@code MONITOR}, where
     * {@code InjuryService} reads the final figure, or a player made immune to fire would
     * still be given burns by a blow that did nothing to them.
     *
     * <p>{@link DamageDealer} is dispatched from here too. It had existed since the modifier
     * framework was built and nothing had ever called it - so any modifier implementing it
     * would have compiled, attached, reported itself on the profile screen and quietly done
     * nothing at all. An interface that is declared but never dispatched is a trap with a
     * long fuse.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim) {
            takeDamage(victim, event);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player attacker
                && !event.isCancelled()) {
            dealDamage(attacker, byEntity);
        }
    }

    private void takeDamage(Player victim, EntityDamageEvent event) {
        double damage = event.getDamage();
        for (Modifier modifier : attachedTo(victim)) {
            if (!(modifier instanceof DamageTaker taker)) {
                continue;
            }
            try {
                damage = taker.adjustIncoming(victim, event, damage);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Modifier '" + modifier.id()
                        + "' threw adjusting damage to " + victim.getName(), e);
            }
        }
        if (damage <= 0) {
            // Cancelled outright rather than set to zero: a zero-damage blow still knocks
            // you back, still flashes red and still counts as being hit. "Fire cannot hurt
            // you" has to mean the fire does nothing, not that it does nothing visible.
            event.setCancelled(true);
            return;
        }
        if (damage != event.getDamage()) {
            event.setDamage(damage);
        }
    }

    private void dealDamage(Player attacker, EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        double damage = event.getDamage();
        for (Modifier modifier : attachedTo(attacker)) {
            if (!(modifier instanceof DamageDealer dealer)) {
                continue;
            }
            try {
                damage = dealer.adjustOutgoing(attacker, victim, damage);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Modifier '" + modifier.id()
                        + "' threw adjusting damage from " + attacker.getName(), e);
            }
        }
        if (damage != event.getDamage()) {
            event.setDamage(Math.max(0.0, damage));
        }
    }

    /**
     * Lets attached modifiers rewrite how their owner mends.
     *
     * <p>{@code HIGHEST}, so this runs strictly after the world-wide rules from Phase 1 have
     * had their say rather than merely alongside them. A curse that quadruples what food
     * gives back is then quadrupling the halved figure the server actually hands out, which
     * is what a player would expect it to mean.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double amount = event.getAmount();
        for (Modifier modifier : attachedTo(player)) {
            if (!(modifier instanceof HealingRule rule)) {
                continue;
            }
            try {
                amount = rule.adjustHealing(player, event.getRegainReason(), amount);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Modifier '" + modifier.id()
                        + "' threw adjusting healing for " + player.getName(), e);
            }
        }
        if (amount <= 0) {
            event.setCancelled(true);
        } else if (amount != event.getAmount()) {
            event.setAmount(amount);
        }
    }

    /** Tells attached modifiers when their owner has killed something. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        for (Slayer slayer : capabilities(killer, Slayer.class)) {
            try {
                slayer.onKill(killer, victim);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Modifier '" + slayer.id()
                        + "' threw on a kill by " + killer.getName(), e);
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

    /**
     * Whether a player is carrying a particular modifier right now.
     *
     * <p>For the handful of systems that have to ask from outside the dispatch loop - the
     * Singularity checking who its creatures should be drawn to, the injury service checking
     * who cannot be maimed. Reads the attached list rather than the profile, so it answers
     * "is this in effect" rather than "is this written down", which is the question every
     * caller actually has.
     */
    public boolean carries(Player player, String modifierId) {
        for (Modifier modifier : attachedTo(player)) {
            if (modifier.id().equals(modifierId)) {
                return true;
            }
        }
        return false;
    }

    /** Every attached modifier that has the given capability. */
    public <T> List<T> capabilities(Player player, Class<T> capability) {
        List<T> found = new ArrayList<>();
        for (Modifier modifier : attachedTo(player)) {
            if (capability.isInstance(modifier)) {
                found.add(capability.cast(modifier));
            }
        }
        return found;
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
