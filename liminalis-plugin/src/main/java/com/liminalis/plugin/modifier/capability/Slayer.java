package com.liminalis.plugin.modifier.capability;

import com.liminalis.plugin.modifier.Modifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * A modifier that wants to know when its owner kills something.
 *
 * <p>Killing is one of the few moments in the game the plugin can hang a rule on that is not
 * damage, healing or a timer, and Bloodhungry is built entirely out of it: nothing in the
 * world will mend that player, and every living thing they cut down gives back a piece of
 * them.
 *
 * <p>Dispatched from {@code ModifierService} along with everything else, so a modifier still
 * never owns a listener. {@code AbilityService} counts kills separately because abilities
 * track progress rather than react to it, and merging the two would tie a boon's behaviour to
 * an ability's bookkeeping.
 */
public interface Slayer extends Modifier {

    /**
     * Called after its owner kills something.
     *
     * @param victim what died; never the killer themselves
     */
    void onKill(Player killer, LivingEntity victim);
}
