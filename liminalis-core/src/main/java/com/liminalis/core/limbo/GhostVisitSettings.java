package com.liminalis.core.limbo;

/**
 * How long the dead get among the living, and how long they wait between trips.
 *
 * @param visitMillis    how long a single visit lasts
 * @param cooldownMillis how long after a visit ends before another can begin
 */
public record GhostVisitSettings(long visitMillis, long cooldownMillis) {

    public static final GhostVisitSettings DEFAULTS =
            new GhostVisitSettings(5 * 60 * 1000L, 15 * 60 * 1000L);
}
