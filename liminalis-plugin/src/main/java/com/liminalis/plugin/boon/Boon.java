package com.liminalis.plugin.boon;

import com.liminalis.core.roll.WeightedEntry;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierType;

/**
 * A blessing or a curse: the thing 30% of players are marked with on their first join.
 *
 * <p>The two differ in kind, not just in size. A blessing is a straight gift. A curse is a
 * bargain - a larger gift than any blessing, paid for with something a player would
 * genuinely rather keep.
 */
public interface Boon extends Modifier {

    /** Relative likelihood within its own pool. */
    default double weight() {
        return 1.0;
    }

    default WeightedEntry asEntry() {
        return new WeightedEntry(id(), weight());
    }

    /** Convenience for readers: whether this is the cursed half of the roster. */
    default boolean isCurse() {
        return type() == ModifierType.CURSE;
    }
}
