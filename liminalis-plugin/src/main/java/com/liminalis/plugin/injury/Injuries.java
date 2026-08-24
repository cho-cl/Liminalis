package com.liminalis.plugin.injury;

import com.liminalis.core.injury.DamageCategory;
import com.liminalis.core.injury.InjurySeverity;
import com.liminalis.plugin.modifier.capability.AttributeContribution;
import com.liminalis.plugin.modifier.capability.AttributeSource;
import com.liminalis.plugin.modifier.capability.Ticking;
import com.liminalis.plugin.trait.TraitTuning;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
 */
public final class Injuries {

    private Injuries() {
    }

    public static List<Injury> all(TraitTuning tuning) {
        return List.of(
                new Bleeding(tuning),
                sprainedAnkle(tuning),
                burns(tuning),
                concussion(tuning),
                puncturedLung(tuning),
                lostArm(tuning),
                brokenLegs(tuning),
                charred(tuning));
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
                        -safeHealthPenalty(player, t.get("burns.health-penalty", 4.0)))));
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
                        -safeHealthPenalty(player, t.get("charred.health-penalty", 8.0)))));
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

            double drain = tuning.get("bleeding.damage-per-tick", 0.5);
            double floor = tuning.get("bleeding.stops-at-health", 1.0);
            if (player.getHealth() - drain <= floor) {
                return;
            }
            player.setHealth(Math.max(floor, player.getHealth() - drain));
            player.getWorld().spawnParticle(Particle.DUST,
                    player.getLocation().add(0, 1.0, 0), 4, 0.2, 0.4, 0.2,
                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(140, 20, 20), 1.0f));
        }

        @Override
        public void onDetach(Player player) {
            sinceLastTick.remove(player.getUniqueId());
        }
    }

    /**
     * Caps a max-health penalty so it can never take a player below a single heart.
     *
     * <p>Not theoretical: Burns and Charred can both be carried at once, and a large enough
     * combined penalty would drive maximum health to zero and kill the player outright the
     * instant the wound landed - which would look exactly like the plugin murdering them.
     */
    static double safeHealthPenalty(Player player, double penalty) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double base = maxHealth == null ? 20.0 : maxHealth.getBaseValue();
        return Math.max(0.0, Math.min(penalty, base - 2.0));
    }
}
