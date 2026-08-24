package com.liminalis.plugin.ability;

import com.liminalis.core.ability.TierRequirement;
import com.liminalis.core.injury.ActiveInjury;
import com.liminalis.core.injury.InjurySeverity;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.injury.Injury;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.capability.DamageDealer;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import com.liminalis.plugin.trait.TraitTuning;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Mob;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Priest - the reference implementation, and the Creator's own.
 *
 * <p>Exists as much to be copied as to be played. Every ability written after this one will
 * be a variation on the same three parts: a thing you do to people, a thing you do to
 * enemies, and something at the top worth working toward. It is written out longhand rather
 * than assembled from helpers so the next one can be written by reading this one.
 *
 * <p><strong>Tier 1</strong> - lay hands on someone and heal them.
 * <br><strong>Tier 2</strong> - holy weight behind every blow against the undead.
 * <br><strong>Tier 3</strong> - close a mortal wound, which nothing else in the game can do.
 *
 * <p>Nothing here works on a player as a target of harm. Abilities are for survival and for
 * each other, and the one that heals should be the clearest example of that.
 */
public final class PriestAbility implements Ability, DamageDealer {

    public static final String ID = "priest";

    /** Counters this ability advances on. Namespaced, as every ability's must be. */
    public static final String HEALED = "priest.healed";
    public static final String UNDEAD_FELLED = "priest.undead_felled";

    private final TraitTuning tuning;
    private final ProfileManager profiles;
    private final ModifierRegistry registry;
    private final Messages messages;

    /** Last use, per player, so laying on hands is not spammable. Transient by design. */
    private final Map<UUID, Long> lastHeal = new ConcurrentHashMap<>();

    public PriestAbility(TraitTuning tuning,
                         ProfileManager profiles,
                         ModifierRegistry registry,
                         Messages messages) {
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<TierRequirement> tiers() {
        return List.of(
                new TierRequirement(1, HEALED, 0),
                new TierRequirement(2, HEALED,
                        (int) tuning.get("priest.tier2-healing-required", 200)),
                new TierRequirement(3, UNDEAD_FELLED,
                        (int) tuning.get("priest.tier3-undead-required", 100)));
    }

    // ------------------------------------------------------------------- tier 1: healing

    /**
     * Lays hands on someone.
     *
     * @return true if anything happened, so the caller knows whether to cancel the interact
     */
    public boolean layHands(Player priest, Player target, int tier) {
        long now = System.currentTimeMillis();
        long cooldown = (long) tuning.get("priest.heal-cooldown-seconds", 8) * 1000L;
        long ready = lastHeal.getOrDefault(priest.getUniqueId(), 0L) + cooldown;

        if (now < ready) {
            messages.send(priest, "ability.priest.not-yet",
                    Messages.placeholder("seconds", (int) Math.ceil((ready - now) / 1000.0)));
            return true;
        }

        AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        if (target.getHealth() >= max) {
            messages.send(priest, "ability.priest.already-whole",
                    Messages.placeholder("player", target.getName()));
            return true;
        }

        double amount = tuning.get("priest.heal-amount", 6.0);
        double healed = Math.min(amount, max - target.getHealth());
        target.setHealth(target.getHealth() + healed);
        lastHeal.put(priest.getUniqueId(), now);

        // Progress counts what actually landed, not what was attempted. Healing someone who
        // was nearly full should not advance a tier as much as pulling someone off the floor.
        award(priest, HEALED, (int) Math.round(healed));

        target.getWorld().spawnParticle(Particle.HEART,
                target.getLocation().add(0, 1.6, 0), 6, 0.35, 0.4, 0.35, 0.0);
        target.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);

        messages.send(priest, "ability.priest.healed",
                Messages.placeholder("player", target.getName()));
        messages.send(target, "ability.priest.healed-by",
                Messages.placeholder("player", priest.getName()));
        return true;
    }

    // -------------------------------------------------------------- tier 2: holy damage

    @Override
    public double adjustOutgoing(Player attacker, Entity victim, double damage) {
        // Never against another player. This ability is for keeping people alive.
        if (victim instanceof Player || !isUndead(victim)) {
            return damage;
        }
        if (tierOf(attacker) < 2) {
            return damage;
        }
        double bonus = tuning.get("priest.holy-damage", 4.0);
        return damage + bonus;
    }

    /** Called when something this priest was fighting dies, so tier 3 can be worked toward. */
    public void recordFelled(Player priest, Entity victim) {
        if (tierOf(priest) >= 2 && isUndead(victim)) {
            award(priest, UNDEAD_FELLED, 1);
        }
    }

    private static boolean isUndead(Entity entity) {
        return entity instanceof Mob mob && mob.getCategory()
                == org.bukkit.entity.EntityCategory.UNDEAD;
    }

    // ------------------------------------------------------- tier 3: closing mortal wounds

    /**
     * Closes a mortal wound - the only thing in the game that can, short of dying.
     *
     * <p>This is what the whole ability is for. Until it exists on somebody's server, a lost
     * arm is permanent until its owner spends a life, and the moment one Priest reaches tier
     * three that stops being true for everybody they can reach.
     */
    public boolean treat(Player priest, Player patient) {
        PlayerProfile theirs = profiles.resident(patient.getUniqueId()).orElse(null);
        if (theirs == null) {
            return false;
        }

        Optional<ActiveInjury> mortal = theirs.injuries().stream()
                .filter(injury -> registry.find(injury.id())
                        .filter(Injury.class::isInstance)
                        .map(Injury.class::cast)
                        .map(known -> known.severity() == InjurySeverity.MORTAL_WOUND)
                        .orElse(false))
                .findFirst();

        if (mortal.isEmpty()) {
            messages.send(priest, "ability.priest.nothing-to-treat",
                    Messages.placeholder("player", patient.getName()));
            return true;
        }

        ActiveInjury wound = mortal.get();
        theirs.removeInjury(wound.id());
        profiles.saveNow(theirs);

        patient.getWorld().spawnParticle(Particle.END_ROD,
                patient.getLocation().add(0, 1.2, 0), 40, 0.5, 0.8, 0.5, 0.02);
        patient.playSound(patient.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);

        messages.send(priest, "ability.priest.treated",
                Messages.placeholder("player", patient.getName()));
        messages.send(patient, "ability.priest.treated-by",
                Messages.placeholder("player", priest.getName()));
        return true;
    }

    // -------------------------------------------------------------------------- helpers

    private void award(Player priest, String counter, int amount) {
        PlayerProfile profile = profiles.resident(priest.getUniqueId()).orElse(null);
        if (profile != null && amount > 0) {
            profile.addAbilityProgress(counter, amount);
        }
    }

    private int tierOf(Player player) {
        return profiles.resident(player.getUniqueId())
                .map(PlayerProfile::abilityTier)
                .orElse(1);
    }

    /** Cleans up the cooldown when the ability is detached. */
    @Override
    public void onDetach(Player player) {
        lastHeal.remove(player.getUniqueId());
    }
}
