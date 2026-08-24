package com.liminalis.core.config;

/**
 * An immutable snapshot of every tunable value.
 *
 * <p>Handed out by value so that a reload can swap the whole config atomically, and nothing
 * can observe a half-applied change part-way through a tick.
 *
 * <p>Grows one field per phase. Phase 0 only needs what the foundation itself reads.
 */
public record LiminalisConfig(
        int startingLives,
        boolean backupOnStart,
        int keepBackups,
        boolean debug) {

    public static final LiminalisConfig DEFAULTS = new LiminalisConfig(3, true, 10, false);
}
