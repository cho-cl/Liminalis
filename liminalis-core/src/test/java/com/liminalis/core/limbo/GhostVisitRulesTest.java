package com.liminalis.core.limbo;

import com.liminalis.core.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dead get five minutes back among the living, once every fifteen.
 *
 * <p>The cooldown is stored on the profile rather than in memory on purpose: it has to
 * survive a logout and a restart, or leaving and rejoining would be a way to haunt the world
 * continuously.
 */
class GhostVisitRulesTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final long NOW = 1_700_000_000_000L;

    private static final long FIVE_MINUTES = 5 * 60 * 1000L;
    private static final long FIFTEEN_MINUTES = 15 * 60 * 1000L;
    private static final GhostVisitSettings SETTINGS =
            new GhostVisitSettings(FIVE_MINUTES, FIFTEEN_MINUTES);

    private PlayerProfile inLimbo() {
        PlayerProfile profile = PlayerProfile.createNew(ID, "Aero", 3);
        profile.setLivesRemaining(0);
        profile.setInLimbo(true);
        profile.setLimboSince(NOW);
        return profile;
    }

    @Test
    void someoneNewlyInLimboCanVisitStraightAway() {
        assertThat(GhostVisitRules.canVisit(inLimbo(), NOW)).isTrue();
    }

    @Test
    void theLivingCannotHaunt() {
        PlayerProfile living = PlayerProfile.createNew(ID, "Aero", 3);

        assertThat(GhostVisitRules.canVisit(living, NOW)).isFalse();
    }

    @Test
    void beginningAVisitReturnsWhenItEnds() {
        PlayerProfile profile = inLimbo();

        long endsAt = GhostVisitRules.beginVisit(profile, SETTINGS, NOW);

        assertThat(endsAt).isEqualTo(NOW + FIVE_MINUTES);
    }

    @Test
    void aSecondVisitCannotBeStartedDuringTheFirst() {
        PlayerProfile profile = inLimbo();
        GhostVisitRules.beginVisit(profile, SETTINGS, NOW);

        assertThat(GhostVisitRules.canVisit(profile, NOW + 60_000L)).isFalse();
    }

    @Test
    void theCooldownRunsFromTheEndOfTheVisitNotTheStart() {
        // Otherwise a 15 minute cooldown on a 5 minute visit is really only 10 minutes off.
        PlayerProfile profile = inLimbo();
        GhostVisitRules.beginVisit(profile, SETTINGS, NOW);

        long justBeforeReady = NOW + FIVE_MINUTES + FIFTEEN_MINUTES - 1;
        assertThat(GhostVisitRules.canVisit(profile, justBeforeReady)).isFalse();
    }

    @Test
    void theyCanVisitAgainOnceTheCooldownExpires() {
        PlayerProfile profile = inLimbo();
        GhostVisitRules.beginVisit(profile, SETTINGS, NOW);

        assertThat(GhostVisitRules.canVisit(profile, NOW + FIVE_MINUTES + FIFTEEN_MINUTES))
                .isTrue();
    }

    @Test
    void reportsHowLongIsLeftOnTheCooldown() {
        PlayerProfile profile = inLimbo();
        GhostVisitRules.beginVisit(profile, SETTINGS, NOW);

        long after = NOW + FIVE_MINUTES + (5 * 60 * 1000L);
        assertThat(GhostVisitRules.cooldownRemainingMillis(profile, after))
                .isEqualTo(10 * 60 * 1000L);
    }

    @Test
    void aReadyPlayerHasNoTimeLeftRatherThanANegativeOne() {
        PlayerProfile profile = inLimbo();

        assertThat(GhostVisitRules.cooldownRemainingMillis(profile, NOW)).isZero();
    }

    @Test
    void beingRevivedAndFallingBackDoesNotInheritAnOldCooldown() {
        // The cooldown is about haunting, not about the person. Someone rescued and lost
        // again should not arrive already locked out.
        PlayerProfile profile = inLimbo();
        GhostVisitRules.beginVisit(profile, SETTINGS, NOW);

        GhostVisitRules.clearCooldown(profile);

        assertThat(GhostVisitRules.canVisit(profile, NOW + 1)).isTrue();
    }
}
