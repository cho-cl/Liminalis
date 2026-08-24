package com.liminalis.plugin.modifier.capability;

import com.liminalis.plugin.modifier.Modifier;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * A modifier that changes a player's attributes by a fixed amount.
 *
 * <p>Covers most of the small traits outright: "10% shorter" is one {@code SCALE}
 * contribution, "mines 30% faster" is one {@code BLOCK_BREAK_SPEED} contribution, "+3 hearts"
 * is one {@code MAX_HEALTH} contribution.
 *
 * <p>Contributions are recomputed and re-applied wholesale rather than adjusted in place, so
 * there is no way for a stale modifier to survive a reload or a respawn.
 */
public interface AttributeSource extends Modifier {

    /**
     * The attribute changes this modifier wants for the given player.
     *
     * <p>Must be a pure function of the player's current state - it will be called again on
     * every recompute, and it must not have side effects.
     */
    List<AttributeContribution> attributeContributions(Player player);
}
