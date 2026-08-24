package com.liminalis.plugin.injury;

import com.liminalis.core.injury.ActiveInjury;
import com.liminalis.core.injury.DamageCategory;
import com.liminalis.core.injury.DamageDescriptor;
import com.liminalis.core.injury.InjuryRules;
import com.liminalis.core.injury.InjurySeverity;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.core.roll.WeightedEntry;
import com.liminalis.core.roll.WeightedPool;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.modifier.ModifierType;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Inflicts wounds, lets them fade, and wipes them when a player respawns.
 *
 * <p>That last rule is the one that gives the system its shape. Injuries persist through
 * logouts and restarts, but a new body carries no old harm - so a player with a lost arm can
 * live crippled, or spend one of their three lives to be whole again. The cost of healing is
 * measured in lives, and it gets steeper every time.
 */
public final class InjuryService implements Listener {

    /** Ticks between expiry sweeps. Two seconds is far finer than any wound's lifetime. */
    private static final long DECAY_INTERVAL_TICKS = 40L;

    private final JavaPlugin plugin;
    private final ConfigService config;
    private final ProfileManager profiles;
    private final ModifierRegistry registry;
    private final ModifierService modifiers;
    private final Messages messages;
    private final Debug debug;
    private final Random random = new Random();

    private BukkitTask decayTask;

    public InjuryService(JavaPlugin plugin,
                         ConfigService config,
                         ProfileManager profiles,
                         ModifierRegistry registry,
                         ModifierService modifiers,
                         Messages messages,
                         Debug debug) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.debug = Objects.requireNonNull(debug, "debug");
    }

    /** One repeating sweep for the whole injury system, not one timer per wound. */
    public void start() {
        decayTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::sweepExpired, DECAY_INTERVAL_TICKS, DECAY_INTERVAL_TICKS);
    }

    public void stop() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
    }

    // ------------------------------------------------------------------------ infliction

    /**
     * Judges a blow after the server has finished reducing it.
     *
     * <p>{@code MONITOR} so the damage figure read here is the one the player actually took,
     * armour and enchantments and Phase 1's PvP halving all applied. Reading it any earlier
     * would mean full plate offered no protection against losing an arm.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerProfile profile = profiles.resident(player.getUniqueId()).orElse(null);
        if (profile == null || profile.inLimbo()) {
            // Nothing can hurt you in Limbo, so nothing can wound you there either.
            return;
        }

        double finalDamage = event.getFinalDamage();
        if (finalDamage >= player.getHealth()) {
            // This blow kills. Wounding a corpse achieves nothing, and respawn would clear
            // it a moment later anyway.
            return;
        }

        DamageCategory category = categorise(event);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();

        InjurySeverity severity = InjuryRules.classify(
                new DamageDescriptor(category, finalDamage, max),
                config.get().injuries(), random);
        if (severity == InjurySeverity.NONE) {
            return;
        }

        inflict(player, profile, category, severity);
    }

    private void inflict(Player player, PlayerProfile profile,
                         DamageCategory category, InjurySeverity severity) {
        List<Injury> pool = matching(category, severity);
        if (pool.isEmpty()) {
            return;
        }

        List<WeightedEntry> entries = pool.stream().map(Injury::asEntry).toList();
        // Exclude what they already have, so a wound is never simply re-dealt.
        Set<String> existing = profile.injuries().stream()
                .map(ActiveInjury::id)
                .collect(java.util.stream.Collectors.toSet());

        Optional<String> chosen = WeightedPool.pick(entries, existing, random);
        if (chosen.isEmpty()) {
            return;
        }

        Injury injury = pool.stream()
                .filter(candidate -> candidate.id().equals(chosen.get()))
                .findFirst()
                .orElseThrow();

        long expiresAt = injury.decays()
                ? System.currentTimeMillis() + (injury.durationSeconds() * 1000L)
                : 0L;
        profile.addInjury(new ActiveInjury(injury.id(), expiresAt));
        profiles.saveNow(profile);
        modifiers.applyFromProfile(player);

        messages.send(player,
                severity == InjurySeverity.MORTAL_WOUND ? "injuries.mortal" : "injuries.taken",
                Messages.placeholder("injury", messages.get(injury.nameKey())),
                Messages.placeholder("description",
                        (net.kyori.adventure.text.Component) messages.get(injury.descriptionKey())));

        debug.log(() -> player.getName() + " took " + severity + " " + injury.id()
                + " from " + category);
    }

    private List<Injury> matching(DamageCategory category, InjurySeverity severity) {
        List<Injury> matches = new ArrayList<>();
        for (Injury injury : allInjuries()) {
            if (injury.severity() == severity && injury.causes().contains(category)) {
                matches.add(injury);
            }
        }
        return matches;
    }

    // ----------------------------------------------------------------------------- decay

    /**
     * Retires wounds whose time is up.
     *
     * <p>A Regeneration effect makes time pass faster for the wounded: each sweep pulls the
     * expiry closer by an extra interval. That is what "faster with regen potions" means
     * mechanically, and it gives the potion a second purpose beyond the health it restores.
     */
    private void sweepExpired() {
        long now = System.currentTimeMillis();
        long acceleration = (long) (DECAY_INTERVAL_TICKS * 50L
                * config.get().injuries().regenerationSpeedup());

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerProfile profile = profiles.resident(player.getUniqueId()).orElse(null);
            if (profile == null || profile.injuries().isEmpty()) {
                continue;
            }

            boolean healing = player.hasPotionEffect(PotionEffectType.REGENERATION);
            List<ActiveInjury> expired = new ArrayList<>();

            for (ActiveInjury injury : profile.injuries()) {
                if (injury.permanent()) {
                    continue;
                }
                ActiveInjury current = injury;
                if (healing && acceleration > 0) {
                    current = new ActiveInjury(injury.id(), injury.expiresAt() - acceleration);
                    profile.addInjury(current);
                }
                if (current.hasExpired(now)) {
                    expired.add(current);
                }
            }

            if (expired.isEmpty()) {
                continue;
            }
            expired.forEach(injury -> profile.removeInjury(injury.id()));
            profiles.saveNow(profile);
            modifiers.applyFromProfile(player);

            for (ActiveInjury injury : expired) {
                registry.find(injury.id()).ifPresent(modifier ->
                        messages.send(player, "injuries.healed", Messages.placeholder(
                                "injury", messages.get(modifier.nameKey()))));
            }
        }
    }

    // --------------------------------------------------------------------------- respawn

    /**
     * A new body carries no old wounds.
     *
     * <p>Deliberate, and the sharpest choice in the phase: it means someone with a mortal
     * wound can trade a life to be whole again. The trade gets harder each time, and on the
     * last life it is not a trade at all.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = profiles.resident(player.getUniqueId()).orElse(null);
        if (profile == null || profile.injuries().isEmpty()) {
            return;
        }
        int cleared = profile.injuries().size();
        profile.clearInjuries();
        profiles.saveNow(profile);

        // Next tick: attributes applied during the respawn itself would otherwise be
        // overwritten by the server finishing the respawn.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                modifiers.applyFromProfile(player);
                messages.send(player, "injuries.cleared-by-death");
            }
        });
        debug.log(() -> "cleared " + cleared + " injuries from " + player.getName()
                + " on respawn");
    }

    // -------------------------------------------------------------------------- helpers

    public List<Injury> allInjuries() {
        return registry.ofType(ModifierType.INJURY).stream()
                .filter(Injury.class::isInstance)
                .map(Injury.class::cast)
                .toList();
    }

    /**
     * Reduces Minecraft's damage causes to the six kinds of harm that leave different marks.
     *
     * <p>Melee is split by what was in the attacker's hand, so "slashed by a sword" is
     * literally true rather than an approximation - a mace or a fist crushes instead.
     */
    private DamageCategory categorise(EntityDamageEvent event) {
        return switch (event.getCause()) {
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> meleeKind(event);
            case PROJECTILE -> DamageCategory.PIERCING;
            case FALL -> DamageCategory.FALLING;
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE, MELTING ->
                    DamageCategory.BURNING;
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> DamageCategory.EXPLOSIVE;
            case FALLING_BLOCK, CONTACT, FLY_INTO_WALL -> DamageCategory.CRUSHING;
            default -> DamageCategory.OTHER;
        };
    }

    private DamageCategory meleeKind(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)
                || !(byEntity.getDamager() instanceof Player attacker)) {
            return DamageCategory.CRUSHING;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        String type = weapon.getType().name();
        return type.endsWith("_SWORD") || type.endsWith("_AXE")
                ? DamageCategory.SLASHING : DamageCategory.CRUSHING;
    }
}
