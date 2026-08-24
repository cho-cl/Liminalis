package com.liminalis.core.lives;

import com.liminalis.core.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three lives, no more no less, and the third death is the one that ends you.
 *
 * <p>This is the most consequential logic in the plugin: getting it wrong either takes
 * somebody's last life when it should not have, or fails to take one when it should. Both are
 * permanent from the player's point of view, so all of it is pinned down here rather than
 * discovered on the server.
 */
class LifeRulesTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final long NOW = 1_700_000_000_000L;
    private static final String MARK = "mark_of_return";

    private static final LifeSettings PVP_COUNTS = new LifeSettings(3, true);
    private static final LifeSettings PVP_FREE = new LifeSettings(3, false);

    private PlayerProfile alive(int lives) {
        PlayerProfile profile = PlayerProfile.createNew(ID, "Aero", 3);
        profile.setLivesRemaining(lives);
        return profile;
    }

    // ---------------------------------------------------------------------- life cost

    @Test
    void anOrdinaryDeathCostsOneLife() {
        PlayerProfile profile = alive(3);

        DeathVerdict verdict = LifeRules.recordDeath(profile, DeathCause.MOB, PVP_COUNTS, NOW);

        assertThat(verdict).isEqualTo(DeathVerdict.LIFE_SPENT);
        assertThat(profile.livesRemaining()).isEqualTo(2);
        assertThat(profile.inLimbo()).isFalse();
    }

    @Test
    void spendingTheLastLifeSendsThePlayerToLimbo() {
        PlayerProfile profile = alive(1);

        DeathVerdict verdict = LifeRules.recordDeath(profile, DeathCause.ENVIRONMENT,
                PVP_COUNTS, NOW);

        assertThat(verdict).isEqualTo(DeathVerdict.FELL_TO_LIMBO);
        assertThat(profile.livesRemaining()).isZero();
        assertThat(profile.inLimbo()).isTrue();
    }

    @Test
    void theThirdDeathIsTheOneThatEndsYou() {
        PlayerProfile profile = alive(3);

        assertThat(LifeRules.recordDeath(profile, DeathCause.MOB, PVP_COUNTS, NOW))
                .isEqualTo(DeathVerdict.LIFE_SPENT);
        assertThat(LifeRules.recordDeath(profile, DeathCause.MOB, PVP_COUNTS, NOW))
                .isEqualTo(DeathVerdict.LIFE_SPENT);
        assertThat(LifeRules.recordDeath(profile, DeathCause.MOB, PVP_COUNTS, NOW))
                .isEqualTo(DeathVerdict.FELL_TO_LIMBO);
    }

    @Test
    void fallingToLimboRecordsWhenItHappened() {
        PlayerProfile profile = alive(1);

        LifeRules.recordDeath(profile, DeathCause.MOB, PVP_COUNTS, NOW);

        assertThat(profile.limboSince()).isEqualTo(NOW);
    }

    @Test
    void dyingInLimboChangesNothing() {
        // There is no way to die in Limbo, but if something ever manages it, it must not
        // push lives below zero or re-stamp the arrival time.
        PlayerProfile profile = alive(0);
        profile.setInLimbo(true);
        profile.setLimboSince(NOW);

        DeathVerdict verdict = LifeRules.recordDeath(profile, DeathCause.MOB, PVP_COUNTS,
                NOW + 5_000L);

        assertThat(verdict).isEqualTo(DeathVerdict.IGNORED);
        assertThat(profile.livesRemaining()).isZero();
        assertThat(profile.limboSince()).isEqualTo(NOW);
    }

    @Test
    void livesNeverGoBelowZero() {
        PlayerProfile profile = alive(0);

        LifeRules.recordDeath(profile, DeathCause.MOB, PVP_COUNTS, NOW);

        assertThat(profile.livesRemaining()).isZero();
    }

    // ------------------------------------------------------------------ what counts

    @Test
    void anAdminKillIsExcused() {
        // Operators kill people to test things and to fix things. That must never quietly
        // cost somebody a life.
        PlayerProfile profile = alive(3);

        DeathVerdict verdict = LifeRules.recordDeath(profile, DeathCause.ADMIN, PVP_COUNTS, NOW);

        assertThat(verdict).isEqualTo(DeathVerdict.IGNORED);
        assertThat(profile.livesRemaining()).isEqualTo(3);
    }

    @Test
    void beingKilledByAnotherPlayerCountsWhenPvpDeathsAreOn() {
        PlayerProfile profile = alive(3);

        DeathVerdict verdict = LifeRules.recordDeath(profile, DeathCause.PLAYER, PVP_COUNTS, NOW);

        assertThat(verdict).isEqualTo(DeathVerdict.LIFE_SPENT);
        assertThat(profile.livesRemaining()).isEqualTo(2);
    }

    @Test
    void beingKilledByAnotherPlayerIsFreeWhenPvpDeathsAreOff() {
        PlayerProfile profile = alive(3);

        DeathVerdict verdict = LifeRules.recordDeath(profile, DeathCause.PLAYER, PVP_FREE, NOW);

        assertThat(verdict).isEqualTo(DeathVerdict.IGNORED);
        assertThat(profile.livesRemaining()).isEqualTo(3);
    }

    @Test
    void turningOffPvpDeathsDoesNotMakeEverythingElseFree() {
        // The toggle must be narrow. Switching it off to stop friends costing each other
        // lives must not accidentally make lava and creepers harmless too.
        PlayerProfile mobbed = alive(3);
        PlayerProfile drowned = alive(3);
        PlayerProfile unknown = alive(3);

        LifeRules.recordDeath(mobbed, DeathCause.MOB, PVP_FREE, NOW);
        LifeRules.recordDeath(drowned, DeathCause.ENVIRONMENT, PVP_FREE, NOW);
        LifeRules.recordDeath(unknown, DeathCause.UNKNOWN, PVP_FREE, NOW);

        assertThat(mobbed.livesRemaining()).isEqualTo(2);
        assertThat(drowned.livesRemaining()).isEqualTo(2);
        assertThat(unknown.livesRemaining()).isEqualTo(2);
    }

    @Test
    void onlyDeathsThatCostALifeAreCounted() {
        PlayerProfile profile = alive(3);

        LifeRules.recordDeath(profile, DeathCause.MOB, PVP_COUNTS, NOW);
        LifeRules.recordDeath(profile, DeathCause.ADMIN, PVP_COUNTS, NOW);

        assertThat(profile.totalDeaths()).isEqualTo(1);
    }

    // --------------------------------------------------------------------- revival

    @Test
    void revivalRestoresLivesClearsLimboAndLeavesAMark() {
        PlayerProfile profile = alive(0);
        profile.setInLimbo(true);
        profile.setLimboSince(NOW);

        boolean revived = LifeRules.revive(profile, 2, MARK);

        assertThat(revived).isTrue();
        assertThat(profile.livesRemaining()).isEqualTo(2);
        assertThat(profile.inLimbo()).isFalse();
        assertThat(profile.limboSince()).isZero();
        assertThat(profile.markIds()).contains(MARK);
    }

    @Test
    void revivingSomeoneWhoIsNotInLimboDoesNothing() {
        // Guards the admin command: reviving a living player must not hand them lives.
        PlayerProfile profile = alive(1);

        boolean revived = LifeRules.revive(profile, 2, MARK);

        assertThat(revived).isFalse();
        assertThat(profile.livesRemaining()).isEqualTo(1);
        assertThat(profile.markIds()).isEmpty();
    }

    @Test
    void theMarkIsPermanentAndSurvivesAReturnToLimbo() {
        PlayerProfile profile = alive(0);
        profile.setInLimbo(true);
        LifeRules.revive(profile, 2, MARK);

        LifeRules.recordDeath(profile, DeathCause.MOB, PVP_COUNTS, NOW);
        LifeRules.recordDeath(profile, DeathCause.MOB, PVP_COUNTS, NOW);

        assertThat(profile.inLimbo()).isTrue();
        assertThat(profile.markIds()).contains(MARK);
    }

    @Test
    void beingRevivedTwiceDoesNotStackTheMark() {
        PlayerProfile profile = alive(0);
        profile.setInLimbo(true);
        LifeRules.revive(profile, 2, MARK);
        profile.setLivesRemaining(0);
        profile.setInLimbo(true);

        LifeRules.revive(profile, 2, MARK);

        assertThat(profile.markIds()).containsExactly(MARK);
    }
}
