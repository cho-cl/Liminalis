package com.liminalis.core.lives;

import com.liminalis.core.profile.PlayerProfile;

import java.util.Objects;

/**
 * Three lives, no more no less, and the third death is the one that ends you.
 *
 * <p>The most consequential logic in the plugin. Getting it wrong takes somebody's last life
 * when it should not have, or fails to take one when it should - and from the player's side
 * both are permanent.
 */
public final class LifeRules {

    private LifeRules() {
    }

    /**
     * Applies a death to a profile.
     *
     * @param now epoch millis, used to stamp the moment they fell to Limbo
     * @return what the death actually did
     */
    public static DeathVerdict recordDeath(PlayerProfile profile,
                                           DeathCause cause,
                                           LifeSettings settings,
                                           long now) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(cause, "cause");

        // There is no way to die in Limbo. If something ever manages it anyway, it must not
        // drive lives negative or re-stamp the arrival time.
        if (profile.inLimbo()) {
            return DeathVerdict.IGNORED;
        }
        if (!costsALife(cause, settings)) {
            return DeathVerdict.IGNORED;
        }

        profile.setTotalDeaths(profile.totalDeaths() + 1);
        profile.setLivesRemaining(Math.max(0, profile.livesRemaining() - 1));

        if (profile.livesRemaining() > 0) {
            return DeathVerdict.LIFE_SPENT;
        }

        profile.setInLimbo(true);
        profile.setLimboSince(now);
        return DeathVerdict.FELL_TO_LIMBO;
    }

    /**
     * Brings a player back from Limbo.
     *
     * <p>Deliberately a no-op on someone who is not in Limbo, so that an admin revive aimed
     * at the wrong name cannot hand a living player free lives.
     *
     * @param livesRestored how many lives they return with
     * @param markId        the permanent mark left by having been to the other side
     * @return true if they were in Limbo and have been returned
     */
    public static boolean revive(PlayerProfile profile, int livesRestored, String markId) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(markId, "markId");

        if (!profile.inLimbo()) {
            return false;
        }

        profile.setInLimbo(false);
        profile.setLimboSince(0L);
        profile.setLivesRemaining(Math.max(0, livesRestored));
        // A set, so a second return leaves exactly one mark.
        profile.addMark(markId);
        return true;
    }

    /**
     * Whether this death should cost a life.
     *
     * <p>The PvP toggle is deliberately narrow: turning it off so friends stop costing each
     * other lives must not also make lava and creepers harmless.
     */
    private static boolean costsALife(DeathCause cause, LifeSettings settings) {
        return switch (cause) {
            case PLAYER -> settings.pvpDeathsCount();
            case MOB, ENVIRONMENT, UNKNOWN -> true;
            case ADMIN -> false;
        };
    }
}
