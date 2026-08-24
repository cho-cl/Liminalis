package com.liminalis.core.combat;

/**
 * What kind of healing is happening, for the purpose of tuning it.
 *
 * <p>Food is halved and regeneration is buffed, which together push players away from
 * grazing their way back to full and toward potions, beacons, and each other.
 */
public enum HealingKind {

    /** Natural regeneration from a full hunger bar, and health gained from eating. */
    FOOD,

    /** The Regeneration effect, from a potion, a beacon, or an ability. */
    REGENERATION,

    /** Instant Health, golden apples, and anything else left at its vanilla value. */
    OTHER
}
