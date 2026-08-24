package com.liminalis.plugin.modifier.capability;

import com.liminalis.plugin.modifier.Modifier;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;

/**
 * A modifier that changes how its owner mends.
 *
 * <p>Healing is already a designed resource on this server rather than an afterthought -
 * Phase 1 halved what food gives back and buffed what Regeneration does, precisely so that
 * getting better is something players have to think about. This is the hook that lets a boon
 * rewrite those rules for one person.
 *
 * <p>It is the shape that makes the two most interesting curses possible, and they are exact
 * opposites: Gluttonous quadruples what a meal is worth and burns through hunger to pay for
 * it; Bloodhungry refuses every source of healing there is and gives you one of its own.
 * Neither is expressible as a stat.
 */
public interface HealingRule extends Modifier {

    /**
     * Adjusts an amount of healing before it is applied.
     *
     * @param reason where the healing came from - eating, regeneration, a golden apple
     * @param amount the healing as it stands
     * @return the healing to apply instead; {@code 0} cancels it entirely
     */
    double adjustHealing(Player player, RegainReason reason, double amount);
}
