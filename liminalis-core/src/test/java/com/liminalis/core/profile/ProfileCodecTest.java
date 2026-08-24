package com.liminalis.core.profile;

import com.liminalis.core.injury.ActiveInjury;
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
        p.setCrossingCooldownUntil(1_700_001_400_000L);
        p.addTrait("resilience");
        p.addTrait("short");
        p.setBlessingId("emberborn");
        p.setCurseId("hollow");
        p.addMark("mark_of_return");
        p.setAbilityId("priest");
        p.setAbilityTier(2);
        p.setFirstJoinComplete(true);
        p.setFirstJoinedAt(1_699_000_000_000L);
        p.setLastSeenAt(1_700_000_500_000L);
        return p;
    }

    // ------------------------------------------------------------------ schema version 4

    @Test
    void aRetiredBlessingIsClearedRatherThanLeftDangling() {
        // Version 4 removed the stat-only blessings. An id with no code behind it would sit
        // in the profile forever, apply nothing, and still be announced on the profile
        // screen as a blessing the player has - which is worse than having none.
        PlayerProfile migrated = codec.fromJson(atVersion3("""
                "blessingId": "ironblood","""));

        assertThat(migrated.blessingId()).isNull();
    }

    @Test
    void aRetiredCurseIsClearedToo() {
        PlayerProfile migrated = codec.fromJson(atVersion3("""
                "curseId": "swiftbane","""));

        assertThat(migrated.curseId()).isNull();
    }

    @Test
    void aBoonThatSurvivedTheCullIsLeftAlone() {
        // Hollow was kept. Clearing it would be exactly the data loss this migration exists
        // to avoid causing.
        PlayerProfile migrated = codec.fromJson(atVersion3("""
                "curseId": "hollow","""));

        assertThat(migrated.curseId()).isEqualTo("hollow");
    }

    @Test
    void migratingAnOldProfileKeepsEverythingElse() {
        PlayerProfile migrated = codec.fromJson(atVersion3("""
                "blessingId": "far_wanderer",
                "traitIds": ["short", "fleet"],
                "totalDeaths": 7,"""));

        assertThat(migrated.blessingId()).isNull();
        assertThat(migrated.traitIds()).containsExactlyInAnyOrder("short", "fleet");
        assertThat(migrated.totalDeaths()).isEqualTo(7);
        assertThat(migrated.livesRemaining()).isEqualTo(2);
    }

    @Test
    void anOldProfileHasNoCrossingCooldown() {
        // Absent reads as zero, which is "may cross now" - right for a profile written
        // before crossings had a cooldown at all.
        assertThat(codec.fromJson(atVersion3("")).crossingCooldownUntil()).isZero();
    }

    /** A version 3 document with whatever extra fields the test cares about spliced in. */
    private static String atVersion3(String extraFields) {
        return """
                {
                  "schemaVersion": 3,
                  "id": "11111111-2222-3333-4444-555555555555",
                  "lastKnownName": "Aero",
                  "livesRemaining": 2,%s
                  "markIds": []
                }
                """.formatted(extraFields);
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
        assertThat(restored.crossingCooldownUntil()).isEqualTo(1_700_001_400_000L);
        assertThat(restored.traitIds()).containsExactlyInAnyOrder("resilience", "short");
        assertThat(restored.blessingId()).isEqualTo("emberborn");
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

    // ---------------------------------------------------------------- injuries (schema 2)

    @Test
    void roundTripsActiveInjuriesWithTheirExpiry() {
        PlayerProfile p = PlayerProfile.createNew(ID, "Aero", 3);
        p.addInjury(new ActiveInjury("bleeding", 1_700_000_600_000L));
        p.addInjury(new ActiveInjury("lost_arm", 0L));

        PlayerProfile restored = codec.fromJson(codec.toJson(p));

        assertThat(restored.injuries()).containsExactly(
                new ActiveInjury("bleeding", 1_700_000_600_000L),
                new ActiveInjury("lost_arm", 0L));
    }

    @Test
    void aProfileWrittenBeforeInjuriesExistedStillLoads() {
        // The migration hook's first real use. A schema 1 profile predates the injuries
        // field entirely, and every player on the server has one - if this throws, nobody
        // can log in after the update.
        String schemaOne = """
            {
              "schemaVersion": 1,
              "id": "11111111-2222-3333-4444-555555555555",
              "lastKnownName": "Aero",
              "livesRemaining": 2,
              "traitIds": ["resilience"],
              "curseId": "hollow"
            }
            """;

        PlayerProfile restored = codec.fromJson(schemaOne);

        assertThat(restored.livesRemaining()).isEqualTo(2);
        assertThat(restored.traitIds()).containsExactly("resilience");
        assertThat(restored.curseId()).isEqualTo("hollow");
        assertThat(restored.injuries()).isEmpty();
    }

    @Test
    void anUpgradedProfileIsWrittenBackAtTheCurrentSchema() {
        String schemaOne = """
            {
              "schemaVersion": 1,
              "id": "11111111-2222-3333-4444-555555555555",
              "lastKnownName": "Aero",
              "livesRemaining": 3
            }
            """;

        String rewritten = codec.toJson(codec.fromJson(schemaOne));

        assertThat(rewritten).contains("\"schemaVersion\": " + ProfileCodec.CURRENT_SCHEMA_VERSION);
    }

    // ------------------------------------------------------ ability progress (schema 3)

    @Test
    void roundTripsAbilityProgressCounters() {
        PlayerProfile p = PlayerProfile.createNew(ID, "Aero", 3);
        p.setAbilityId("priest");
        p.addAbilityProgress("priest.healed", 140);
        p.addAbilityProgress("priest.undead_felled", 12);

        PlayerProfile restored = codec.fromJson(codec.toJson(p));

        assertThat(restored.abilityProgress())
                .containsEntry("priest.healed", 140)
                .containsEntry("priest.undead_felled", 12);
    }

    @Test
    void progressAccumulatesRatherThanReplacing() {
        PlayerProfile p = PlayerProfile.createNew(ID, "Aero", 3);
        p.addAbilityProgress("priest.healed", 100);
        p.addAbilityProgress("priest.healed", 40);

        assertThat(p.abilityProgress()).containsEntry("priest.healed", 140);
    }

    @Test
    void aProfileFromEitherEarlierSchemaStillLoads() {
        // Both migrations walked in one go: schema 1 predates injuries AND ability progress.
        String schemaOne = """
            {
              "schemaVersion": 1,
              "id": "11111111-2222-3333-4444-555555555555",
              "lastKnownName": "Aero",
              "livesRemaining": 2,
              "abilityId": "priest"
            }
            """;

        PlayerProfile restored = codec.fromJson(schemaOne);

        assertThat(restored.abilityId()).isEqualTo("priest");
        assertThat(restored.injuries()).isEmpty();
        assertThat(restored.abilityProgress()).isEmpty();
    }

    @Test
    void aSchemaTwoProfileGainsEmptyProgressRatherThanFailing() {
        String schemaTwo = """
            {
              "schemaVersion": 2,
              "id": "11111111-2222-3333-4444-555555555555",
              "lastKnownName": "Aero",
              "livesRemaining": 3,
              "injuries": [{"id": "bleeding", "expiresAt": 1700000600000}]
            }
            """;

        PlayerProfile restored = codec.fromJson(schemaTwo);

        assertThat(restored.injuries()).hasSize(1);
        assertThat(restored.abilityProgress()).isEmpty();
    }

    @Test
    void refusesMalformedJsonRatherThanReturningAnEmptyProfile() {
        assertThatThrownBy(() -> codec.fromJson("{ this is not json"))
                .isInstanceOf(ProfileCodec.ProfileFormatException.class);
    }
}
