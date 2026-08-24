package com.liminalis.core.ability;

/**
 * Tuning for how abilities open up.
 *
 * @param progressPerResidue how much progress one shard of Singularity residue buys toward
 *                           the next tier. The rate that decides whether the accelerant is a
 *                           genuine alternative route or merely a nudge
 */
public record AbilitySettings(int progressPerResidue) {

    public static final AbilitySettings DEFAULTS = new AbilitySettings(25);
}
