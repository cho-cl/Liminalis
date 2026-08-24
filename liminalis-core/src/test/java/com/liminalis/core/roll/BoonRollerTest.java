package com.liminalis.core.roll;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Some are blessed, some are cursed, and most are neither.
 *
 * <p>The two chances have to be independent of each other and add up to exactly what the
 * config says. The obvious implementation - roll for a blessing, then roll for a curse if
 * that missed - quietly makes the curse rarer than its number, because it only ever gets
 * rolled on the 85% of players who were not blessed.
 */
class BoonRollerTest {

    private static final int ITERATIONS = 200_000;

    private static final List<WeightedEntry> BLESSINGS = List.of(
            new WeightedEntry("ironblood", 1.0),
            new WeightedEntry("far_wanderer", 1.0));

    private static final List<WeightedEntry> CURSES = List.of(
            new WeightedEntry("hollow", 1.0),
            new WeightedEntry("brittle", 1.0));

    private static final BoonRollSettings FIFTEEN_EACH = new BoonRollSettings(0.15, 0.15);

    private static BoonRoller roller() {
        return new BoonRoller(BLESSINGS, CURSES);
    }

    @Test
    void aboutFifteenPercentAreBlessed() {
        assertThat(rateOf(BoonKind.BLESSING, FIFTEEN_EACH)).isBetween(0.145, 0.155);
    }

    @Test
    void aboutFifteenPercentAreCursed() {
        // The number that the naive implementation gets wrong: rolling the curse only after
        // the blessing misses would land this near 0.1275 instead.
        assertThat(rateOf(BoonKind.CURSE, FIFTEEN_EACH)).isBetween(0.145, 0.155);
    }

    @Test
    void aboutSeventyPercentAreNeither() {
        assertThat(rateOf(BoonKind.NONE, FIFTEEN_EACH)).isBetween(0.695, 0.705);
    }

    @Test
    void nobodyIsEverBothBlessedAndCursed() {
        Random random = new Random(5);
        for (int i = 0; i < 10_000; i++) {
            BoonOutcome outcome = roller().roll(FIFTEEN_EACH, random);
            if (outcome.kind() == BoonKind.NONE) {
                assertThat(outcome.id()).isNull();
            } else {
                assertThat(outcome.id()).isNotNull();
            }
        }
    }

    @Test
    void blessingsComeFromTheBlessingPoolAndCursesFromTheCursePool() {
        Random random = new Random(9);
        for (int i = 0; i < 10_000; i++) {
            BoonOutcome outcome = roller().roll(FIFTEEN_EACH, random);
            switch (outcome.kind()) {
                case BLESSING -> assertThat(outcome.id()).isIn("ironblood", "far_wanderer");
                case CURSE -> assertThat(outcome.id()).isIn("hollow", "brittle");
                case NONE -> assertThat(outcome.id()).isNull();
            }
        }
    }

    @Test
    void certaintyMeansEverybodyIsBlessed() {
        Random random = new Random(2);
        for (int i = 0; i < 500; i++) {
            assertThat(roller().roll(new BoonRollSettings(1.0, 0.0), random).kind())
                    .isEqualTo(BoonKind.BLESSING);
        }
    }

    @Test
    void zeroChanceMeansNobodyIsTouched() {
        Random random = new Random(2);
        for (int i = 0; i < 500; i++) {
            assertThat(roller().roll(new BoonRollSettings(0.0, 0.0), random).kind())
                    .isEqualTo(BoonKind.NONE);
        }
    }

    @Test
    void anEmptyPoolYieldsNothingRatherThanThrowing() {
        // A build with curses configured but none registered must not stop players joining.
        BoonRoller noCurses = new BoonRoller(BLESSINGS, List.of());

        Random random = new Random(4);
        for (int i = 0; i < 5_000; i++) {
            assertThat(noCurses.roll(new BoonRollSettings(0.0, 1.0), random).kind())
                    .isEqualTo(BoonKind.NONE);
        }
    }

    @Test
    void heavierWeightsAreDrawnMoreOften() {
        BoonRoller weighted = new BoonRoller(
                List.of(new WeightedEntry("common", 9.0), new WeightedEntry("rare", 1.0)),
                CURSES);
        Random random = new Random(6);

        int common = 0;
        int blessed = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            BoonOutcome outcome = weighted.roll(new BoonRollSettings(1.0, 0.0), random);
            if (outcome.kind() == BoonKind.BLESSING) {
                blessed++;
                if ("common".equals(outcome.id())) {
                    common++;
                }
            }
        }
        assertThat(common / (double) blessed).isBetween(0.88, 0.92);
    }

    @Test
    void theSameSeedAlwaysProducesTheSameOutcome() {
        assertThat(roller().roll(FIFTEEN_EACH, new Random(77)))
                .isEqualTo(roller().roll(FIFTEEN_EACH, new Random(77)));
    }

    private static double rateOf(BoonKind kind, BoonRollSettings settings) {
        Random random = new Random(31);
        int hits = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            if (roller().roll(settings, random).kind() == kind) {
                hits++;
            }
        }
        return hits / (double) ITERATIONS;
    }
}
