package com.liminalis.core.ability;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * How far along a player is with their ability.
 *
 * <p>Every ability unlocks through conditions tailored to what it is - the Priest earns tiers
 * by healing and by putting down the undead, because a generic ladder attached to a healer
 * would feel arbitrary. What every ability shares is this arithmetic: which tier the counters
 * leave you on, and how close the next one is.
 */
public final class AbilityProgression {

    private AbilityProgression() {
    }

    /**
     * The highest tier currently open.
     *
     * <p>Walks the tiers in order and stops at the first unmet requirement, so tiers cannot
     * be skipped. Someone who has felled a hundred undead without healing anybody has done
     * the work for tier three and none of the work for tier two; they get tier one, and the
     * ability opens up in the order it was written to.
     *
     * <p>Never returns less than 1. An ability its owner cannot use at all would make being
     * granted one feel like a rejection.
     */
    public static int unlockedTier(List<TierRequirement> tiers, Map<String, Integer> counters) {
        Objects.requireNonNull(tiers, "tiers");
        Objects.requireNonNull(counters, "counters");

        int unlocked = 1;
        for (TierRequirement requirement : sorted(tiers)) {
            if (!isMet(requirement, counters)) {
                break;
            }
            unlocked = Math.max(unlocked, requirement.tier());
        }
        return unlocked;
    }

    /**
     * How close the next tier is, from 0 to 1.
     *
     * <p>Returns 1.0 at the top, because a progress bar that can never fill is worse than no
     * progress bar.
     */
    public static double progressToNext(List<TierRequirement> tiers,
                                        Map<String, Integer> counters) {
        Optional<TierRequirement> next = nextRequirement(tiers, counters);
        if (next.isEmpty()) {
            return 1.0;
        }
        TierRequirement requirement = next.get();
        if (requirement.required() <= 0) {
            return 1.0;
        }
        int have = counters.getOrDefault(requirement.counterKey(), 0);
        return Math.max(0.0, Math.min(1.0, have / (double) requirement.required()));
    }

    /**
     * The requirement standing between this player and their next tier.
     *
     * <p>This is what a player is actually shown - not "tier 2", but the thing they have to
     * go and do.
     */
    public static Optional<TierRequirement> nextRequirement(List<TierRequirement> tiers,
                                                            Map<String, Integer> counters) {
        Objects.requireNonNull(tiers, "tiers");
        Objects.requireNonNull(counters, "counters");

        for (TierRequirement requirement : sorted(tiers)) {
            if (!isMet(requirement, counters)) {
                return Optional.of(requirement);
            }
        }
        return Optional.empty();
    }

    /**
     * Progress bought with Singularity residue.
     *
     * <p>The universal accelerant, and the reason it exists: an ability gated behind
     * something its owner rarely does would otherwise leave them permanently stuck with a
     * power they can see and cannot reach.
     */
    public static int progressFromResidue(int residueSpent, int progressPerResidue) {
        if (residueSpent <= 0 || progressPerResidue <= 0) {
            return 0;
        }
        return residueSpent * progressPerResidue;
    }

    private static boolean isMet(TierRequirement requirement, Map<String, Integer> counters) {
        return counters.getOrDefault(requirement.counterKey(), 0) >= requirement.required();
    }

    private static List<TierRequirement> sorted(List<TierRequirement> tiers) {
        return tiers.stream()
                .sorted(java.util.Comparator.comparingInt(TierRequirement::tier))
                .toList();
    }
}
