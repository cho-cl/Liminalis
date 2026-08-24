package com.liminalis.core.lives;

/**
 * Why a player died, reduced to the only distinctions that affect whether it costs a life.
 *
 * <p>Minecraft has around thirty damage causes; this has five, because the rule is simply
 * "everything counts except what the server itself did to you, and player kills are a toggle".
 */
public enum DeathCause {

    /** Killed by another player, directly or through an arrow, a pet, or an explosive. */
    PLAYER,

    /** Killed by a mob. */
    MOB,

    /** Fall, lava, drowning, fire, suffocation, the void - the world itself. */
    ENVIRONMENT,

    /**
     * The server did it: an operator's {@code /kill}, a plugin, or a technical fault.
     *
     * <p>Never costs a life. Operators kill people to test and to fix things, and a lag
     * spike is not a death the player earned.
     */
    ADMIN,

    /** Anything unrecognised. Treated as the world killing you, so it counts. */
    UNKNOWN
}
