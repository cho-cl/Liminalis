package com.liminalis.plugin.ability;

import org.bukkit.entity.Player;

/**
 * One of the things an ability can do, bound to a numbered slot.
 *
 * <p>Powers are triggered by {@code /ability <n>} rather than by right-clicking, which is what
 * makes several of them possible at all - there are only so many distinct click gestures, and
 * asking players to remember whether a power is sneak-click or bare-handed-click does not
 * scale past about two.
 *
 * <p>Slot number and level are normally the same thing. Power 3 is what level 3 grants, so a
 * player who knows their level knows exactly which numbers work without consulting anything.
 */
public interface Power {

    /** The number a player types. Normally also the level that opens it. */
    int slot();

    /**
     * The level that opens this power, when it is not the slot number.
     *
     * <p>Almost always the slot, and the default keeps that promise. The exception is a
     * settings-style power - the Drones' aggression toggle sits in slot 6 and is available
     * from the start, because a control that decides whether your drones will hit other
     * players is not a reward, it is something you need before you have anything dangerous
     * enough to need it.
     *
     * <p>Kept as a separate question from {@link #slot()} rather than by shuffling such a
     * power into slot 1, because the numbers a player types should stay in a fixed order for
     * the whole life of the ability.
     */
    default int unlockedAt() {
        return slot();
    }

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
