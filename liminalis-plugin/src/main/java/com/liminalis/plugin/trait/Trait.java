package com.liminalis.plugin.trait;

import com.liminalis.core.roll.RollCandidate;
import com.liminalis.core.roll.TraitTier;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierType;

/**
 * A trait: the thing every player is rolled on their first join and keeps forever.
 *
 * <p>Adds only what the roll needs on top of {@link Modifier} - which tier it belongs to and
 * how likely it is within that tier. Everything a trait actually <em>does</em> comes from the
 * capability interfaces, exactly as it does for blessings, curses and injuries.
 */
public interface Trait extends Modifier {

    TraitTier tier();

    /** Relative likelihood within this trait's tier. 2.0 is twice as likely as 1.0. */
    default double weight() {
        return 1.0;
    }

    @Override
    default ModifierType type() {
        return ModifierType.TRAIT;
    }

    default RollCandidate asCandidate() {
        return new RollCandidate(id(), tier(), weight());
    }
}
