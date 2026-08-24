package com.liminalis.plugin.ability;

import com.liminalis.core.ability.TierRequirement;
import com.liminalis.core.injury.ActiveInjury;
import com.liminalis.core.injury.InjurySeverity;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.injury.Injury;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import com.liminalis.plugin.trait.TraitTuning;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The Priest - the reference implementation, and the Creator's own.
 *
 * <p>Exists as much to be copied as to be played. Every ability written after this one is five
 * powers on the same frame, so this is written longhand rather than assembled from helpers:
 * the next one should be writable by reading this one and changing what each power does.
 *
 * <p>The five are deliberately not five sizes of the same thing. One helps someone else, one
 * helps you, one hurts what deserves it, one protects a group, and one does something nothing
 * else in the world can. An ability whose powers are all the same verb has one power and four
 * numbers.
 *
 * <p>Nothing here touches a player as a target of harm. Abilities are for surviving and for
 * each other, and the one that heals should be the clearest example of that.
 */
public final class PriestAbility implements Ability {

    public static final String ID = "priest";

    /** Counters this ability advances on. Namespaced, as every ability's must be. */
    public static final String HEALED = "priest.healed";
    public static final String UNDEAD_FELLED = "priest.undead_felled";

    private final TraitTuning tuning;
    private final ProfileManager profiles;
    private final ModifierRegistry registry;
    private final ModifierService modifiers;
    private final Messages messages;

    public PriestAbility(TraitTuning tuning,
                         ProfileManager profiles,
                         ModifierRegistry registry,
                         ModifierService modifiers,
                         Messages messages) {
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public String id() {
        return ID;
    }

    /**
     * Five tiers, five powers, and the counters deliberately alternate.
     *
     * <p>Healing opens the first half and felling the undead opens the second, so nobody
     * reaches the top by only ever doing the half they find easier. The order also means the
     * ability teaches itself: you learn to keep people alive before you are handed anything
     * that kills.
     */
    @Override
    public List<TierRequirement> tiers() {
        return List.of(
                new TierRequirement(1, HEALED, 0),
                new TierRequirement(2, HEALED, (int) tuning.get("priest.tier2-healing", 120)),
                new TierRequirement(3, HEALED, (int) tuning.get("priest.tier3-healing", 300)),
                new TierRequirement(4, UNDEAD_FELLED,
                        (int) tuning.get("priest.tier4-undead", 60)),
                new TierRequirement(5, UNDEAD_FELLED,
                        (int) tuning.get("priest.tier5-undead", 150)));
    }

    @Override
    public List<Power> powers() {
        return List.of(new LayHands(), new MendSelf(), new HolySmite(),
                new Consecrate(), new CloseTheWound());
    }

    // ------------------------------------------------------------------------ 1. others

    /**
     * Heal someone else, and close what is closeable.
     *
     * <p>Clears ordinary injuries as well as restoring health, which is what keeps the first
     * power worth using once the later ones exist. Mortal wounds are untouched - those are
     * power five, and the whole arc of the ability is earning the right to fix them.
     */
    private final class LayHands implements Power {

        @Override
        public int slot() {
            return 1;
        }

        @Override
        public String id() {
            return "lay_hands";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("priest.lay-hands-cooldown", 8);
        }

        @Override
        public boolean needsTarget() {
            return true;
        }

        @Override
        public boolean use(Player priest, Player target) {
            int healed = restore(target, tuning.get("priest.lay-hands-heal", 6.0));
            List<String> cured = cureMinorInjuries(target);

            if (healed == 0 && cured.isEmpty()) {
                messages.send(priest, "ability.priest.already-whole",
                        Messages.placeholder("player", target.getName()));
                return false;
            }

            award(priest, HEALED, healed);
            bless(target, Particle.HEART, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f);

            messages.send(priest, "ability.priest.healed",
                    Messages.placeholder("player", target.getName()));
            messages.send(target, "ability.priest.healed-by",
                    Messages.placeholder("player", priest.getName()));
            if (!cured.isEmpty()) {
                messages.send(target, "ability.priest.injuries-closed",
                        Messages.placeholder("injuries", String.join(", ", cured)));
            }
            return true;
        }
    }

    // -------------------------------------------------------------------------- 2. self

    /**
     * Patch yourself up.
     *
     * <p>Deliberately weaker and far slower than laying hands on somebody else. A priest who
     * keeps themselves standing better than they keep anyone else standing is not a priest,
     * they are a warrior with extra steps - so this exists to stop them being helpless alone,
     * not to make them self-sufficient. It earns no progress for the same reason: the counters
     * measure what you do for other people.
     */
    private final class MendSelf implements Power {

        @Override
        public int slot() {
            return 2;
        }

        @Override
        public String id() {
            return "mend_self";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("priest.mend-self-cooldown", 25);
        }

        @Override
        public boolean use(Player priest, Player ignored) {
            if (restore(priest, tuning.get("priest.mend-self-heal", 4.0)) == 0) {
                messages.send(priest, "ability.priest.self-whole");
                return false;
            }
            bless(priest, Particle.END_ROD, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.1f);
            messages.send(priest, "ability.priest.mended");
            return true;
        }
    }

    // ------------------------------------------------------------------------- 3. smite

    /**
     * Burn everything undead standing near you.
     *
     * <p>An area effect rather than a targeted bolt, because the moment a priest actually
     * needs this is when several things have closed on them at once. The living are untouched
     * entirely - it is not a weapon, it is an objection.
     */
    private final class HolySmite implements Power {

        @Override
        public int slot() {
            return 3;
        }

        @Override
        public String id() {
            return "holy_smite";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("priest.smite-cooldown", 20);
        }

        @Override
        public boolean use(Player priest, Player ignored) {
            double range = tuning.get("priest.smite-range", 6.0);
            double damage = tuning.get("priest.smite-damage", 9.0);

            List<LivingEntity> struck = new ArrayList<>();
            for (Entity nearby : priest.getNearbyEntities(range, range, range)) {
                if (nearby instanceof LivingEntity living && isUndead(nearby)) {
                    struck.add(living);
                }
            }

            if (struck.isEmpty()) {
                messages.send(priest, "ability.priest.nothing-to-smite");
                return false;
            }

            for (LivingEntity victim : struck) {
                // Dealt as damage from the priest, so kills credit their counter and any
                // other system that cares who did it sees the right answer.
                victim.damage(damage, priest);
                victim.getWorld().spawnParticle(Particle.END_ROD,
                        victim.getLocation().add(0, 1.0, 0), 18, 0.3, 0.6, 0.3, 0.02);
            }
            priest.getWorld().playSound(priest.getLocation(),
                    Sound.ITEM_TRIDENT_THUNDER, 0.7f, 1.6f);

            messages.send(priest, "ability.priest.smote",
                    Messages.placeholder("count", struck.size()));
            return true;
        }
    }

    // -------------------------------------------------------------------- 4. consecrate

    /**
     * A moment of protection for everyone standing with you.
     *
     * <p>The only power that does almost nothing for a priest travelling alone, which is the
     * point of putting it this late: by tier four they should be someone a group forms around.
     */
    private final class Consecrate implements Power {

        @Override
        public int slot() {
            return 4;
        }

        @Override
        public String id() {
            return "consecrate";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("priest.consecrate-cooldown", 60);
        }

        @Override
        public boolean use(Player priest, Player ignored) {
            double range = tuning.get("priest.consecrate-range", 10.0);
            int ticks = (int) tuning.get("priest.consecrate-seconds", 12) * 20;

            List<Player> blessed = new ArrayList<>();
            blessed.add(priest);
            for (Entity nearby : priest.getNearbyEntities(range, range, range)) {
                if (nearby instanceof Player other) {
                    blessed.add(other);
                }
            }

            for (Player person : blessed) {
                person.addPotionEffect(new PotionEffect(
                        PotionEffectType.REGENERATION, ticks, 0, true, true));
                person.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE, ticks, 0, true, true));
                person.getWorld().spawnParticle(Particle.END_ROD,
                        person.getLocation().add(0, 1.2, 0), 20, 0.4, 0.7, 0.4, 0.01);
                if (!person.equals(priest)) {
                    messages.send(person, "ability.priest.consecrated-by",
                            Messages.placeholder("player", priest.getName()));
                }
            }
            priest.getWorld().playSound(priest.getLocation(),
                    Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.3f);

            messages.send(priest, "ability.priest.consecrated",
                    Messages.placeholder("count", blessed.size()));
            return true;
        }
    }

    // ----------------------------------------------------------------- 5. mortal wounds

    /**
     * Closes a mortal wound - the only thing in the world that can, short of dying.
     *
     * <p>What the whole ability is for. Until one priest reaches this tier, a lost arm is
     * permanent until its owner spends a life; the moment somebody gets here, that stops being
     * true for everyone they can reach.
     */
    private final class CloseTheWound implements Power {

        @Override
        public int slot() {
            return 5;
        }

        @Override
        public String id() {
            return "close_the_wound";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("priest.close-wound-cooldown", 300);
        }

        @Override
        public boolean needsTarget() {
            return true;
        }

        @Override
        public boolean use(Player priest, Player target) {
            PlayerProfile theirs = profiles.resident(target.getUniqueId()).orElse(null);
            if (theirs == null) {
                return false;
            }

            Optional<ActiveInjury> mortal = theirs.injuries().stream()
                    .filter(injury -> severityOf(injury.id()) == InjurySeverity.MORTAL_WOUND)
                    .findFirst();

            if (mortal.isEmpty()) {
                messages.send(priest, "ability.priest.nothing-to-treat",
                        Messages.placeholder("player", target.getName()));
                return false;
            }

            theirs.removeInjury(mortal.get().id());
            profiles.saveNow(theirs);
            modifiers.applyFromProfile(target);

            target.getWorld().spawnParticle(Particle.END_ROD,
                    target.getLocation().add(0, 1.2, 0), 60, 0.5, 0.9, 0.5, 0.03);
            target.playSound(target.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);

            messages.send(priest, "ability.priest.treated",
                    Messages.placeholder("player", target.getName()));
            messages.send(target, "ability.priest.treated-by",
                    Messages.placeholder("player", priest.getName()));
            return true;
        }
    }

    // ------------------------------------------------------------------------- progress

    /** Credited when something this priest killed turns out to have been undead. */
    public void recordFelled(Player priest, Entity victim) {
        if (isUndead(victim)) {
            award(priest, UNDEAD_FELLED, 1);
        }
    }

    // -------------------------------------------------------------------------- helpers

    /**
     * Heals up to {@code amount}, returning what actually landed.
     *
     * <p>Progress counts what landed rather than what was attempted, so topping up someone who
     * was nearly full is worth almost nothing and pulling somebody off the floor is worth the
     * lot.
     */
    private int restore(Player player, double amount) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        double healed = Math.min(amount, max - player.getHealth());
        if (healed <= 0) {
            return 0;
        }
        player.setHealth(player.getHealth() + healed);
        return (int) Math.round(healed);
    }

    /** Clears every ordinary injury, leaving mortal wounds for power five. */
    private List<String> cureMinorInjuries(Player target) {
        PlayerProfile theirs = profiles.resident(target.getUniqueId()).orElse(null);
        if (theirs == null) {
            return List.of();
        }
        List<String> cured = theirs.injuries().stream()
                .map(ActiveInjury::id)
                .filter(id -> severityOf(id) == InjurySeverity.INJURY)
                .toList();
        if (cured.isEmpty()) {
            return cured;
        }
        cured.forEach(theirs::removeInjury);
        profiles.saveNow(theirs);
        modifiers.applyFromProfile(target);
        return cured;
    }

    private InjurySeverity severityOf(String injuryId) {
        return registry.find(injuryId)
                .filter(Injury.class::isInstance)
                .map(Injury.class::cast)
                .map(Injury::severity)
                .orElse(null);
    }

    private void bless(Player who, Particle particle, Sound sound, float pitch) {
        who.getWorld().spawnParticle(particle, who.getLocation().add(0, 1.6, 0),
                8, 0.35, 0.4, 0.35, 0.0);
        who.playSound(who.getLocation(), sound, 1.0f, pitch);
    }

    private void award(Player priest, String counter, int amount) {
        PlayerProfile profile = profiles.resident(priest.getUniqueId()).orElse(null);
        if (profile != null && amount > 0) {
            profile.addAbilityProgress(counter, amount);
        }
    }

    private static boolean isUndead(Entity entity) {
        return entity instanceof Mob mob && mob.getCategory() == EntityCategory.UNDEAD;
    }
}
