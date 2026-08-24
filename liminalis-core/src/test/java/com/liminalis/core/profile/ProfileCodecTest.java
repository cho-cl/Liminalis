package com.liminalis.core.profile;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The profile file is the only record of who a player is. These tests exist because a
 * silent serialization bug here would erase somebody's identity permanently, and we would
 * not find out until they logged in.
 */
class ProfileCodecTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final ProfileCodec codec = new ProfileCodec();

    private PlayerProfile fullyPopulated() {
        PlayerProfile p = PlayerProfile.createNew(ID, "Aero", 3);
        p.setLastKnownName("AeroRenamed");
        p.setLivesRemaining(1);
        p.setTotalDeaths(2);
        p.setInLimbo(true);
        p.setLimboSince(1_700_000_000_000L);
        p.setGhostVisitCooldownUntil(1_700_000_900_000L);
        p.addTrait("resilience");
        p.addTrait("short");
        p.setBlessingId("ironblood");
        p.setCurseId("hollow");
        p.addMark("mark_of_return");
        p.setAbilityId("priest");
        p.setAbilityTier(2);
        p.setFirstJoinComplete(true);
        p.setFirstJoinedAt(1_699_000_000_000L);
        p.setLastSeenAt(1_700_000_500_000L);
        return p;
    }

    @Test
    void roundTripPreservesEveryField() {
        PlayerProfile original = fullyPopulated();

        PlayerProfile restored = codec.fromJson(codec.toJson(original));

        assertThat(restored.id()).isEqualTo(ID);
        assertThat(restored.lastKnownName()).isEqualTo("AeroRenamed");
        assertThat(restored.livesRemaining()).isEqualTo(1);
        assertThat(restored.totalDeaths()).isEqualTo(2);
        assertThat(restored.inLimbo()).isTrue();
        assertThat(restored.limboSince()).isEqualTo(1_700_000_000_000L);
        assertThat(restored.ghostVisitCooldownUntil()).isEqualTo(1_700_000_900_000L);
        assertThat(restored.traitIds()).containsExactlyInAnyOrder("resilience", "short");
        assertThat(restored.blessingId()).isEqualTo("ironblood");
        assertThat(restored.curseId()).isEqualTo("hollow");
        assertThat(restored.markIds()).containsExactly("mark_of_return");
        assertThat(restored.abilityId()).isEqualTo("priest");
        assertThat(restored.abilityTier()).isEqualTo(2);
        assertThat(restored.firstJoinComplete()).isTrue();
        assertThat(restored.firstJoinedAt()).isEqualTo(1_699_000_000_000L);
        assertThat(restored.lastSeenAt()).isEqualTo(1_700_000_500_000L);
    }

    @Test
    void writesTheCurrentSchemaVersion() {
        String json = codec.toJson(fullyPopulated());

        assertThat(json).contains("\"schemaVersion\": " + ProfileCodec.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void unsetOptionalReferencesSurviveAsNull() {
        PlayerProfile p = PlayerProfile.createNew(ID, "Aero", 3);

        PlayerProfile restored = codec.fromJson(codec.toJson(p));

        assertThat(restored.blessingId()).isNull();
        assertThat(restored.curseId()).isNull();
        assertThat(restored.abilityId()).isNull();
        assertThat(restored.traitIds()).isEmpty();
        assertThat(restored.markIds()).isEmpty();
    }

    @Test
    void ignoresFieldsItDoesNotRecognise() {
        // A profile written by a NEWER build must still load on an older one rather than
        // taking the server down. Forward compatibility is not optional mid-season.
        String json = """
            {
              "schemaVersion": 1,
              "id": "11111111-2222-3333-4444-555555555555",
              "lastKnownName": "Aero",
              "livesRemaining": 3,
              "somethingFromTheFuture": {"nested": [1, 2, 3]}
            }
            """;

        PlayerProfile restored = codec.fromJson(json);

        assertThat(restored.id()).isEqualTo(ID);
        assertThat(restored.livesRemaining()).isEqualTo(3);
    }

    @Test
    void absentCollectionsBecomeEmptyRatherThanNull() {
        String json = """
            {
              "schemaVersion": 1,
              "id": "11111111-2222-3333-4444-555555555555",
              "lastKnownName": "Aero",
              "livesRemaining": 3
            }
            """;

        PlayerProfile restored = codec.fromJson(json);

        assertThat(restored.traitIds()).isEmpty();
        assertThat(restored.markIds()).isEmpty();
    }

    @Test
    void refusesAProfileMissingItsIdentity() {
        // Defaulting a missing id would silently mint a brand new player. Fail loudly.
        String json = """
            { "schemaVersion": 1, "lastKnownName": "Aero", "livesRemaining": 3 }
            """;

        assertThatThrownBy(() -> codec.fromJson(json))
                .isInstanceOf(ProfileCodec.ProfileFormatException.class)
                .hasMessageContaining("id");
    }

    @Test
    void refusesAProfileMissingItsLifeCount() {
        // Defaulting lives would hand someone their lives back, or take them away.
        String json = """
            {
              "schemaVersion": 1,
              "id": "11111111-2222-3333-4444-555555555555",
              "lastKnownName": "Aero"
            }
            """;

        assertThatThrownBy(() -> codec.fromJson(json))
                .isInstanceOf(ProfileCodec.ProfileFormatException.class)
                .hasMessageContaining("livesRemaining");
    }

    @Test
    void refusesASchemaFromTheFutureItCannotUnderstand() {
        String json = """
            {
              "schemaVersion": 9999,
              "id": "11111111-2222-3333-4444-555555555555",
              "lastKnownName": "Aero",
              "livesRemaining": 3
            }
            """;

        assertThatThrownBy(() -> codec.fromJson(json))
                .isInstanceOf(ProfileCodec.ProfileFormatException.class)
                .hasMessageContaining("9999");
    }

    @Test
    void refusesMalformedJsonRatherThanReturningAnEmptyProfile() {
        assertThatThrownBy(() -> codec.fromJson("{ this is not json"))
                .isInstanceOf(ProfileCodec.ProfileFormatException.class);
    }
}
