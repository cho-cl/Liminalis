package com.liminalis.core.roll;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * A weighted draw with exclusions.
 *
 * <p>Shared by the trait and boon rolls so there is exactly one implementation of "pick one
 * of these, respecting weights" to get right.
 */
public final class WeightedPool {

    private WeightedPool() {
    }

    /**
     * Draws one entry.
     *
     * <p>Excluded entries are filtered out <em>before</em> the draw rather than re-rolled on
     * a clash. That matters for small pools: re-rolling could loop, and an exhausted pool
     * simply yields nothing instead of handing back a duplicate.
     *
     * @return the drawn id, or empty if nothing is available
     */
    public static Optional<String> pick(List<WeightedEntry> entries,
                                        Set<String> exclude,
                                        Random random) {
        List<WeightedEntry> available = new ArrayList<>();
        double total = 0.0;
        for (WeightedEntry entry : entries) {
            if (exclude.contains(entry.id()) || entry.weight() <= 0) {
                continue;
            }
            available.add(entry);
            total += entry.weight();
        }
        if (available.isEmpty() || total <= 0) {
            return Optional.empty();
        }

        double target = random.nextDouble() * total;
        for (WeightedEntry entry : available) {
            target -= entry.weight();
            if (target < 0) {
                return Optional.of(entry.id());
            }
        }
        // Only reachable through floating-point drift at the very top of the range.
        return Optional.of(available.get(available.size() - 1).id());
    }
}
