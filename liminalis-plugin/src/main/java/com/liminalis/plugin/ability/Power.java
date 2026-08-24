package com.liminalis.plugin.ability;

import org.bukkit.entity.Player;

/**
 * One of the five things an ability can do, bound to a numbered slot.
 *
 * <p>Powers are triggered by {@code /ability <n>} rather than by right-clicking, which is what
 * makes five of them possible at all - there are only so many distinct click gestures, and
 * asking players to remember whether a power is sneak-click or bare-handed-click does not
 * scale past about two.
 *
 * <p>Slot number and tier are the same thing. Power 3 is what tier 3 grants, so a player who
 * knows they are tier 3 knows exactly which numbers work without consulting anything.
 */
public interface Power {

    /** 1 to 5. Also the tier that unlocks it. */
    int slot();

    /** Stable id, used for cooldown bookkeeping and message keys. */
    String id();

    /** Seconds before it can be used again. Zero for powers with no cooldown. */
    long cooldownSeconds();

    /** Whether this power does something to another player, and so needs one aimed at. */
    default boolean needsTarget() {
        return false;
    }

    /**
     * Fires the power.
     *
     * @param target who they were aiming at, or null - only ever non-null when
     *               {@link #needsTarget()} is true and someone was actually found
     * @return true if it fired and the cooldown should start; false if it was refused, in
     *         which case the implementation has already explained why
     */
    boolean use(Player user, Player target);
}
