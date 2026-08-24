package com.liminalis.core.injury;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the shipped tuning actually does to a player, stated in blows rather than in fractions.
 *
 * <p>{@link InjuryRulesTest} proves the rule is correct against settings it makes up.
 * This proves the settings we <em>ship</em> produce the game we said we wanted, which is a
 * different question and the one that was actually wrong: the old numbers were arithmetically
 * fine and meant that a player in decent armour was essentially never wounded by anything.
 *
 * <p>Each case is a real hit with the damage a player would really take from it, so a change
 * to the defaults fails with the name of the blow that changed rather than a bare number.
 */
class InjuryTuningTest {

    private static final InjurySettings SHIPPED = InjurySettings.DEFAULTS;
    private static final double NORMAL_HEALTH = 20.0;
    private static final int ITERATIONS = 200_000;

    // ------------------------------------------------------------------ wounds are common

    @Test
    void aSolidMobHitOnAnUnarmouredPlayerUsuallyWounds() {
        // A zombie or a skeleton's arrow: 3 damage of the 20 you have.
        assertThat(woundRate(3.0)).isGreaterThan(0.60);
    }

    @Test
    void aShortFallWounds() {
        // Six blocks: 3 damage. You should limp away from this more often than not.
        assertThat(woundRate(3.0)).isGreaterThan(0.60);
    }

    @Test
    void aCreeperAtRangeWounds() {
        assertThat(woundRate(9.0)).isGreaterThan(0.65);
    }

    @Test
    void aRealFightInIronArmourStillLeavesMarks() {
        // Full iron takes roughly 60% off, so a 7-damage swing lands as under 3. The old
        // tuning needed 5 through armour to wound at all, which is why armoured players
        // reported that injuries "just never happen".
        assertThat(woundRate(2.8)).isGreaterThan(0.60);
    }

    // --------------------------------------------------------------- scratches still are

    @Test
    void aScratchLeavesNothing() {
        // One damage: a cactus brush, a tick of fire, a fall off a fence.
        assertThat(woundRate(1.0)).isZero();
    }

    @Test
    void aTickOfFireCannotWound() {
        // Important beyond flavour: fire ticks every second, so if this could wound, standing
        // in a campfire would roll the injury table twenty times.
        assertThat(woundRate(1.0)).isZero();
        assertThat(woundRate(2.0)).isZero();
    }

    @Test
    void poisonAndWitherCannotWound() {
        // Both tick for 1 at a time. Damage over time must never be a wound engine.
        assertThat(woundRate(1.0)).isZero();
    }

    // ------------------------------------------------------------- maiming stays reserved

    @Test
    void nothingSurvivableFromAnOrdinaryMobCanMaim() {
        // The largest single hit a normal hostile mob deals is well under half your health.
        assertThat(mortalRate(3.0)).isZero();
        assertThat(mortalRate(5.0)).isZero();
        assertThat(mortalRate(11.0)).isZero();
    }

    @Test
    void onlyAHugeBlowCanMaim() {
        // Twelve damage through armour - a point-blank creeper, a long fall, a charged hit
        // from something that should not have been fought alone.
        assertThat(mortalRate(12.0)).isGreaterThan(0.25);
    }

    @Test
    void maimingIsTheMinorityOutcomeEvenOfAHugeBlow() {
        // A blow past the mortal line is far more likely to leave an ordinary wound than to
        // take a limb. Being maimed should be a story, not a Tuesday.
        assertThat(mortalRate(14.0)).isLessThan(woundRate(14.0));
    }

    @Test
    void theTwoThresholdsAreFarApart() {
        // The gap is the design: hurt often, maimed almost never. If these ever converge,
        // every wounding blow starts costing limbs.
        assertThat(SHIPPED.mortalThreshold()).isGreaterThan(SHIPPED.injuryThreshold() * 4);
    }

    // ------------------------------------------------------------------ extra hearts stay fair

    @Test
    void extraHeartsDoNotMakeYouEasierToWoundInProportion() {
        // A Hollow player with 26 health takes a 3-damage hit. In absolute terms it is the
        // same blow; in proportion it is smaller, and it should wound less often.
        double normal = rate(InjurySeverity.INJURY, 3.0, NORMAL_HEALTH);
        double hollow = rate(InjurySeverity.INJURY, 3.0, 26.0);

        assertThat(hollow).isLessThan(normal);
    }

    // ------------------------------------------------------------------------- helpers

    /** How often a blow of this size leaves anything at all. */
    private static double woundRate(double damage) {
        return rate(InjurySeverity.INJURY, damage, NORMAL_HEALTH)
                + rate(InjurySeverity.MORTAL_WOUND, damage, NORMAL_HEALTH);
    }

    private static double mortalRate(double damage) {
        return rate(InjurySeverity.MORTAL_WOUND, damage, NORMAL_HEALTH);
    }

    private static double rate(InjurySeverity severity, double damage, double maxHealth) {
        DamageDescriptor blow =
                new DamageDescriptor(DamageCategory.SLASHING, damage, maxHealth);
        Random random = new Random(4_071);
        int hits = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            if (InjuryRules.classify(blow, SHIPPED, random) == severity) {
                hits++;
            }
        }
        return hits / (double) ITERATIONS;
    }
}
