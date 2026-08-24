package com.liminalis.plugin.modifier.capability;

import com.liminalis.plugin.modifier.Modifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A modifier that forbids a player from wearing something.
 *
 * <p>This is the primitive behind the "cannot wear Protection IV" class of curse drawback:
 * the thing that makes a curse a bargain rather than just a bigger blessing. The cost has to
 * be something a player would otherwise want, and armour is the most reliably wanted thing
 * in the game.
 *
 * <p>Enforcement is a refusal rather than a punishment - the piece comes straight back off
 * and the player is told why. Letting them wear it and quietly taking the protection away
 * would be indistinguishable from a bug.
 */
public interface Restriction extends Modifier {

    /**
     * Whether this modifier forbids wearing the given piece.
     *
     * <p>Called whenever a player's armour changes, so it must be cheap and must not have
     * side effects.
     *
     * @param armour the piece being put on; never null and never air
     */
    boolean forbidsWearing(Player player, ItemStack armour);

    /** messages.yml key for the line shown when a piece is refused. */
    default String refusalKey() {
        return type().id() + "." + id() + ".refused";
    }
}
