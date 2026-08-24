package com.liminalis.core.rescue;

import com.liminalis.core.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Crossing into the grey to fetch someone, and what it costs if you stay too long.
 *
 * <p>The rescue is the only part of the design where a living player can be lost without
 * dying, so these rules get pinned down rather than trusted. Getting the expiry wrong either
 * makes rescue free - and then nobody is ever really gone - or strands someone who should
 * have made it back.
 */
class RescueRulesTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String MARK = "mark_of_return";
    private static final long NOW = 1_700_000_000_000L;

    private PlayerProfile living(int lives) {
        PlayerProfile profile = PlayerProfile.createNew(ID, "Aero", 3);
        profile.setLivesRemaining(lives);
        return profile;
    }

    private PlayerProfile lost() {
        PlayerProfile profile = PlayerProfile.createNew(ID, "Lost", 3);
        profile.setLivesRemaining(0);
        profile.setInLimbo(true);
        profile.setLimboSince(NOW);
        return profile;
    }

    // -------------------------------------------------------------------- who may cross

    @Test
    void aLivingPlayerMayCross() {
        assertThat(RescueRules.mayCross(living(2))).isTrue();
    }

    @Test
    void someoneAlreadyInLimboMayNotCross() {
        // They are not going on a rescue. They are the rescue.
        assertThat(RescueRules.mayCross(lost())).isFalse();
    }

    @Test
    void someoneOnTheirLastLifeMayStillCross() {
        // Deliberately allowed. It is the most dangerous thing a player can do in this
        // server, and forbidding it would take away the only real sacrifice available.
        assertThat(RescueRules.mayCross(living(1))).isTrue();
    }

    // ------------------------------------------------------------------------- returning

    @Test
    void comingBackInTimeCostsNothing() {
        PlayerProfile rescuer = living(2);

        assertThat(RescueRules.returnFrom(rescuer)).isEqualTo(CrossingOutcome.RETURNED);
        assertThat(rescuer.livesRemaining()).isEqualTo(2);
        assertThat(rescuer.inLimbo()).isFalse();
    }

    // -------------------------------------------------------------------------- expiring

    @Test
    void runningOutOfTimeCostsALife() {
        PlayerProfile rescuer = living(3);

        assertThat(RescueRules.expire(rescuer, NOW)).isEqualTo(CrossingOutcome.RETURNED_DIMINISHED);
        assertThat(rescuer.livesRemaining()).isEqualTo(2);
        assertThat(rescuer.inLimbo()).isFalse();
    }

    @Test
    void runningOutOfTimeOnYourLastLifeStrandsYou() {
        // The whole risk of the rescue, in one line: go in with one life left and stay too
        // long, and you have joined the person you went to fetch.
        PlayerProfile rescuer = living(1);

        assertThat(RescueRules.expire(rescuer, NOW)).isEqualTo(CrossingOutcome.STRANDED);
        assertThat(rescuer.livesRemaining()).isZero();
        assertThat(rescuer.inLimbo()).isTrue();
        assertThat(rescuer.limboSince()).isEqualTo(NOW);
    }

    @Test
    void expiringNeverDrivesLivesBelowZero() {
        PlayerProfile rescuer = living(0);

        RescueRules.expire(rescuer, NOW);

        assertThat(rescuer.livesRemaining()).isZero();
    }

    // -------------------------------------------------------------------------- retrieval

    @Test
    void carryingSomeoneOutRevivesThem() {
        PlayerProfile carried = lost();

        assertThat(RescueRules.retrieve(carried, 2, MARK)).isTrue();
        assertThat(carried.inLimbo()).isFalse();
        assertThat(carried.livesRemaining()).isEqualTo(2);
        assertThat(carried.markIds()).contains(MARK);
    }

    @Test
    void retrievingSomeoneWhoIsNotLostDoesNothing() {
        PlayerProfile notLost = living(2);

        assertThat(RescueRules.retrieve(notLost, 2, MARK)).isFalse();
        assertThat(notLost.livesRemaining()).isEqualTo(2);
        assertThat(notLost.markIds()).isEmpty();
    }

    @Test
    void aRetrievedPlayerArrivesWithNoGhostCooldownHangingOver() {
        // They are not a ghost any more. A cooldown from their time in the grey following
        // them back into the living world would be meaningless and confusing.
        PlayerProfile carried = lost();
        carried.setGhostVisitCooldownUntil(NOW + 900_000L);

        RescueRules.retrieve(carried, 2, MARK);

        assertThat(carried.ghostVisitCooldownUntil()).isZero();
    }
}
