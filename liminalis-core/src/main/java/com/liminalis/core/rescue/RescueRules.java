package com.liminalis.core.rescue;

import com.liminalis.core.limbo.GhostVisitRules;
import com.liminalis.core.lives.LifeRules;
import com.liminalis.core.profile.PlayerProfile;

import java.util.Objects;

/**
 * Crossing into the grey to fetch someone back.
 *
 * <p>Nothing done from the living world reaches into Limbo - that is what the books say and
 * what the design commits to. The only way to retrieve someone is to go and get them, which
 * makes rescue an expedition rather than a rite, and makes the rescuer's own lives the
 * currency it is paid in.
 *
 * <p>The expiry rule is the whole risk of the phase. Stay too long and the grey takes a life
 * in exchange for letting you out. Stay too long with none left to give, and it does not let
 * you out at all - you have joined the person you came for, and someone will have to come for
 * you both.
 */
public final class RescueRules {

    private RescueRules() {
    }

    /**
     * Whether this player may cross.
     *
     * <p>Someone on their last life is deliberately allowed to go. It is the most dangerous
     * thing available in the server, and forbidding it would remove the only real sacrifice
     * a player can make for someone else.
     */
    public static boolean mayCross(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return !profile.inLimbo();
    }

    /** Coming back under your own power, in time. Costs nothing. */
    public static CrossingOutcome returnFrom(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return CrossingOutcome.RETURNED;
    }

    /**
     * The crossing ran out.
     *
     * @param now epoch millis, used to stamp the arrival if they are kept
     * @return what happened to them
     */
    public static CrossingOutcome expire(PlayerProfile profile, long now) {
        Objects.requireNonNull(profile, "profile");

        int remaining = profile.livesRemaining();
        if (remaining > 1) {
            profile.setLivesRemaining(remaining - 1);
            return CrossingOutcome.RETURNED_DIMINISHED;
        }

        // Nothing left to pay with.
        profile.setLivesRemaining(0);
        profile.setInLimbo(true);
        profile.setLimboSince(now);
        return CrossingOutcome.STRANDED;
    }

    /**
     * Carrying someone out of the grey.
     *
     * <p>Delegates the state change to {@link LifeRules#revive} so there is exactly one place
     * that decides what returning from Limbo means, whether it happened through a rescue or
     * through an admin command.
     *
     * @return true if they were in Limbo and have been brought back
     */
    public static boolean retrieve(PlayerProfile carried, int livesRestored, String markId) {
        Objects.requireNonNull(carried, "carried");

        if (!LifeRules.revive(carried, livesRestored, markId)) {
            return false;
        }
        // They are not a ghost any more; a haunting cooldown following them back into the
        // living world would mean nothing.
        GhostVisitRules.clearCooldown(carried);
        return true;
    }
}
