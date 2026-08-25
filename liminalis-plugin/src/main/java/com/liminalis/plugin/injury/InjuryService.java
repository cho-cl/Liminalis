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
import com.liminalis.plugin.modifier.capability.MortalWard;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /** Seconds each player has spent under Regeneration since their last mended wound. */
    private final Map<UUID, Double> regenerating = new ConcurrentHashMap<>();

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

        if (WoundDamage.isWoundTick(player)) {
            // Damage from a wound they already have. Judging it would let bleeding inflict
            // bleeding, which is a loop that only ends when the player does.
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
        severity = soften(player, severity);

        inflict(player, profile, category, severity);
    }

    /**
     * Downgrades a maiming blow for anyone who cannot be maimed.
     *
     * <p>Downgraded rather than cancelled. A blessing that turned the hardest hits in the game
     * into nothing at all would make the Unbroken safest exactly when everyone else is in the
     * most trouble, which is a stronger promise than intended - they should still come out of
     * it bleeding, just still whole.
     */
    private InjurySeverity soften(Player player, InjurySeverity severity) {
        if (severity != InjurySeverity.MORTAL_WOUND) {
            return severity;
        }
        for (MortalWard ward : modifiers.capabilities(player, MortalWard.class)) {
            if (ward.softensMortalWounds(player)) {
                messages.send(player, "injuries.ward-held");
                debug.log(() -> ward.id() + " softened a mortal wound on " + player.getName());
                return InjurySeverity.INJURY;
            }
        }
        return severity;
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

    // ------------------------------------------------------------------- decay and mending

    /**
     * Retires wounds whose time is up, and mends one for anyone sitting under Regeneration.
     *
     * <p>Regeneration used to quietly pull every wound's expiry closer, which was invisible:
     * nothing happened at any particular moment, wounds simply went away sooner than the
     * player had any way to notice. It mends them outright now, one at a time, and says so
     * when it does - so drinking the potion is an action with a result rather than a change
     * to a number nobody can see.
     */
    private void sweepExpired() {
        long now = System.currentTimeMillis();
        double cureSeconds = config.get().injuries().regenerationCureSeconds();
        double sweepSeconds = DECAY_INTERVAL_TICKS / 20.0;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerProfile profile = profiles.resident(player.getUniqueId()).orElse(null);
            if (profile == null || profile.injuries().isEmpty()) {
                regenerating.remove(player.getUniqueId());
                continue;
            }

            List<ActiveInjury> expired = new ArrayList<>();
            for (ActiveInjury injury : profile.injuries()) {
                if (!injury.permanent() && injury.hasExpired(now)) {
                    expired.add(injury);
                }
            }
            if (!expired.isEmpty()) {
                expired.forEach(injury -> profile.removeInjury(injury.id()));
                persistAndReapply(player, profile);
                for (ActiveInjury injury : expired) {
                    announceHealed(player, injury.id(), "injuries.healed");
                }
            }

            tickRegeneration(player, profile, cureSeconds, sweepSeconds);
        }
    }

    /** Counts up the time a player has spent regenerating, and mends a wound per interval. */
    private void tickRegeneration(Player player, PlayerProfile profile,
                                  double cureSeconds, double sweepSeconds) {
        UUID id = player.getUniqueId();
        if (cureSeconds <= 0 || !player.hasPotionEffect(PotionEffectType.REGENERATION)) {
            // Reset rather than pause: a player who drinks a second potion later should not
            // get an instant cure out of seconds banked half an hour ago.
            regenerating.remove(id);
            return;
        }

        double elapsed = regenerating.merge(id, sweepSeconds, Double::sum);
        if (elapsed < cureSeconds) {
            return;
        }
        regenerating.put(id, elapsed - cureSeconds);
        mend(player, profile, 1, "injuries.mended-by-regeneration");
    }

    // ---------------------------------------------------------------------------- mending

    /**
     * A potion of Healing mends a wound as well as the health.
     *
     * <p>{@code MAGIC} is the reason vanilla gives for instant health specifically, so this
     * catches the potion whether it was drunk, thrown or fired, and catches nothing else -
     * eating, natural regeneration and the Regeneration effect all report differently.
     *
     * <p>Strength is deliberately ignored - Healing II mends exactly as many wounds as
     * Healing I, and simply restores more health. It could have been read from the effect,
     * except that an instantaneous effect is applied and discarded without ever joining the
     * player's active effects, so the lookup would have returned nothing and quietly answered
     * "tier one" forever. Inferring the tier back out of the healed amount is the other
     * option and is worse: it guesses, and it would start guessing wrong the moment anything
     * else touched healing, which on this server two curses already do.
     *
     * <p>{@code ignoreCancelled} is deliberate and has one consequence worth stating: a
     * Bloodhungry player, whose curse refuses every source of healing there is, gets nothing
     * out of the potion at all. That is the curse working rather than this failing.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInstantHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getRegainReason() != EntityRegainHealthEvent.RegainReason.MAGIC) {
            return;
        }
        PlayerProfile profile = profiles.resident(player.getUniqueId()).orElse(null);
        if (profile == null || profile.injuries().isEmpty()) {
            return;
        }
        mend(player, profile, config.get().injuries().instantHealthCures(),
                "injuries.mended-by-potion");
    }

    /**
     * Mends up to {@code count} ordinary wounds, worst first.
     *
     * <p>Worst first - the one with the longest left to run - so a potion is spent on the
     * thing that was actually bothering them rather than on a wound about to fade anyway.
     *
     * <p>Mortal wounds are never touched. Nothing you can drink regrows an arm; that still
     * costs a life, or a Priest at the top of their tier.
     */
    private void mend(Player player, PlayerProfile profile, int count, String messageKey) {
        if (count <= 0) {
            return;
        }
        List<ActiveInjury> mendable = new ArrayList<>(profile.injuries().stream()
                .filter(injury -> !injury.permanent())
                .toList());
        if (mendable.isEmpty()) {
            return;
        }
        mendable.sort(Comparator.comparingLong(ActiveInjury::expiresAt).reversed());

        List<ActiveInjury> mended =
                List.copyOf(mendable.subList(0, Math.min(count, mendable.size())));
        mended.forEach(injury -> profile.removeInjury(injury.id()));
        persistAndReapply(player, profile);

        for (ActiveInjury injury : mended) {
            announceHealed(player, injury.id(), messageKey);
        }
        player.getWorld().spawnParticle(Particle.HEART,
                player.getLocation().add(0, 1.2, 0), mended.size() + 2, 0.4, 0.4, 0.4, 0.0);
        debug.log(() -> player.getName() + " mended " + mended.size() + " wound(s)");
    }

    private void persistAndReapply(Player player, PlayerProfile profile) {
        profiles.saveNow(profile);
        modifiers.applyFromProfile(player);
    }

    private void announceHealed(Player player, String injuryId, String messageKey) {
        registry.find(injuryId).ifPresent(modifier -> messages.send(player, messageKey,
                Messages.placeholder("injury", messages.get(modifier.nameKey()))));
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
     * The kind of harm this blow did.
     *
     * <p>The table itself lives in {@link DamageCauses} so it can be read and checked without
     * a server. Melee is the one answer that cannot be given by the cause alone: it is split
     * by what was in the attacker's hand, so "slashed by a sword" is literally true rather
     * than an approximation, and a mace or a bare fist crushes instead.
     */
    private DamageCategory categorise(EntityDamageEvent event) {
        DamageCategory fromCause = DamageCauses.categoryOf(event.getCause());
        return fromCause != null ? fromCause : meleeKind(event);
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
