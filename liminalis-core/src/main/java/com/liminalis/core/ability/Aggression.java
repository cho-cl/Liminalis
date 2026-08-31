package com.liminalis.core.ability;

/**
 * What a player's drones are willing to do to other things.
 *
 * <p>Four settings rather than a single on/off, because "will my drones hit that player" and
 * "will my drones start a fight I did not ask for" are separate worries and a server where
 * everyone has three lives makes both of them real. The order below is the order the toggle
 * cycles in, and it runs from harmless to dangerous on purpose: cycling past the end lands
 * you back on {@link #PASSIVE} rather than one step from attacking your friends.
 */
public enum Aggression {

    /** They will not fight anything, even to save you. */
    PASSIVE("passive"),

    /** They fight only what has already hit you. The sane default. */
    DEFENSIVE("defensive"),

    /** They pick fights with hostile mobs nearby, and never with a player. */
    HOSTILES("hostiles"),

    /**
     * They fight anything you fight, players included.
     *
     * <p>The only setting that can cost somebody else a life, so it is the last in the cycle
     * and the one a player has to pass through every other option to reach.
     */
    EVERYTHING("everything");

    private final String id;

    Aggression(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** The default a newly summoned fleet starts on. */
    public static Aggression standard() {
        return DEFENSIVE;
    }

    /** The next setting in the cycle, wrapping back to {@link #PASSIVE} from the end. */
    public Aggression next() {
        Aggression[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Whether this setting lets a drone start a fight nobody has started with you. */
    public boolean initiates() {
        return this == HOSTILES || this == EVERYTHING;
    }

    /** Whether this setting lets a drone raise a stinger against another player. */
    public boolean allowsPlayers() {
        return this == EVERYTHING;
    }

    /** Whether this setting lets a drone answer something that has hit its owner. */
    public boolean retaliates() {
        return this != PASSIVE;
    }

    /** Parses an id back, falling to the standard setting for anything unrecognised. */
    public static Aggression byId(String id) {
        for (Aggression mode : values()) {
            if (mode.id.equalsIgnoreCase(id)) {
                return mode;
            }
        }
        return standard();
    }
}
