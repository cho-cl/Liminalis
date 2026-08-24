package com.liminalis.plugin.modifier.capability;

import com.liminalis.plugin.modifier.Modifier;
import org.bukkit.entity.Player;

/**
 * A modifier that changes what dying costs its owner.
 *
 * <p>On a server where everyone has three lives, death is already the most expensive event
 * there is. What it costs on top of a life - everything you were carrying, and every level
 * you had earned - is a second penalty entirely, and one a boon can reasonably speak to.
 *
 * <p>Kept separate from the life arithmetic on purpose. Whether a death costs a life is a
 * rule of the world and belongs in {@code LifeRules}; whether your pack survives it is a
 * property of the person dying.
 */
public interface DeathBehaviour extends Modifier {

    /** Whether its owner keeps their inventory and levels through a death. */
    default boolean keepsInventory(Player player) {
        return false;
    }
}
