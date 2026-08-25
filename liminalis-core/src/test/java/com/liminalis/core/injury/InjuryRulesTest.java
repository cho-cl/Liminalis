package com.liminalis.core.injury;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Large damage wounds you; massive damage maims you.
 *
 * <p>Severity is judged on damage <em>after</em> armour, as a fraction of the player's own
 * maximum health. Both halves of that matter. Post-armour is what makes the brief's example
 * work - a netherite sword through weak armour costs you an arm, and the same blow through
 * full protection does not. As a fraction of max health is what keeps it fair for a player
 * carrying Ironblood, whose thirteen hearts should not make them harder to injure in
 * proportion to what they can survive.
 */
class InjuryRulesTest {

    private static final double MAX_HEALTH = 20.0;

    /** Injury above 25% of max health at 40%; mortal above 60% at 35%. */
    private static final InjurySettings SETTINGS = new InjurySettings(0.25, 0.40, 0.60, 0.35, 1, 10.0);

    /** Chances forced to certainty, so threshold behaviour can be tested on its own. */
    private static final InjurySettings ALWAYS = new InjurySettings(0.25, 1.0, 0.60, 1.0, 1, 10.0);

    private static DamageDescriptor blow(double damage) {
        return new DamageDescriptor(DamageCategory.SLASHING, damage, MAX_HEALTH);
    }

    @Test
    void aGlancingBlowDoesNoLastingHarm() {
        // 4 damage out of 20 is under the injury threshold entirely.
        assertThat(InjuryRules.classify(blow(4.0), ALWAYS, new Random(1)))
                .isEqualTo(InjurySeverity.NONE);
    }

    @Test
    void aLargeBlowCanInjure() {
        assertThat(InjuryRules.classify(blow(8.0), ALWAYS, new Random(1)))
                .isEqualTo(InjurySeverity.INJURY);
    }

    @Test
    void aMassiveBlowCanMaim() {
        assertThat(InjuryRules.classify(blow(15.0), ALWAYS, new Random(1)))
                .isEqualTo(InjurySeverity.MORTAL_WOUND);
    }

    @Test
    void theThresholdsAreInclusive() {
        // Exactly on the line counts. Otherwise the configured number is not the threshold.
        assertThat(InjuryRules.classify(blow(5.0), ALWAYS, new Random(1)))
                .isEqualTo(InjurySeverity.INJURY);
        assertThat(InjuryRules.classify(blow(12.0), ALWAYS, new Random(1)))
                .isEqualTo(InjurySeverity.MORTAL_WOUND);
    }

    @Test
    void aBigHitOnSomeoneWithMoreHeartsIsJudgedInProportion() {
        // 8 damage maims a 20-health player but merely injures one blessed with 32.
        DamageDescriptor onTheBlessed = new DamageDescriptor(DamageCategory.SLASHING, 8.0, 32.0);

        assertThat(InjuryRules.classify(onTheBlessed, ALWAYS, new Random(1)))
                .isEqualTo(InjurySeverity.INJURY);
    }

    @Test
    void chanceIsRespectedForInjuries() {
        assertThat(rateOf(InjurySeverity.INJURY, blow(8.0), SETTINGS)).isBetween(0.38, 0.42);
    }

    @Test
    void chanceIsRespectedForMortalWounds() {
        assertThat(rateOf(InjurySeverity.MORTAL_WOUND, blow(15.0), SETTINGS))
                .isBetween(0.33, 0.37);
    }

    @Test
    void aMassiveBlowThatDoesNotMaimStillCountsAsALargeOne() {
        // Otherwise the most violent hits in the game would be the least likely to leave a
        // mark, because failing the mortal roll would mean nothing happened at all.
        InjurySettings mortalNeverInjuryAlways = new InjurySettings(0.25, 1.0, 0.60, 0.0, 1, 10.0);

        assertThat(InjuryRules.classify(blow(15.0), mortalNeverInjuryAlways, new Random(1)))
                .isEqualTo(InjurySeverity.INJURY);
    }

    @Test
    void zeroChanceMeansNothingEverHappens() {
        InjurySettings never = new InjurySettings(0.25, 0.0, 0.60, 0.0, 1, 10.0);

        assertThat(rateOf(InjurySeverity.NONE, blow(18.0), never)).isEqualTo(1.0);
    }

    @Test
    void damageIsIgnoredWhenMaxHealthIsNonsense() {
        // Guards against a divide-by-zero turning every scratch into a lost arm.
        DamageDescriptor broken = new DamageDescriptor(DamageCategory.SLASHING, 5.0, 0.0);

        assertThat(InjuryRules.classify(broken, ALWAYS, new Random(1)))
                .isEqualTo(InjurySeverity.NONE);
    }

    @Test
    void negativeDamageNeverInjures() {
        assertThat(InjuryRules.classify(blow(-5.0), ALWAYS, new Random(1)))
                .isEqualTo(InjurySeverity.NONE);
    }

    @Test
    void theSameSeedAlwaysGivesTheSameVerdict() {
        assertThat(InjuryRules.classify(blow(9.0), SETTINGS, new Random(55)))
                .isEqualTo(InjuryRules.classify(blow(9.0), SETTINGS, new Random(55)));
    }

    private static double rateOf(InjurySeverity severity,
                                 DamageDescriptor damage,
                                 InjurySettings settings) {
        Random random = new Random(17);
        int hits = 0;
        int iterations = 200_000;
        for (int i = 0; i < iterations; i++) {
            if (InjuryRules.classify(damage, settings, random) == severity) {
                hits++;
            }
        }
        return hits / (double) iterations;
    }
}
