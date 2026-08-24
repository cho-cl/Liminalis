package com.liminalis.plugin.modifier.capability;

import com.liminalis.plugin.modifier.Modifier;
import org.bukkit.entity.Player;

/**
 * A modifier that needs to do something periodically - bleeding out, a burn dealing damage
 * over time, an aura pulsing.
 *
 * <p>Implementations are driven by the single shared loop in
 * {@code ModifierService}. They must never schedule their own task: one repeating task per
 * player per modifier is how a server ends up with hundreds of timers doing nothing.
 */
public interface Ticking extends Modifier {

    /**
     * Called on the main thread at the shared tick interval, only while the player is online.
     *
     * <p>This runs for every player carrying this modifier, so keep it cheap and do nothing
     * that blocks.
     */
    void tick(Player player);
}
