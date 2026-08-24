package com.liminalis.plugin.modifier;

/**
 * Which slot on a profile a modifier occupies.
 *
 * <p>All of these behave identically once attached to a player - they differ in how they are
 * acquired, whether they can be lost, and how many you can hold at once.
 */
public enum ModifierType {

    /** Rolled on first join. Everyone has at least one. Permanent. */
    TRAIT("trait", true),

    /** Rolled on first join at 15%. A straight upside, no drawback. At most one. */
    BLESSING("blessing", false),

    /** Rolled on first join at 15%. A larger upside paid for with a real cost. At most one. */
    CURSE("curse", false),

    /** Earned, not rolled. The Mark of Return is the first. Permanent. */
    MARK("mark", true),

    /** Inflicted by damage. Decays with time; cleared entirely by respawning. */
    INJURY("injury", true),

    /** Hand-assigned by the Creator, one per player, unlocked in tiers. */
    ABILITY("ability", false);

    private final String id;
    private final boolean stackable;

    ModifierType(String id, boolean stackable) {
        this.id = id;
        this.stackable = stackable;
    }

    /** Lowercase identifier, used in command arguments and permission nodes. */
    public String id() {
        return id;
    }

    /** Whether a player can hold more than one of this type at a time. */
    public boolean stackable() {
        return stackable;
    }
}
