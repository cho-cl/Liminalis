package com.liminalis.plugin.trait;

import com.liminalis.core.roll.TraitTier;
import com.liminalis.plugin.modifier.capability.AttributeContribution;
import com.liminalis.plugin.modifier.capability.AttributeSource;
import com.liminalis.plugin.modifier.capability.DynamicAttributeSource;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * The everyday roster.
 *
 * <p>Deliberately spans both scales the brief described: some are small enough that a player
 * might take a while to notice, and some change how you fight.
 *
 * <p>The two dynamic ones are worth reading together. Resilience and Coward look like
 * opposites and are in fact the same primitive with different parameters - a stat scaled
 * along a curve by current health. Building {@code DynamicAttributeSource} once bought both,
 * and buys most of the traits that have not been thought of yet.
 *
 * <p>Every number is read from {@link TraitTuning} at the point of use rather than captured
 * in a constructor, so {@code /liminalis reload} genuinely rebalances a live server.
 */
public final class OrdinaryTraits {

    private OrdinaryTraits() {
    }

    public static List<Trait> all(TraitTuning tuning) {
        return List.of(
                new Short(tuning),
                new SwiftHands(tuning),
                new Ironbound(tuning),
                new DeepLungs(tuning),
                new Resilience(tuning),
                new Coward(tuning));
    }

    /** Smaller than everyone else, which in 1.21 also means a slightly shorter reach. */
    public static final class Short implements Trait, AttributeSource {

        private final TraitTuning tuning;

        Short(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "short";
        }

        @Override
        public TraitTier tier() {
            return TraitTier.ORDINARY;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            return List.of(AttributeContribution.scale(
                    Attribute.SCALE, tuning.get("short.scale", -0.10)));
        }
    }

    /** Mines noticeably faster than anyone else. */
    public static final class SwiftHands implements Trait, AttributeSource {

        private final TraitTuning tuning;

        SwiftHands(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "swift_hands";
        }

        @Override
        public TraitTier tier() {
            return TraitTier.ORDINARY;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            return List.of(AttributeContribution.scale(
                    Attribute.BLOCK_BREAK_SPEED, tuning.get("swift_hands.break-speed", 0.30)));
        }
    }

    /** Naturally tougher. A full armour bar's worth, always. */
    public static final class Ironbound implements Trait, AttributeSource {

        private final TraitTuning tuning;

        Ironbound(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "ironbound";
        }

        @Override
        public TraitTier tier() {
            return TraitTier.ORDINARY;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            return List.of(AttributeContribution.add(
                    Attribute.ARMOR, tuning.get("ironbound.armor", 2.0)));
        }
    }

    /** Holds their breath far longer than they should be able to. */
    public static final class DeepLungs implements Trait, AttributeSource {

        private final TraitTuning tuning;

        DeepLungs(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "deep_lungs";
        }

        @Override
        public TraitTier tier() {
            return TraitTier.ORDINARY;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            return List.of(AttributeContribution.add(
                    Attribute.OXYGEN_BONUS, tuning.get("deep_lungs.oxygen", 4.0)));
        }
    }

    /**
     * Grows harder to kill the closer they are to dying.
     *
     * <p>Armour rises smoothly from nothing at full health to its maximum at the edge of
     * death, so it does the most for someone who has already lost a fight and is trying to
     * get away.
     */
    public static final class Resilience implements Trait, DynamicAttributeSource {

        private final TraitTuning tuning;

        Resilience(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "resilience";
        }

        @Override
        public TraitTier tier() {
            return TraitTier.ORDINARY;
        }

        @Override
        public double weight() {
            // Rarer than the small quirks: this one genuinely changes a fight.
            return 0.6;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            double missing = 1.0 - healthFraction(player);
            double maxBonus = tuning.get("resilience.max-armor", 8.0);
            return List.of(AttributeContribution.add(Attribute.ARMOR, maxBonus * missing));
        }
    }

    /**
     * Dangerous while untouched, and less so with every hit they take.
     *
     * <p>The exact inverse of Resilience, and the same code path. Someone with this wants the
     * first strike and wants the fight over quickly.
     */
    public static final class Coward implements Trait, DynamicAttributeSource {

        private final TraitTuning tuning;

        Coward(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "coward";
        }

        @Override
        public TraitTier tier() {
            return TraitTier.ORDINARY;
        }

        @Override
        public double weight() {
            return 0.6;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            double maxBonus = tuning.get("coward.max-bonus-damage", 3.0);
            double maxPenalty = tuning.get("coward.max-penalty-damage", 2.0);

            // +maxBonus at full health, sliding to -maxPenalty at the edge of death.
            double fraction = healthFraction(player);
            double amount = (maxBonus * fraction) - (maxPenalty * (1.0 - fraction));
            return List.of(AttributeContribution.add(Attribute.ATTACK_DAMAGE, amount));
        }
    }

    /**
     * Current health as a fraction of maximum, clamped to 0..1.
     *
     * <p>Reads max health from the attribute rather than assuming 20, so it stays correct for
     * anyone carrying a blessing that adds hearts.
     */
    static double healthFraction(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        if (max <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, player.getHealth() / max));
    }
}
