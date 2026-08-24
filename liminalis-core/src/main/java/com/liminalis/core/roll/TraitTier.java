package com.liminalis.core.roll;

/**
 * How rare a trait is, and which pool it is drawn from.
 *
 * <p>The two tiers are drawn separately rather than by weight within one pool. That is what
 * makes the configured Singularity rate mean what it says: if Singularity traits also sat in
 * the ordinary pool, they would be reachable through both rolls and genuinely rarer or
 * commoner than the number in the config.
 */
public enum TraitTier {

    /** The everyday roster: small quirks and the occasional serious one. */
    ORDINARY,

    /** Much, much different from the rest, and drawn only on its own low chance. */
    SINGULARITY
}
