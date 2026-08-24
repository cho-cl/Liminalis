package com.liminalis.core.singularity;

import com.liminalis.core.roll.WeightedEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How often the Singularity reaches into the world, and what it leaves when it is driven off.
 *
 * <p>The spawn roll is per player rather than per server, which is the detail that decides
 * whether the server feels alive. Rolled once for everyone, a full server and an empty one
 * would see the same number of creatures, and the people actually playing would rarely meet
 * any.
 */
class SingularityRulesTest {

    private static final int ITERATIONS = 100_000;

    private static final List<WeightedEntry> BOOKS = List.of(
            new WeightedEntry("on_limbo", 1.0),
            new WeightedEntry("on_the_liminalis", 1.0),
            new WeightedEntry("on_blessings", 1.0),
            new WeightedEntry("on_the_singularity", 1.0),
            new WeightedEntry("on_return", 1.0));

    // ------------------------------------------------------------------------- spawning

    @Test
    void anEmptyServerSeesNothing() {
        assertThat(SingularityRules.spawnCountFor(0, 1.0, new Random(1))).isZero();
    }

    @Test
    void everyPlayerIsRolledForSeparately() {
        // At certainty, ten players means ten creatures - not one.
        assertThat(SingularityRules.spawnCountFor(10, 1.0, new Random(1))).isEqualTo(10);
    }

    @Test
    void halfChanceMeansAboutHalfTheServer() {
        Random random = new Random(31);
        long total = 0;
        int rounds = 20_000;
        for (int i = 0; i < rounds; i++) {
            total += SingularityRules.spawnCountFor(10, 0.5, random);
        }
        assertThat(total / (double) rounds).isBetween(4.9, 5.1);
    }

    @Test
    void zeroChanceMeansNothingEverComes() {
        assertThat(SingularityRules.spawnCountFor(20, 0.0, new Random(1))).isZero();
    }

    @Test
    void aNegativePlayerCountIsTreatedAsEmptyRatherThanThrowing() {
        assertThat(SingularityRules.spawnCountFor(-3, 1.0, new Random(1))).isZero();
    }

    // ---------------------------------------------------------------------------- drops

    @Test
    void aboutThreeQuartersOfKillsYieldABook() {
        Random random = new Random(7);
        int drops = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            if (SingularityRules.rollBook(BOOKS, 0.75, random).isPresent()) {
                drops++;
            }
        }
        assertThat(drops / (double) ITERATIONS).isBetween(0.745, 0.755);
    }

    @Test
    void everyBookIsReachable() {
        // A book nobody can ever find is a piece of the story that never gets told.
        Random random = new Random(3);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            SingularityRules.rollBook(BOOKS, 1.0, random).ifPresent(seen::add);
        }
        assertThat(seen).hasSize(BOOKS.size());
    }

    @Test
    void certaintyAlwaysDrops() {
        Random random = new Random(3);
        for (int i = 0; i < 500; i++) {
            assertThat(SingularityRules.rollBook(BOOKS, 1.0, random)).isPresent();
        }
    }

    @Test
    void zeroChanceNeverDrops() {
        Random random = new Random(3);
        for (int i = 0; i < 500; i++) {
            assertThat(SingularityRules.rollBook(BOOKS, 0.0, random)).isEmpty();
        }
    }

    @Test
    void anEmptyLibraryDropsNothingRatherThanThrowing() {
        assertThat(SingularityRules.rollBook(List.of(), 1.0, new Random(1))).isEmpty();
    }

    @Test
    void theSameSeedAlwaysDropsTheSameBook() {
        Optional<String> first = SingularityRules.rollBook(BOOKS, 1.0, new Random(99));
        Optional<String> second = SingularityRules.rollBook(BOOKS, 1.0, new Random(99));

        assertThat(first).isEqualTo(second);
    }
}
