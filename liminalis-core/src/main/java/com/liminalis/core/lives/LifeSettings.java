package com.liminalis.core.lives;

/**
 * Tuning for the lives system.
 *
 * @param startingLives   how many lives a new player begins with
 * @param pvpDeathsCount  whether being killed by another player costs a life; toggled live
 *                        with {@code /liminalis lives pvpcounts}
 */
public record LifeSettings(int startingLives, boolean pvpDeathsCount) {

    public static final LifeSettings DEFAULTS = new LifeSettings(3, true);
}
