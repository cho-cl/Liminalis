package com.liminalis.plugin.injury;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Harm dealt by a wound the player is already carrying.
 *
 * <p>This used to be {@code player.setHealth(health - drain)}, which is not damage - it is
 * arithmetic. Nothing flashed, nothing made a sound, the hurt animation never played, and the
 * hearts simply had fewer of them the next time you looked. Players reported bleeding as
 * doing nothing, and they were right to: the only evidence it existed was a number going down
 * while they were looking somewhere else.
 *
 * <p>So it goes through the real damage path now, exactly the way poison does, and feels the
 * way poison feels. The damage type is {@code MAGIC} for a specific reason: vanilla tags it
 * {@code bypasses_armor}, which is what you want from a wound that is already inside the
 * armour. Poison uses the same one.
 *
 * <p><strong>The guard is load-bearing.</strong> Real damage fires a real
 * {@code EntityDamageEvent}, and {@link InjuryService} listens to those to decide whether a
 * blow wounded somebody. Without this, a bleeding tick would be judged as a fresh injury -
 * and a wound that can inflict wounds is a loop that only ends when the player does. The
 * numbers alone happen to keep it below the threshold today; that is not a thing to rely on
 * when the threshold is a config value somebody will edit.
 */
public final class WoundDamage {

    /** Players currently inside a wound tick. Main thread only, so a plain set is enough. */
    private static final Set<UUID> INSIDE = ConcurrentHashMap.newKeySet();

    private WoundDamage() {
    }

    /**
     * Deals damage from a wound, refusing to be the thing that kills them.
     *
     * <p>A wound that could finish someone who had already escaped a fight would make
     * ordinary injuries as decisive as mortal ones, so the damage stops at the floor and
     * the player is left standing.
     *
     * @param floor health this will never take a player below
     * @return true if damage was actually dealt
     */
    public static boolean inflict(Player player, double amount, double floor) {
        if (amount <= 0 || player.getHealth() - amount <= floor) {
            return false;
        }
        UUID id = player.getUniqueId();
        INSIDE.add(id);
        try {
            player.damage(amount, DamageSource.builder(DamageType.MAGIC).build());
        } finally {
            // In a finally block rather than after the call: a listener that throws must not
            // leave a player permanently exempt from being wounded.
            INSIDE.remove(id);
        }
        return true;
    }

    /** Whether the damage currently being processed came from a wound this player has. */
    public static boolean isWoundTick(Player player) {
        return INSIDE.contains(player.getUniqueId());
    }
}
