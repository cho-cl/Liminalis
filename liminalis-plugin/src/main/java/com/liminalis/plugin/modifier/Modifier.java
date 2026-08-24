package com.liminalis.plugin.modifier;

import org.bukkit.entity.Player;

/**
 * Something attached to a player that changes how they work.
 *
 * <p>Traits, blessings, curses, marks, injuries and abilities are all this. Keeping them one
 * concept means the roll system, the admin commands, the profile screen and the persistence
 * layer are each written once rather than six times.
 *
 * <p>Behaviour is opted into through the small capability interfaces in
 * {@code com.liminalis.plugin.modifier.capability} rather than declared here, so a modifier
 * that only changes a stat does not have to know anything about ticking or damage.
 *
 * <p><strong>A modifier must never register its own listener or schedule its own task.</strong>
 * {@link ModifierService} owns the one listener and the one tick loop, and dispatches to
 * whatever is attached to the affected player. That keeps ordering deterministic and stops
 * the server accumulating a task per player per trait.
 */
public interface Modifier {

    /**
     * Stable lowercase identifier. This is what gets written into player profiles, so
     * changing it orphans it on everyone who has it - treat it as permanent once released.
     */
    String id();

    ModifierType type();

    /** Key into messages.yml for the display name. */
    default String nameKey() {
        return type().id() + "." + id() + ".name";
    }

    /** Key into messages.yml for the description shown on the profile screen. */
    default String descriptionKey() {
        return type().id() + "." + id() + ".description";
    }

    /** Called when this is attached to a player: on join, on being granted, on reload. */
    default void onAttach(Player player) {
    }

    /** Called when this is removed, and on quit. Must undo anything {@code onAttach} did. */
    default void onDetach(Player player) {
    }
}
