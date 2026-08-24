package com.liminalis.core.roll;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The roll that decides who a player is, run once and never again.
 *
 * <p>Tested statistically rather than by example, because the thing that can go wrong is not
 * a crash - it is a distribution that is quietly nothing like the one in the config. A
 * Singularity trait meant to be rare showing up for one player in three would look like
 * working code right up until the server felt wrong for reasons nobody could name.
 */
class TraitRollerTest {

    private static final int ITERATIONS = 100_000;

    private static final List<RollCandidate> POOL = List.of(
            new RollCandidate("short", TraitTier.ORDINARY, 1.0),
            new RollCandidate("swift_hands", TraitTier.ORDINARY, 1.0),
            new RollCandidate("ironbound", TraitTier.ORDINARY, 1.0),
            new RollCandidate("resilience", TraitTier.ORDINARY, 1.0),
            new RollCandidate("deathsight", TraitTier.SINGULARITY, 1.0),
            new RollCandidate("stillness", TraitTier.SINGULARITY, 1.0));

    private static final TraitRollSettings SETTINGS = new TraitRollSettings(0.25, 0.03);

    private static TraitRoller roller() {
        return new TraitRoller(POOL);
    }

    @Test
    void everybodyGetsAtLeastOneTrait() {
        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            assertThat(roller().roll(SETTINGS, random)).isNotEmpty();
        }
    }

    @Test
    void nobodyGetsMoreThanTwo() {
        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            assertThat(roller().roll(SETTINGS, random)).hasSizeLessThanOrEqualTo(2);
        }
    }

    @Test
    void aboutAQuarterOfPlayersGetASecondTrait() {
        Random random = new Random(42);
        int withTwo = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            if (roller().roll(SETTINGS, random).size() == 2) {
                withTwo++;
            }
        }
        double rate = withTwo / (double) ITERATIONS;
        assertThat(rate).isBetween(0.24, 0.26);
    }

    @Test
    void singularityTraitsStayRare() {
        Random random = new Random(42);
        int withSingularity = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            if (roller().roll(SETTINGS, random).stream().anyMatch(TraitRollerTest::isSingularity)) {
                withSingularity++;
            }
        }
        // Two slots at 3% each, so a little under 6% of players see one at all.
        double rate = withSingularity / (double) ITERATIONS;
        assertThat(rate).isBetween(0.03, 0.05);
    }

    @Test
    void nobodyEverRollsTheSameTraitTwice() {
        Random random = new Random(7);
        for (int i = 0; i < ITERATIONS; i++) {
            List<String> rolled = roller().roll(SETTINGS, random);
            assertThat(Set.copyOf(rolled)).hasSameSizeAs(rolled);
        }
    }

    @Test
    void heavierWeightsAreDrawnMoreOften() {
        List<RollCandidate> weighted = List.of(
                new RollCandidate("common", TraitTier.ORDINARY, 9.0),
                new RollCandidate("rare", TraitTier.ORDINARY, 1.0));
        TraitRoller roller = new TraitRoller(weighted);
        Random random = new Random(3);

        int commonCount = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            if (roller.roll(new TraitRollSettings(0.0, 0.0), random).contains("common")) {
                commonCount++;
            }
        }
        assertThat(commonCount / (double) ITERATIONS).isBetween(0.88, 0.92);
    }

    @Test
    void neverOffersASingularityTraitAsAnOrdinaryOne() {
        // Otherwise the rare tier would also be reachable through the common roll, and the
        // configured rarity would be a fiction.
        Random random = new Random(11);
        TraitRollSettings never = new TraitRollSettings(1.0, 0.0);

        for (int i = 0; i < 10_000; i++) {
            assertThat(roller().roll(never, random)).noneMatch(TraitRollerTest::isSingularity);
        }
    }

    @Test
    void alwaysSingularityWhenTheChanceIsCertain() {
        Random random = new Random(11);
        TraitRollSettings always = new TraitRollSettings(0.0, 1.0);

        for (int i = 0; i < 1000; i++) {
            assertThat(roller().roll(always, random)).allMatch(TraitRollerTest::isSingularity);
        }
    }

    @Test
    void anEmptyPoolRollsNothingRatherThanThrowing() {
        // A build with no traits registered must not stop players logging in.
        TraitRoller empty = new TraitRoller(List.of());

        assertThat(empty.roll(SETTINGS, new Random(1))).isEmpty();
    }

    @Test
    void fallsBackToTheOrdinaryPoolWhenNoSingularityTraitsExist() {
        TraitRoller ordinaryOnly = new TraitRoller(List.of(
                new RollCandidate("short", TraitTier.ORDINARY, 1.0)));

        assertThat(ordinaryOnly.roll(new TraitRollSettings(0.0, 1.0), new Random(1)))
                .containsExactly("short");
    }

    @Test
    void stopsEarlyRatherThanRepeatingWhenThePoolIsExhausted() {
        TraitRoller single = new TraitRoller(List.of(
                new RollCandidate("short", TraitTier.ORDINARY, 1.0)));

        assertThat(single.roll(new TraitRollSettings(1.0, 0.0), new Random(1)))
                .containsExactly("short");
    }

    @Test
    void theSameSeedAlwaysProducesTheSameRoll() {
        assertThat(roller().roll(SETTINGS, new Random(99)))
                .isEqualTo(roller().roll(SETTINGS, new Random(99)));
    }

    private static boolean isSingularity(String id) {
        return POOL.stream()
                .anyMatch(c -> c.id().equals(id) && c.tier() == TraitTier.SINGULARITY);
    }
}
