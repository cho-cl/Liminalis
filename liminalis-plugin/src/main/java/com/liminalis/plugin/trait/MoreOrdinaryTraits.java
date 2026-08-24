package com.liminalis.plugin.trait;

import com.liminalis.core.roll.TraitTier;
import com.liminalis.plugin.modifier.capability.AttributeContribution;
import com.liminalis.plugin.modifier.capability.AttributeSource;
import com.liminalis.plugin.modifier.capability.DynamicAttributeSource;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * The second half of the ordinary roster.
 *
 * <p>Split from {@link OrdinaryTraits} to keep either file readable rather than because the
 * two are different in kind. Between them the ordinary pool is fourteen, which is enough that
 * two players comparing notes rarely have the same one.
 *
 * <p>Dawnbound and Duskbound are the interesting pair. They use the same
 * {@code DynamicAttributeSource} primitive as Resilience and Coward, but driven by the time of
 * day rather than by health - which is the clearest evidence that abstraction was worth
 * building. Nothing had to change to support a completely different input.
 */
public final class MoreOrdinaryTraits {

    private MoreOrdinaryTraits() {
    }

    public static List<Trait> all(TraitTuning tuning) {
        return List.of(
                new Simple("tall", tuning, (t, player) -> List.of(
                        AttributeContribution.scale(Attribute.SCALE,
                                t.get("tall.scale", 0.12)))),

                new Simple("fleet", tuning, (t, player) -> List.of(
                        AttributeContribution.scale(Attribute.MOVEMENT_SPEED,
                                t.get("fleet.speed", 0.08)))),

                new Simple("sure_footed", tuning, (t, player) -> List.of(
                        AttributeContribution.add(Attribute.SAFE_FALL_DISTANCE,
                                t.get("sure_footed.safe-fall", 6.0)))),

                new Simple("heavy", tuning, (t, player) -> List.of(
                        AttributeContribution.add(Attribute.KNOCKBACK_RESISTANCE,
                                t.get("heavy.knockback-resistance", 0.35)))),

                new Simple("warm_blooded", tuning, (t, player) -> List.of(
                        AttributeContribution.scale(Attribute.BURNING_TIME,
                                -t.get("warm_blooded.burning-time", 0.50)))),

                new Simple("waterborne", tuning, (t, player) -> List.of(
                        AttributeContribution.add(Attribute.WATER_MOVEMENT_EFFICIENCY,
                                t.get("waterborne.movement", 0.5)),
                        AttributeContribution.add(Attribute.SUBMERGED_MINING_SPEED,
                                t.get("waterborne.mining", 0.4)))),

                new Dawnbound(tuning),
                new Duskbound(tuning));
    }

    // -------------------------------------------------------------------- time of day

    /**
     * Stronger while the sun is up.
     *
     * <p>Reads the world clock rather than the player's health, which is the point: the
     * primitive was built for Resilience and works here unchanged. Someone with this wants
     * the fight over before dusk, and will genuinely plan their day around it.
     */
    public static final class Dawnbound implements Trait, DynamicAttributeSource {

        private final TraitTuning tuning;

        Dawnbound(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "dawnbound";
        }

        @Override
        public TraitTier tier() {
            return TraitTier.ORDINARY;
        }

        @Override
        public double weight() {
            return 0.7;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            double bonus = tuning.get("dawnbound.max-attack", 2.5);
            return List.of(AttributeContribution.add(
                    Attribute.ATTACK_DAMAGE, bonus * daylightFraction(player.getWorld())));
        }
    }

    /** The inverse, and the same code path. Stronger once the light is gone. */
    public static final class Duskbound implements Trait, DynamicAttributeSource {

        private final TraitTuning tuning;

        Duskbound(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "duskbound";
        }

        @Override
        public TraitTier tier() {
            return TraitTier.ORDINARY;
        }

        @Override
        public double weight() {
            return 0.7;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            double bonus = tuning.get("duskbound.max-attack", 2.5);
            return List.of(AttributeContribution.add(
                    Attribute.ATTACK_DAMAGE, bonus * (1.0 - daylightFraction(player.getWorld()))));
        }
    }

    /**
     * How much daylight there is, from 0 at midnight to 1 at noon.
     *
     * <p>Worked out from the world clock rather than from block light, so standing in a cave
     * at noon does not rob a Dawnbound player of what they built their day around.
     */
    static double daylightFraction(World world) {
        long time = world.getTime() % 24_000L;
        // Day runs 0..12000 with noon at 6000; night runs 12000..24000.
        double angle = (time / 24_000.0) * 2 * Math.PI;
        // cos peaks at time 0 (sunrise-ish); shift so the peak lands at noon.
        double value = Math.cos(angle - Math.PI / 2.0);
        return Math.max(0.0, Math.min(1.0, (value + 1.0) / 2.0));
    }

    /** A trait that is one or more attribute changes and nothing else. */
    private record Simple(String id,
                          TraitTuning tuning,
                          BiFunction<TraitTuning, Player, List<AttributeContribution>> shape)
            implements Trait, AttributeSource {

        private Simple {
            Objects.requireNonNull(tuning, "tuning");
            Objects.requireNonNull(shape, "shape");
        }

        @Override
        public TraitTier tier() {
            return TraitTier.ORDINARY;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            // Read at the point of use, so /liminalis reload rebalances a live server.
            return shape.apply(tuning, player);
        }
    }
}
