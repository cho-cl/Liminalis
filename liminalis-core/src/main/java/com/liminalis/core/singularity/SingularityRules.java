package com.liminalis.core.singularity;

import com.liminalis.core.roll.WeightedEntry;
import com.liminalis.core.roll.WeightedPool;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * How often the Singularity reaches into the world, and what it leaves behind.
 */
public final class SingularityRules {

    private SingularityRules() {
    }

    /**
     * How many creatures arrive this wave.
     *
     * <p>Rolled per player rather than once for the server. That is the detail that decides
     * whether the world feels inhabited: a single roll would mean a full server and an empty
     * one saw the same number of creatures, and the people actually playing would almost
     * never meet one.
     */
    public static int spawnCountFor(int onlinePlayers, double chancePerPlayer, Random random) {
        Objects.requireNonNull(random, "random");
        if (onlinePlayers <= 0 || chancePerPlayer <= 0) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < onlinePlayers; i++) {
            if (random.nextDouble() < chancePerPlayer) {
                count++;
            }
        }
        return count;
    }

    /**
     * What a dead Singularity creature leaves.
     *
     * @param books  the library, weighted
     * @param chance the chance anything drops at all
     * @return the book id, or empty if this one left nothing
     */
    public static Optional<String> rollBook(List<WeightedEntry> books,
                                            double chance,
                                            Random random) {
        Objects.requireNonNull(books, "books");
        Objects.requireNonNull(random, "random");

        if (chance <= 0 || random.nextDouble() >= chance) {
            return Optional.empty();
        }
        return WeightedPool.pick(books, Set.of(), random);
    }
}
