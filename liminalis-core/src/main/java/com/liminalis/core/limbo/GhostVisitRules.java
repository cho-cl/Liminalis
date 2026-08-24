package com.liminalis.core.limbo;

import com.liminalis.core.profile.PlayerProfile;

import java.util.Objects;

/**
 * Governs a Limbo player's brief returns to the living world.
 *
 * <p>The cooldown lives on the profile rather than in memory because it has to survive a
 * logout and a server restart. Held in memory, leaving and rejoining would reset it, and
 * haunting would become continuous rather than the rare, unsettling thing it is meant to be.
 */
public final class GhostVisitRules {

    private GhostVisitRules() {
    }

    /** Whether this player may begin a visit right now. */
    public static boolean canVisit(PlayerProfile profile, long now) {
        Objects.requireNonNull(profile, "profile");
        return profile.inLimbo() && now >= profile.ghostVisitCooldownUntil();
    }

    /**
     * Starts a visit and arms the cooldown.
     *
     * <p>The cooldown is measured from the moment the visit <em>ends</em>. Measured from the
     * start, a fifteen minute cooldown on a five minute visit would really only be ten
     * minutes away.
     *
     * @return the epoch millis at which the visit ends and the player is returned to Limbo
     */
    public static long beginVisit(PlayerProfile profile, GhostVisitSettings settings, long now) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(settings, "settings");

        long endsAt = now + settings.visitMillis();
        profile.setGhostVisitCooldownUntil(endsAt + settings.cooldownMillis());
        return endsAt;
    }

    /** How long until this player may visit again; zero if they already can. */
    public static long cooldownRemainingMillis(PlayerProfile profile, long now) {
        Objects.requireNonNull(profile, "profile");
        return Math.max(0L, profile.ghostVisitCooldownUntil() - now);
    }

    /**
     * Wipes the cooldown.
     *
     * <p>Used on revival, and by the admin command. The cooldown is a limit on haunting, not
     * a punishment attached to the person - someone rescued and later lost again should not
     * arrive in Limbo already locked out.
     */
    public static void clearCooldown(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        profile.setGhostVisitCooldownUntil(0L);
    }
}
