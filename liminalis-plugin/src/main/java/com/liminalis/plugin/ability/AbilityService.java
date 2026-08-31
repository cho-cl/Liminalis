package com.liminalis.plugin.ability;

import com.liminalis.core.ability.AbilityLevels;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.rescue.RescueService;
import com.liminalis.plugin.singularity.SingularityResidue;
import com.liminalis.plugin.text.Messages;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives abilities: their progress, their tiers, and the residue that hurries them along.
 *
 * <p>Tier-ups are checked whenever progress moves rather than on a timer, so the moment
 * somebody crosses a threshold they are told. An ability that quietly became stronger and
 * only revealed it the next time the player happened to use it would waste the best moment
 * it has.
 */
public final class AbilityService implements Listener {

    private final JavaPlugin plugin;
    private final ConfigService config;
    private final ProfileManager profiles;
    private final ModifierRegistry registry;
    private final ModifierService modifiers;
    private final RescueService rescue;
    private final Messages messages;
    private final Debug debug;

    /** Per-player, per-power ready times. Transient by design. */
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public AbilityService(JavaPlugin plugin,
                          ConfigService config,
                          ProfileManager profiles,
                          ModifierRegistry registry,
                          ModifierService modifiers,
                          RescueService rescue,
                          Messages messages,
                          Debug debug) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
        this.rescue = Objects.requireNonNull(rescue, "rescue");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.debug = Objects.requireNonNull(debug, "debug");
    }

    // ---------------------------------------------------------------------------- using

    // ---------------------------------------------------------------------------- firing

    /**
     * Runs a power, charging its cooldown only if it actually did something.
     *
     * <p>A power that refuses - nothing to smite, nobody hurt to heal - costs nothing. Making
     * a misfire cost the cooldown would punish players for the plugin not telling them the
     * state of the world.
     */
    public void fire(Player user, Ability ability, Power power, Player target) {
        // The focus has to be in hand. It makes an ability visible across a field and makes
        // using one a decision, because the hand holding a staff is not holding a sword.
        if (!AbilityFocus.held(plugin, user, ability.id())) {
            messages.send(user, "ability.focus.missing",
                    Messages.placeholder("focus",
                            messages.get("ability." + ability.id() + ".focus.name")));
            return;
        }

        long remaining = cooldownRemaining(user, power);
        if (remaining > 0) {
            messages.send(user, "ability.cooling",
                    Messages.placeholder("seconds", (int) remaining));
            return;
        }

        boolean fired;
        try {
            fired = power.use(user, target);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Power '" + power.id() + "' of ability '" + ability.id()
                            + "' threw for " + user.getName(), e);
            messages.send(user, "ability.failed");
            return;
        }

        if (fired && power.cooldownSeconds() > 0) {
            cooldowns.computeIfAbsent(user.getUniqueId(), id -> new ConcurrentHashMap<>())
                    .put(power.id(), System.currentTimeMillis()
                            + power.cooldownSeconds() * 1000L);
        }
        if (fired) {
            // Only a power that did something counts. A smite with nothing to smite is not
            // progress, or the fastest way to level would be firing into an empty field.
            recordUse(user, 1);
        }
    }

    /**
     * Adds to the one counter and opens whatever that earns.
     *
     * <p>The level is recomputed from the counter rather than incremented, so a level that
     * drifted - through an admin edit, or a rebalanced ladder in config - corrects itself the
     * next time anything happens. One source of truth, and it is the count.
     */
    public void recordUse(Player player, int uses) {
        PlayerProfile profile = profiles.resident(player.getUniqueId()).orElse(null);
        Optional<Ability> ability = abilityOf(player);
        if (profile == null || ability.isEmpty()) {
            return;
        }
        if (uses > 0) {
            profile.addAbilityProgress(AbilityLevels.USES, uses);
        }

        List<Integer> ladder = ladderFor(ability.get());
        int earned = AbilityLevels.levelFor(
                profile.abilityProgress().getOrDefault(AbilityLevels.USES, 0), ladder);
        int before = profile.abilityTier();
        profiles.saveNow(profile);

        if (earned == before) {
            return;
        }
        profile.setAbilityTier(earned);
        profiles.saveNow(profile);
        modifiers.applyFromProfile(player);

        if (earned > before) {
            messages.send(player, "ability.level-gained",
                    Messages.placeholder("level", earned),
                    Messages.placeholder("granted",
                            (net.kyori.adventure.text.Component)
                                    messages.get(ability.get().levelKey(earned))));
        }
        debug.log(() -> player.getName() + " ability level " + before + " -> " + earned);
    }

    /**
     * The ladder this ability climbs, cut to the number of powers it actually has.
     *
     * <p>Every ability shares the same rungs, but an ability with three powers has no use for
     * the fourth and fifth - and letting somebody reach a level with nothing behind it would
     * announce a power that does not exist.
     */
    public List<Integer> ladderFor(Ability ability) {
        List<Integer> configured = config.get().abilities().usesPerLevel();
        int rungs = Math.min(configured.size(), Math.max(0, ability.maxLevel() - 1));
        return configured.subList(0, rungs);
    }

    /** Uses still needed for the next power, or 0 at the top. */
    public int usesToNextLevel(Player player) {
        PlayerProfile profile = profiles.resident(player.getUniqueId()).orElse(null);
        Optional<Ability> ability = abilityOf(player);
        if (profile == null || ability.isEmpty()) {
            return 0;
        }
        return AbilityLevels.usesToNext(
                profile.abilityProgress().getOrDefault(AbilityLevels.USES, 0),
                ladderFor(ability.get()));
    }

    /** Seconds left on a power, or 0 if it is ready. */
    public long cooldownRemaining(Player user, Power power) {
        Map<String, Long> theirs = cooldowns.get(user.getUniqueId());
        if (theirs == null) {
            return 0;
        }
        long until = theirs.getOrDefault(power.id(), 0L);
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    /** Cooldowns are transient - a restart clearing them is not worth a profile field. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------------ accelerant

    /**
     * Spending residue to hurry an ability along.
     *
     * <p>This is why residue exists as a currency at all. An ability gated behind something
     * its owner rarely does - a priest who never fights undead, say - would otherwise leave
     * them staring at a tier they can read about and never reach.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpendResidue(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                    && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        if (!SingularityResidue.is(plugin, held) || !player.isSneaking()) {
            return;
        }
        event.setCancelled(true);

        Optional<Ability> ability = abilityOf(player);
        if (ability.isEmpty()) {
            messages.send(player, "ability.none-to-feed");
            return;
        }
        if (usesToNextLevel(player) <= 0) {
            messages.send(player, "ability.already-complete");
            return;
        }

        int gained = config.get().abilities().usesPerResidue();
        held.subtract();
        recordUse(player, gained);

        messages.send(player, "ability.fed", Messages.placeholder("amount", gained));
    }

    // -------------------------------------------------------------------------- lookup

    public Optional<Ability> abilityOf(Player player) {
        return profiles.resident(player.getUniqueId())
                .map(PlayerProfile::abilityId)
                .flatMap(registry::find)
                .filter(Ability.class::isInstance)
                .map(Ability.class::cast);
    }

    private <T extends Ability> Optional<T> abilityOf(Player player, Class<T> type) {
        return abilityOf(player).filter(type::isInstance).map(type::cast);
    }

    private int tierOf(Player player) {
        return profiles.resident(player.getUniqueId())
                .map(PlayerProfile::abilityTier)
                .orElse(1);
    }
}
