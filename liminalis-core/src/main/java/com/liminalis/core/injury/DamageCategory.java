package com.liminalis.core.injury;

/**
 * The kind of harm done, reduced to the distinctions that change what wound it leaves.
 *
 * <p>The brief asked for injuries that match their cause - slashed by a sword and you bleed,
 * fall off a cliff and you sprain an ankle - so this exists to pick the right pool, not to
 * scale the damage.
 */
public enum DamageCategory {

    /** Swords, axes: cuts that bleed. */
    SLASHING,

    /** Arrows, tridents, stings: punctures. */
    PIERCING,

    /** Falling anvils, maces, being crushed: broken things. */
    CRUSHING,

    /** Long drops: ankles, legs. */
    FALLING,

    /** Fire, lava, magma: burns. */
    BURNING,

    /** TNT, creepers, crystals: concussion and ruptured hearing. */
    EXPLOSIVE,

    /** Anything with no matching pool. Leaves generic harm. */
    OTHER
}
