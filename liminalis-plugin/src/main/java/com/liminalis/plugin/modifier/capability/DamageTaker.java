package com.liminalis.plugin.modifier.capability;

import com.liminalis.plugin.modifier.Modifier;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * A modifier that changes the damage its owner takes.
 *
 * <p>The missing half of {@link DamageDealer}, and the primitive behind a whole class of boon
 * that could not be written before it: not "you take less fire damage" but "fire cannot hurt
 * you", which is a different kind of promise and the reason Emberborn is worth rolling.
 *
 * <p>Deliberately given the whole event rather than a number, because the interesting
 * decisions are about the <em>cause</em>. A modifier that only wants to scale damage can
 * ignore it and return a smaller figure.
 */
public interface DamageTaker extends Modifier {

    /**
     * Adjusts incoming damage.
     *
     * <p>Return {@code 0} to make the blow harmless; the event is cancelled outright, so it
     * also leaves no wound, no knockback and no hurt animation. Anything else is applied as
     * the new damage.
     *
     * @param event  the blow, for its cause; must not be cancelled or modified here
     * @param damage the damage as it stands, after everything before us
     * @return the damage to take instead
     */
    double adjustIncoming(Player player, EntityDamageEvent event, double damage);
}
