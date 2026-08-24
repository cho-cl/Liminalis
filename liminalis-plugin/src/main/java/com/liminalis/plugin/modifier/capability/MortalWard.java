package com.liminalis.plugin.modifier.capability;

import com.liminalis.plugin.modifier.Modifier;
import org.bukkit.entity.Player;

/**
 * A modifier that stops its owner from ever being maimed.
 *
 * <p>Mortal wounds are the only permanent harm in the game: they do not fade, no potion
 * touches them, and until an ability exists to treat one the only cure is to spend a life for
 * a new body. Being immune to that is a categorical promise rather than a number, and it is
 * the whole of what the Unbroken blessing is.
 *
 * <p>Deliberately a softening rather than a cancellation. A blow that should have taken an arm
 * still leaves an ordinary wound, because a blessing that made the biggest hits in the game
 * <em>harmless</em> would be a strictly better outcome than not being hit at all.
 */
public interface MortalWard extends Modifier {

    /** Whether a maiming blow should land as an ordinary wound instead. */
    default boolean softensMortalWounds(Player player) {
        return true;
    }
}
