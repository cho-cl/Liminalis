package com.liminalis.core.rescue;

/**
 * How long a living player may stay in the grey.
 *
 * @param crossingSeconds time before the grey starts keeping things. Long enough to search a
 *                        featureless world and get back, short enough that wandering off is a
 *                        real mistake
 */
public record RescueSettings(long crossingSeconds) {

    public static final RescueSettings DEFAULTS = new RescueSettings(300L);
}
