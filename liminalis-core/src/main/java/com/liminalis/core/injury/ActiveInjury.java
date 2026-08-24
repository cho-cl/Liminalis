package com.liminalis.core.injury;

/**
 * A wound a player is currently carrying.
 *
 * <p>The only modifier that has state of its own. Traits, blessings and marks are just ids -
 * you either have them or you do not - but an injury also has an expiry, because it fades.
 *
 * @param id        the injury modifier id
 * @param expiresAt epoch millis at which it fades, or 0 for a mortal wound, which never does
 */
public record ActiveInjury(String id, long expiresAt) {

    /** A wound that will not fade on its own. Mortal wounds, and nothing else. */
    public boolean permanent() {
        return expiresAt <= 0L;
    }

    public boolean hasExpired(long now) {
        return !permanent() && now >= expiresAt;
    }

    /** How long is left, in millis; zero for something already gone, and for the permanent. */
    public long remainingMillis(long now) {
        return permanent() ? 0L : Math.max(0L, expiresAt - now);
    }
}
