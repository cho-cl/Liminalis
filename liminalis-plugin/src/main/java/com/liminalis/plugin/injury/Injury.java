package com.liminalis.plugin.injury;

import com.liminalis.core.injury.DamageCategory;
import com.liminalis.core.injury.InjurySeverity;
import com.liminalis.core.roll.WeightedEntry;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierType;

import java.util.Set;

/**
 * A wound: the only modifier a player acquires by being hurt rather than by being rolled or
 * granted.
 *
 * <p>Injuries differ from everything else in the system in two ways that matter. They decay
 * on their own with time, faster under a Regeneration effect - and they are wiped entirely by
 * respawning, because a new body carries no old wounds. That second rule is the deliberate
 * trade at the heart of the design: a player with a mortal wound can live crippled, or spend
 * one of their three lives to be whole again.
 *
 * <p>Mortal wounds are the same type with {@code decays()} false. They do not fade, and until
 * a healing ability exists to treat one, dying is the only way out of it.
 */
public interface Injury extends Modifier {

    /** Which kinds of harm can leave this wound. */
    Set<DamageCategory> causes();

    /** Whether this is an ordinary injury or something that costs a limb. */
    InjurySeverity severity();

    /** Relative likelihood within its pool. */
    default double weight() {
        return 1.0;
    }

    /**
     * How long this takes to fade on its own, in seconds. Ignored for mortal wounds.
     *
     * <p>Read from config by implementations, so the pace of healing is tunable without a
     * rebuild like everything else.
     */
    long durationSeconds();

    /** Mortal wounds never fade. Everything else does. */
    default boolean decays() {
        return severity() != InjurySeverity.MORTAL_WOUND;
    }

    @Override
    default ModifierType type() {
        return ModifierType.INJURY;
    }

    default WeightedEntry asEntry() {
        return new WeightedEntry(id(), weight());
    }
}
