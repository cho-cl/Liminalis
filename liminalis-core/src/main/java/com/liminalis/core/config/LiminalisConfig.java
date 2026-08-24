package com.liminalis.core.config;

import com.liminalis.core.combat.CombatSettings;

/**
 * An immutable snapshot of every tunable value.
 *
 * <p>Handed out by value so that a reload can swap the whole config atomically, and nothing
 * can observe a half-applied change part-way through a tick.
 *
 * <p>Grows one field per phase. Related settings are grouped into their own record rather
 * than flattened in here, so this stays readable as the server's rules accumulate.
 */
public record LiminalisConfig(
        int startingLives,
        boolean backupOnStart,
        int keepBackups,
        boolean debug,
        CombatSettings combat) {

    public static final LiminalisConfig DEFAULTS =
            new LiminalisConfig(3, true, 10, false, CombatSettings.DEFAULTS);
}
