package com.liminalis.plugin.modifier.capability;

import com.liminalis.plugin.modifier.Modifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * A modifier that changes the damage its owner deals.
 *
 * <p>Deliberately left unbuilt until something needed it. It exists now because the Priest's
 * second tier is holy damage against the undead, which is a real consumer with a real shape -
 * inventing this interface in Phase 0 with nothing to implement it would have been guesswork.
 */
public interface DamageDealer extends Modifier {

    /**
     * Adjusts outgoing damage.
     *
     * @param victim what is being hit; abilities are not meant for PvP, so most
     *               implementations should check this is not a player
     * @param damage the damage as it stands
     * @return the damage to deal instead
     */
    double adjustOutgoing(Player attacker, Entity victim, double damage);
}
