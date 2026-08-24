package com.liminalis.core.lives;

import com.liminalis.core.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one boon effect that is a state change rather than a live recompute.
 *
 * <p>Every other modifier is applied fresh on every join, so getting it wrong costs a
 * relogin. This one edits a counter, so getting it wrong costs somebody a life - or hands
 * them an endless supply of them.
 */
class BoonLivesTest {

    private static PlayerProfile alive(int lives) {
        return PlayerProfile.createNew(UUID.randomUUID(), "Tester", lives);
    }

    private static PlayerProfile inLimbo() {
        PlayerProfile profile = PlayerProfile.createNew(UUID.randomUUID(), "Tester", 0);
        profile.setInLimbo(true);
        return profile;
    }

    @Test
    void grantingAddsALife() {
        PlayerProfile profile = alive(3);

        assertThat(BoonLives.grant(profile, 1)).isTrue();
        assertThat(profile.livesRemaining()).isEqualTo(4);
    }

    @Test
    void revokingTakesItBack() {
        PlayerProfile profile = alive(4);

        assertThat(BoonLives.revoke(profile, 1)).isTrue();
        assertThat(profile.livesRemaining()).isEqualTo(3);
    }

    @Test
    void grantAndRevokeAreExactlyReversible() {
        // The pair has to balance, or repeatedly regranting a blessing inflates the count.
        PlayerProfile profile = alive(3);
        for (int i = 0; i < 20; i++) {
            BoonLives.grant(profile, 1);
            BoonLives.revoke(profile, 1);
        }
        assertThat(profile.livesRemaining()).isEqualTo(3);
    }

    @Test
    void revokingNeverCondemnsSomeoneToLimbo() {
        // Zero lives is not a number, it is the state that means "in Limbo". Clearing a
        // blessing must not be able to put someone there by accident.
        PlayerProfile profile = alive(1);

        BoonLives.revoke(profile, 1);

        assertThat(profile.livesRemaining()).isEqualTo(1);
        assertThat(profile.inLimbo()).isFalse();
    }

    @Test
    void revokingMoreThanTheyHaveStillLeavesThemOneLife() {
        PlayerProfile profile = alive(2);

        BoonLives.revoke(profile, 99);

        assertThat(profile.livesRemaining()).isEqualTo(1);
    }

    @Test
    void aBlessingIsNotAWayOutOfLimbo() {
        PlayerProfile profile = inLimbo();

        assertThat(BoonLives.grant(profile, 1)).isFalse();
        assertThat(profile.livesRemaining()).isZero();
        assertThat(profile.inLimbo()).isTrue();
    }

    @Test
    void revokingFromSomeoneInLimboChangesNothing() {
        PlayerProfile profile = inLimbo();

        assertThat(BoonLives.revoke(profile, 1)).isFalse();
        assertThat(profile.livesRemaining()).isZero();
    }

    @Test
    void zeroOrNegativeIsANoOp() {
        PlayerProfile profile = alive(3);

        assertThat(BoonLives.grant(profile, 0)).isFalse();
        assertThat(BoonLives.revoke(profile, -2)).isFalse();
        assertThat(profile.livesRemaining()).isEqualTo(3);
    }
}
