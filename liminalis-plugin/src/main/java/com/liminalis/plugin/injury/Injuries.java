package com.liminalis.plugin.injury;

import com.liminalis.core.injury.DamageCategory;
import com.liminalis.core.injury.InjuryCoverage;
import com.liminalis.core.injury.InjurySeverity;
import com.liminalis.plugin.modifier.capability.AttributeContribution;
import com.liminalis.plugin.modifier.capability.AttributeSource;
import com.liminalis.plugin.modifier.capability.Ticking;
import com.liminalis.plugin.trait.TraitTuning;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * The wounds a player can be left with, and what each one costs them.
 *
 * <p>Every injury is tied to the kinds of harm that could plausibly cause it, because the
 * brief was specific about that: slashed by a sword and you bleed, fall off a cliff and you
 * sprain an ankle. A wound that did not match its cause would read as random punishment
 * rather than as consequence.
 *
 * <p>Mortal wounds are the same shape with a far larger cost and no expiry. Until a healing
 * ability exists to treat one, the only way out is to spend a life and get a new body - which
 * is the trade the whole system is built around.
 *
 * <p><strong>Every damage category must appear at both severities</strong>, and
 * {@link #validate(TraitTuning)} refuses to let the server start otherwise. That is not neatness. The
 * classifier decides severity from the damage alone and never asks whether a matching wound
 * exists, so a category with no mortal wound behind it meant the largest hits in the game
 * were classified, found nothing to inflict, and did nothing at all - while smaller hits of
 * the same kind wounded normally. Piercing had exactly that hole: a huge arrow left no mark
 * where a glancing one drew blood.
 */
public final class Injuries {

    private Injuries() {
    }

    public static List<Injury> all(TraitTuning tuning) {
        return List.of(
                // slashing / piercing
                new Bleeding(tuning),
                puncturedLung(tuning),
                lostArm(tuning),
                impaled(tuning),
                // falling / crushing
                sprainedAnkle(tuning),
                concussion(tuning),
                brokenLegs(tuning),
                // burning / explosive
                burns(tuning),
                charred(tuning),
                // frost
                frostbite(tuning),
                frozenMarrow(tuning),
                // withering
                poisonedBlood(tuning),
                rottingWound(tuning),
                // everything else
                shock(tuning),
                failingBody(tuning));
    }

    /**
     * Refuses a roster with a hole in it.
     *
     * <p>Called at startup. Failing loudly here costs one restart; failing silently costs a
     * season of players wondering why the worst hit they ever took did nothing.
     */
    public static void validate(TraitTuning tuning) {
        InjuryCoverage.require(all(tuning).stream()
                .map(injury -> new InjuryCoverage.Entry(
                        injury.id(), injury.causes(), injury.severity()))
                .toList());
    }

    // ------------------------------------------------------------------ ordinary wounds

    private static Injury sprainedAnkle(TraitTuning tuning) {
        return new SimpleInjury("sprained_ankle",
                Set.of(DamageCategory.FALLING), InjurySeverity.INJURY, tuning, 300,
                (t, player) -> List.of(AttributeContribution.scale(Attribute.MOVEMENT_SPEED,
                        -t.get("sprained_ankle.speed-penalty", 0.20))));
    }

    private static Injury burns(TraitTuning tuning) {
        return new SimpleInjury("burns",
                Set.of(DamageCategory.BURNING), InjurySeverity.INJURY, tuning, 300,
                (t, player) -> List.of(AttributeContribution.add(Attribute.MAX_HEALTH,
                        -t.get("burns.health-penalty", 4.0))));
    }

    private static Injury concussion(TraitTuning tuning) {
        return new SimpleInjury("concussion",
                Set.of(DamageCategory.EXPLOSIVE, DamageCategory.CRUSHING),
                InjurySeverity.INJURY, tuning, 240,
                (t, player) -> List.of(
                        AttributeContribution.scale(Attribute.BLOCK_BREAK_SPEED,
                                -t.get("concussion.break-speed-penalty", 0.30)),
                        AttributeContribution.add(Attribute.ATTACK_DAMAGE,
                                -t.get("concussion.attack-penalty", 1.0))));
    }

    private static Injury puncturedLung(TraitTuning tuning) {
        return new SimpleInjury("punctured_lung",
                Set.of(DamageCategory.PIERCING), InjurySeverity.INJURY, tuning, 240,
                (t, player) -> List.of(
                        AttributeContribution.add(Attribute.OXYGEN_BONUS,
                                -t.get("punctured_lung.oxygen-penalty", 5.0)),
                        AttributeContribution.scale(Attribute.MOVEMENT_SPEED,
                                -t.get("punctured_lung.speed-penalty", 0.10))));
    }

    /** Numb hands: you can still fight and dig, just badly. */
    private static Injury frostbite(TraitTuning tuning) {
        return new SimpleInjury("frostbite",
                Set.of(DamageCategory.FROST), InjurySeverity.INJURY, tuning, 240,
                (t, player) -> List.of(
                        AttributeContribution.add(Attribute.ATTACK_DAMAGE,
                                -t.get("frostbite.attack-penalty", 1.5)),
                        AttributeContribution.scale(Attribute.BLOCK_BREAK_SPEED,
                                -t.get("frostbite.break-speed-penalty", 0.25))));
    }

    /** Whatever got into you is still in you. */
    private static Injury poisonedBlood(TraitTuning tuning) {
        return new SimpleInjury("poisoned_blood",
                Set.of(DamageCategory.WITHERING), InjurySeverity.INJURY, tuning, 300,
                (t, player) -> List.of(
                        AttributeContribution.add(Attribute.ATTACK_DAMAGE,
                                -t.get("poisoned_blood.attack-penalty", 1.5)),
                        AttributeContribution.scale(Attribute.MOVEMENT_SPEED,
                                -t.get("poisoned_blood.speed-penalty", 0.10))));
    }

    /**
     * The generic answer to harm with no name of its own - drowning, starving, the void.
     *
     * <p>Deliberately unglamorous and deliberately present. Without a wound behind
     * {@link DamageCategory#OTHER} the whole tail of Minecraft damage causes would classify
     * and then inflict nothing, and every cause added in a future version would join them.
     */
    private static Injury shock(TraitTuning tuning) {
        return new SimpleInjury("shock",
                Set.of(DamageCategory.OTHER), InjurySeverity.INJURY, tuning, 180,
                (t, player) -> List.of(
                        AttributeContribution.add(Attribute.ATTACK_DAMAGE,
                                -t.get("shock.attack-penalty", 1.0)),
                        AttributeContribution.scale(Attribute.MOVEMENT_SPEED,
                                -t.get("shock.speed-penalty", 0.10))));
    }

    // -------------------------------------------------------------------- mortal wounds

    private static Injury lostArm(TraitTuning tuning) {
        return new SimpleInjury("lost_arm",
                Set.of(DamageCategory.SLASHING), InjurySeverity.MORTAL_WOUND, tuning, 0,
                (t, player) -> List.of(
                        AttributeContribution.add(Attribute.ATTACK_DAMAGE,
                                -t.get("lost_arm.attack-penalty", 3.0)),
                        AttributeContribution.scale(Attribute.BLOCK_BREAK_SPEED,
                                -t.get("lost_arm.break-speed-penalty", 0.40))));
    }

    /**
     * Something went through you and the hole never closed.
     *
     * <p>The wound piercing damage did not have. Before this, a mortal-severity arrow or
     * trident was classified as maiming, found nothing in the roster, and left no mark at all.
     */
    private static Injury impaled(TraitTuning tuning) {
        return new SimpleInjury("impaled",
                Set.of(DamageCategory.PIERCING), InjurySeverity.MORTAL_WOUND, tuning, 0,
                (t, player) -> List.of(
                        AttributeContribution.add(Attribute.ATTACK_DAMAGE,
                                -t.get("impaled.attack-penalty", 2.0)),
                        AttributeContribution.scale(Attribute.MOVEMENT_SPEED,
                                -t.get("impaled.speed-penalty", 0.15))));
    }

    private static Injury brokenLegs(TraitTuning tuning) {
        return new SimpleInjury("broken_legs",
                Set.of(DamageCategory.FALLING, DamageCategory.CRUSHING),
                InjurySeverity.MORTAL_WOUND, tuning, 0,
                (t, player) -> List.of(
                        AttributeContribution.scale(Attribute.MOVEMENT_SPEED,
                                -t.get("broken_legs.speed-penalty", 0.45)),
                        AttributeContribution.scale(Attribute.JUMP_STRENGTH,
                                -t.get("broken_legs.jump-penalty", 0.50))));
    }

    private static Injury charred(TraitTuning tuning) {
        return new SimpleInjury("charred",
                Set.of(DamageCategory.BURNING, DamageCategory.EXPLOSIVE),
                InjurySeverity.MORTAL_WOUND, tuning, 0,
                (t, player) -> List.of(AttributeContribution.add(Attribute.MAX_HEALTH,
                        -t.get("charred.health-penalty", 8.0))));
    }

    /** The cold got into the bone and stayed there. */
    private static Injury frozenMarrow(TraitTuning tuning) {
        return new SimpleInjury("frozen_marrow",
                Set.of(DamageCategory.FROST), InjurySeverity.MORTAL_WOUND, tuning, 0,
                (t, player) -> List.of(
                        AttributeContribution.scale(Attribute.MOVEMENT_SPEED,
                                -t.get("frozen_marrow.speed-penalty", 0.25)),
                        AttributeContribution.add(Attribute.ATTACK_DAMAGE,
                                -t.get("frozen_marrow.attack-penalty", 2.0))));
    }

    /** It is not healing. It is spreading. */
    private static Injury rottingWound(TraitTuning tuning) {
        return new SimpleInjury("rotting_wound",
                Set.of(DamageCategory.WITHERING), InjurySeverity.MORTAL_WOUND, tuning, 0,
                (t, player) -> List.of(AttributeContribution.add(Attribute.MAX_HEALTH,
                        -t.get("rotting_wound.health-penalty", 4.0))));
    }

    /** Something inside stopped working properly and never started again. */
    private static Injury failingBody(TraitTuning tuning) {
        return new SimpleInjury("failing_body",
                Set.of(DamageCategory.OTHER), InjurySeverity.MORTAL_WOUND, tuning, 0,
                (t, player) -> List.of(
                        AttributeContribution.add(Attribute.MAX_HEALTH,
                                -t.get("failing_body.health-penalty", 4.0)),
                        AttributeContribution.add(Attribute.ATTACK_DAMAGE,
                                -t.get("failing_body.attack-penalty", 1.0))));
    }

    /**
     * A wound that is one or more attribute penalties and nothing else.
     *
     * @param defaultDuration seconds it lasts, before config; 0 for a mortal wound
     */
    private record SimpleInjury(String id,
                                Set<DamageCategory> causes,
                                InjurySeverity severity,
                                TraitTuning tuning,
                                long defaultDuration,
                                BiFunction<TraitTuning, Player, List<AttributeContribution>> penalties)
            implements Injury, AttributeSource {

        private SimpleInjury {
            Objects.requireNonNull(tuning, "tuning");
            causes = Set.copyOf(causes);
        }

        @Override
        public long durationSeconds() {
            return (long) tuning.get(id + ".duration-seconds", defaultDuration);
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            return penalties.apply(tuning, player);
        }
    }

    /**
     * Bleeding: the one wound that is not a penalty but a clock.
     *
     * <p>It does not weaken you, it drains you, and it will not kill you on its own - the
     * damage stops at the last half-heart. A wound that could finish someone who had already
     * escaped a fight would make ordinary injuries as decisive as mortal ones.
     *
     * <p>The damage goes through {@link WoundDamage} rather than {@code setHealth}, so it
     * flashes, makes a sound and plays the hurt animation the way poison does. Silently
     * subtracting health was the reason players said bleeding did nothing: there was no
     * moment to notice, only a smaller number later on.
     */
    public static final class Bleeding implements Injury, Ticking {

        /** Shared-loop intervals between drops of blood. Four is two seconds. */
        private static final int INTERVALS_BETWEEN_TICKS = 4;

        private final TraitTuning tuning;
        private final java.util.Map<java.util.UUID, Integer> sinceLastTick =
                new java.util.concurrent.ConcurrentHashMap<>();

        Bleeding(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "bleeding";
        }

        @Override
        public Set<DamageCategory> causes() {
            return Set.of(DamageCategory.SLASHING, DamageCategory.PIERCING);
        }

        @Override
        public InjurySeverity severity() {
            return InjurySeverity.INJURY;
        }

        @Override
        public long durationSeconds() {
            return (long) tuning.get("bleeding.duration-seconds", 180);
        }

        @Override
        public void tick(Player player) {
            int waited = sinceLastTick.merge(player.getUniqueId(), 1, Integer::sum);
            if (waited < INTERVALS_BETWEEN_TICKS) {
                return;
            }
            sinceLastTick.put(player.getUniqueId(), 0);

            double drain = tuning.get("bleeding.damage-per-tick", 1.0);
            double floor = tuning.get("bleeding.stops-at-health", 4.0);
            if (!WoundDamage.inflict(player, drain, floor)) {
                return;
            }
            player.getWorld().spawnParticle(Particle.DUST,
                    player.getLocation().add(0, 1.0, 0), 4, 0.2, 0.4, 0.2,
                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(140, 20, 20), 1.0f));
        }

        @Override
        public void onDetach(Player player) {
            sinceLastTick.remove(player.getUniqueId());
        }
    }
}
