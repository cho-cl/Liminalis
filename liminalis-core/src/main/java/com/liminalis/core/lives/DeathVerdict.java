package com.liminalis.core.lives;

/**
 * What a death actually did to the player.
 */
public enum DeathVerdict {

    /** A life is gone, but they are still among the living. */
    LIFE_SPENT,

    /** That was the last one. They are in Limbo now. */
    FELL_TO_LIMBO,

    /** It did not count: an admin kill, a player kill with PvP deaths off, or a death in Limbo. */
    IGNORED
}
