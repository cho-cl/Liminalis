package com.liminalis.core.injury;

/**
 * The kind of harm done, reduced to the distinctions that change what wound it leaves.
 *
 * <p>The brief asked for injuries that match their cause - slashed by a sword and you bleed,
 * fall off a cliff and you sprain an ankle - so this exists to pick the right pool, not to
 * scale the damage.
 *
 * <p><strong>Every constant here must have both an ordinary and a mortal wound behind it.</strong>
 * A category with an empty pool is worse than a category that does not exist: the classifier
 * still says "this blow maimed them", the roster has nothing to hand back, and the blow lands
 * as though nothing happened at all - so the most violent hits in the game become the least
 * consequential. {@link InjuryCoverage} exists to make that impossible to ship.
 */
public enum DamageCategory {

    /** Swords, axes: cuts that bleed. */
    SLASHING,

    /** Arrows, tridents, thorns, cactus: punctures. */
    PIERCING,

    /** Falling anvils, maces, being buried, a Warden's shout: broken things. */
    CRUSHING,

    /** Long drops: ankles, legs. */
    FALLING,

    /** Fire, lava, magma, lightning: burns. */
    BURNING,

    /** TNT, creepers, crystals: concussion and ruptured hearing. */
    EXPLOSIVE,

    /** Powder snow, and anything else that takes the warmth out of a body. */
    FROST,

    /** Wither, poison, potions, dragon's breath: harm that works from the inside. */
    WITHERING,

    /**
     * Drowning, starvation, the void - harm with no shape of its own.
     *
     * <p>Kept as a real category with a real pool rather than a hole in the roster, so that
     * anything Mojang adds in a future version still wounds a player instead of silently
     * doing nothing.
     */
    OTHER
}
