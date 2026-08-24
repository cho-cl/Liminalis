package com.liminalis.core.combat;

/**
 * How a piece of damage traces back to a player, if it does at all.
 *
 * <p>The distinction matters because halving only melee damage would not reduce fighting, it
 * would just change the weapon. Anyone who wanted to hurt someone would reach for a bow, a
 * wolf, or a stack of TNT instead.
 */
public enum PlayerDamageSource {

    /** A player hitting another player themselves, with a weapon or their fist. */
    DIRECT,

    /** An arrow, trident, splash potion or similar loosed by a player. */
    PROJECTILE,

    /** A tamed animal attacking on its owner's behalf. */
    PET,

    /** TNT, an end crystal, or another explosive a player set off. */
    EXPLOSIVE,

    /** Not attributable to any player: mobs, falling, lava, drowning, the void. */
    NONE
}
