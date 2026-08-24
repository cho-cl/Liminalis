package com.liminalis.core.profile;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerProfileTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void newProfileIsAliveWithFullLivesAndHasNotBeenRolled() {
        PlayerProfile profile = PlayerProfile.createNew(ID, "Aero", 3);

        assertThat(profile.id()).isEqualTo(ID);
        assertThat(profile.lastKnownName()).isEqualTo("Aero");
        assertThat(profile.livesRemaining()).isEqualTo(3);
        assertThat(profile.totalDeaths()).isZero();
        assertThat(profile.inLimbo()).isFalse();
        assertThat(profile.firstJoinComplete()).isFalse();
    }
}
