package com.liminalis.core.roll;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Decides who a player is, once, on the day they first join.
 *
 * <p>Everyone gets one trait. A quarter of players get a second. Each slot has its own small
 * chance of reaching into the Singularity pool instead of the ordinary one.
 *
 * <p>Takes its {@link Random} as a parameter so the distribution can actually be tested,
 * which matters more here than almost anywhere else in the plugin: a roll table that is
 * quietly nothing like its configuration produces a server that feels wrong for reasons
 * nobody can put their finger on.
 */
public final class TraitRoller {

    private final List<RollCandidate> ordinary;
    private final List<RollCandidate> singularity;

    public TraitRoller(List<RollCandidate> pool) {
        Objects.requireNonNull(pool, "pool");
        this.ordinary = pool.stream()
                .filter(candidate -> candidate.tier() == TraitTier.ORDINARY)
                .toList();
        this.singularity = pool.stream()
                .filter(candidate -> candidate.tier() == TraitTier.SINGULARITY)
                .toList();
    }

    /**
     * Rolls a player's traits.
     *
     * @return the trait ids granted, in the order they were drawn; never null, and empty only
     *         if there are no traits registered at all
     */
    public List<String> roll(TraitRollSettings settings, Random random) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(random, "random");

        Set<String> granted = new LinkedHashSet<>();

        drawInto(granted, settings, random);
        if (random.nextDouble() < settings.secondTraitChance()) {
            drawInto(granted, settings, random);
        }
        return List.copyOf(granted);
    }

    private static List<WeightedEntry> asEntries(List<RollCandidate> pool) {
        return pool.stream()
                .map(candidate -> new WeightedEntry(candidate.id(), candidate.weight()))
                .toList();
    }

    private void drawInto(Set<String> granted, TraitRollSettings settings, Random random) {
        boolean reachForSingularity = random.nextDouble() < settings.singularityChance();

        // Fall back to the ordinary pool if the Singularity pool is empty, so a build with no
        // Singularity traits still gives everyone something rather than nothing.
        List<RollCandidate> pool = reachForSingularity && !singularity.isEmpty()
                ? singularity : ordinary;

        WeightedPool.pick(asEntries(pool), granted, random).ifPresent(granted::add);
    }

}
