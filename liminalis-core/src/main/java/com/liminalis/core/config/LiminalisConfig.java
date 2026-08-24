package com.liminalis.core.config;

import com.liminalis.core.combat.CombatSettings;
import com.liminalis.core.limbo.LimboSettings;
import com.liminalis.core.lives.LifeSettings;

/**
 * An immutable snapshot of every tunable value.
 *
 * <p>Handed out by value so that a reload can swap the whole config atomically, and nothing
 * can observe a half-applied change part-way through a tick.
 *
 * <p>Grows one group per phase. Related settings live in their own record rather than
 * flattened in here, so this stays readable as the server's rules accumulate.
 */
public record LiminalisConfig(
        LifeSettings lives,
        LimboSettings limbo,
        CombatSettings combat,
        boolean backupOnStart,
        int keepBackups,
        boolean debug) {

    public static final LiminalisConfig DEFAULTS = new LiminalisConfig(
            LifeSettings.DEFAULTS,
            LimboSettings.DEFAULTS,
            CombatSettings.DEFAULTS,
            true,
            10,
            false);
}
