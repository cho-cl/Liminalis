package com.liminalis.core.lives;

import com.liminalis.core.profile.PlayerProfile;

import java.util.Objects;

/**
 * Lives handed out or taken back by a blessing, rather than spent by dying.
 *
 * <p>A blessing that grants an extra life is the one boon that cannot be a live effect
 * recomputed on every join. Lives are a counter that goes down, so "you have one more" has to
 * be a single change made at the moment the blessing lands - not something applied on attach,
 * which happens on every login, every reload and every admin grant, and would hand out an
 * unbounded supply of lives to anyone who reconnected.
 *
 * <p>That makes granting and revoking a matched pair, and getting the pair wrong in either
 * direction is permanent from the player's side. Hence: one place, in core, with tests.
 */
public final class BoonLives {

    private BoonLives() {
    }

    /**
     * Adds the lives a blessing carries.
     *
     * <p>Refused outright for someone already in Limbo. Their life count is zero because they
     * ran out, and quietly setting it to one would leave a player who is flagged as dead,
     * held in the fog by the login rule, and carrying a life they cannot spend - a state no
     * other path in the plugin can produce and none of them know how to resolve. A blessing
     * is not a way out of Limbo; the expedition is.
     *
     * @return true if the lives were actually added
     */
    public static boolean grant(PlayerProfile profile, int lives) {
        Objects.requireNonNull(profile, "profile");
        if (lives <= 0 || profile.inLimbo()) {
            return false;
        }
        profile.setLivesRemaining(profile.livesRemaining() + lives);
        return true;
    }

    /**
     * Takes back the lives a blessing carried, when it is cleared or replaced.
     *
     * <p>Floors at one rather than at zero. Zero lives is not a number - it is the state that
     * means "in Limbo", reached only by dying, and an admin correcting a blessing must not be
     * able to condemn somebody by accident. If they should be in Limbo, send them there.
     *
     * @return true if any lives were actually taken
     */
    public static boolean revoke(PlayerProfile profile, int lives) {
        Objects.requireNonNull(profile, "profile");
        if (lives <= 0 || profile.inLimbo()) {
            return false;
        }
        int before = profile.livesRemaining();
        profile.setLivesRemaining(Math.max(1, before - lives));
        return profile.livesRemaining() != before;
    }
}
