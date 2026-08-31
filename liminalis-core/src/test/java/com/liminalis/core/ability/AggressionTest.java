package com.liminalis.core.ability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What drones are allowed to do, and to whom.
 *
 * <p>Worth testing rather than eyeballing because one of these settings can take a life off
 * another player, and the difference between the four is expressed nowhere except here.
 */
class AggressionTest {

    @Test
    void onlyOneSettingEverTouchesAPlayer() {
        assertThat(Aggression.values())
                .filteredOn(Aggression::allowsPlayers)
                .containsExactly(Aggression.EVERYTHING);
    }

    @Test
    void passiveDoesNothingAtAll() {
        assertThat(Aggression.PASSIVE.retaliates()).isFalse();
        assertThat(Aggression.PASSIVE.initiates()).isFalse();
        assertThat(Aggression.PASSIVE.allowsPlayers()).isFalse();
    }

    @Test
    void defensiveAnswersButNeverStarts() {
        assertThat(Aggression.DEFENSIVE.retaliates()).isTrue();
        assertThat(Aggression.DEFENSIVE.initiates()).isFalse();
        assertThat(Aggression.DEFENSIVE.allowsPlayers()).isFalse();
    }

    @Test
    void hostilesStartFightsButNotWithPeople() {
        assertThat(Aggression.HOSTILES.initiates()).isTrue();
        assertThat(Aggression.HOSTILES.allowsPlayers()).isFalse();
    }

    @Test
    void theCycleRunsHarmlessToDangerousAndWrapsToHarmless() {
        // Wrapping to PASSIVE rather than to EVERYTHING matters: a player idly cycling the
        // toggle should never arrive at "attack other people" by accident.
        assertThat(Aggression.PASSIVE.next()).isEqualTo(Aggression.DEFENSIVE);
        assertThat(Aggression.DEFENSIVE.next()).isEqualTo(Aggression.HOSTILES);
        assertThat(Aggression.HOSTILES.next()).isEqualTo(Aggression.EVERYTHING);
        assertThat(Aggression.EVERYTHING.next()).isEqualTo(Aggression.PASSIVE);
    }

    @Test
    void cyclingAllTheWayRoundReturnsWhereItStarted() {
        Aggression mode = Aggression.standard();
        for (int i = 0; i < Aggression.values().length; i++) {
            mode = mode.next();
        }
        assertThat(mode).isEqualTo(Aggression.standard());
    }

    @Test
    void theDefaultIsTheSaneOne() {
        // Summoning drones should not immediately have them picking fights on your behalf.
        assertThat(Aggression.standard()).isEqualTo(Aggression.DEFENSIVE);
        assertThat(Aggression.standard().initiates()).isFalse();
    }

    @Test
    void idsRoundTrip() {
        for (Aggression mode : Aggression.values()) {
            assertThat(Aggression.byId(mode.id())).isEqualTo(mode);
        }
    }

    @Test
    void anUnrecognisedIdFallsToTheSafeDefaultRatherThanTheDangerousOne() {
        // A profile written by a future build, or edited by hand, must not silently arm
        // somebody's drones against other players.
        assertThat(Aggression.byId("nonsense")).isEqualTo(Aggression.standard());
        assertThat(Aggression.byId("nonsense").allowsPlayers()).isFalse();
    }
}
