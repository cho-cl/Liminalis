package com.liminalis.core.profile;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traits, blessings, curses, marks and abilities are all modifiers, but they live in
 * different slots on the profile. This is the one place that knows how to read them all out.
 */
class ProfileModifierIdsTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private PlayerProfile profile() {
        return PlayerProfile.createNew(ID, "Aero", 3);
    }

    @Test
    void listsEverySlotAPlayerCanCarry() {
        PlayerProfile p = profile();
        p.addTrait("resilience");
        p.addTrait("coward");
        p.setBlessingId("ironblood");
        p.setCurseId("hollow");
        p.addMark("mark_of_return");
        p.setAbilityId("priest");

        assertThat(ProfileModifierIds.referencedBy(p)).containsExactlyInAnyOrder(
                "resilience", "coward", "ironblood", "hollow", "mark_of_return", "priest");
    }

    @Test
    void aBrandNewPlayerReferencesNothing() {
        assertThat(ProfileModifierIds.referencedBy(profile())).isEmpty();
    }

    @Test
    void emptySlotsAreSkippedRatherThanListedAsNull() {
        // A null ability id must not become a null entry that blows up on lookup later.
        PlayerProfile p = profile();
        p.addTrait("resilience");

        assertThat(ProfileModifierIds.referencedBy(p))
                .containsExactly("resilience")
                .doesNotContainNull();
    }

    @Test
    void theSameIdInTwoSlotsIsOnlyListedOnce() {
        // Nothing should ever be both a trait and a curse, but if a bad admin command makes
        // it so, attaching the same modifier twice would double its effect.
        PlayerProfile p = profile();
        p.addTrait("hollow");
        p.setCurseId("hollow");

        assertThat(ProfileModifierIds.referencedBy(p)).containsExactly("hollow");
    }
}
