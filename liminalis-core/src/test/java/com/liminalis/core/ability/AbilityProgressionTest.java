package com.liminalis.core.ability;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * How a hand-written ability opens up over a season.
 *
 * <p>Every ability unlocks through conditions tailored to what it is - the Priest earns its
 * tiers by healing and by putting down the undead, not by a generic ladder that would feel
 * arbitrary attached to a healer. What is shared is the arithmetic: which tier that leaves
 * you on, and how far you are from the next.
 */
class AbilityProgressionTest {

    private static final List<TierRequirement> PRIEST = List.of(
            new TierRequirement(1, "priest.healed", 0),
            new TierRequirement(2, "priest.healed", 200),
            new TierRequirement(3, "priest.undead_felled", 100));

    @Test
    void aNewlyGrantedAbilityStartsAtItsFirstTier() {
        // Tier 1 costs nothing. Handing someone an ability they cannot use yet would make
        // the grant itself feel like a rejection.
        assertThat(AbilityProgression.unlockedTier(PRIEST, Map.of())).isEqualTo(1);
    }

    @Test
    void meetingAThresholdOpensTheNextTier() {
        assertThat(AbilityProgression.unlockedTier(PRIEST, Map.of("priest.healed", 200)))
                .isEqualTo(2);
    }

    @Test
    void theThresholdIsInclusive() {
        assertThat(AbilityProgression.unlockedTier(PRIEST, Map.of("priest.healed", 199)))
                .isEqualTo(1);
        assertThat(AbilityProgression.unlockedTier(PRIEST, Map.of("priest.healed", 200)))
                .isEqualTo(2);
    }

    @Test
    void tiersCannotBeSkipped() {
        // Someone who has felled a hundred undead without healing anyone has done the work
        // for tier three and none of the work for tier two. They get tier one.
        Map<String, Integer> counters = Map.of("priest.undead_felled", 500);

        assertThat(AbilityProgression.unlockedTier(PRIEST, counters)).isEqualTo(1);
    }

    @Test
    void doingEverythingOpensEverything() {
        Map<String, Integer> counters = Map.of(
                "priest.healed", 200, "priest.undead_felled", 100);

        assertThat(AbilityProgression.unlockedTier(PRIEST, counters)).isEqualTo(3);
    }

    @Test
    void progressBeyondTheLastTierStaysAtTheLastTier() {
        Map<String, Integer> counters = Map.of(
                "priest.healed", 99_999, "priest.undead_felled", 99_999);

        assertThat(AbilityProgression.unlockedTier(PRIEST, counters)).isEqualTo(3);
    }

    // -------------------------------------------------------------------------- progress

    @Test
    void reportsHowFarAlongTheNextTierIs() {
        assertThat(AbilityProgression.progressToNext(PRIEST, Map.of("priest.healed", 50)))
                .isCloseTo(0.25, within(1e-9));
    }

    @Test
    void progressIsZeroAtTheStartOfATier() {
        assertThat(AbilityProgression.progressToNext(PRIEST, Map.of()))
                .isCloseTo(0.0, within(1e-9));
    }

    @Test
    void progressIsFullOnceTheLastTierIsReached() {
        // Nothing left to work toward. Reporting a fraction of a tier that does not exist
        // would show a player a bar that never fills.
        Map<String, Integer> counters = Map.of(
                "priest.healed", 200, "priest.undead_felled", 100);

        assertThat(AbilityProgression.progressToNext(PRIEST, counters))
                .isCloseTo(1.0, within(1e-9));
    }

    @Test
    void progressNeverExceedsOne() {
        assertThat(AbilityProgression.progressToNext(PRIEST, Map.of("priest.healed", 10_000)))
                .isLessThanOrEqualTo(1.0);
    }

    @Test
    void namesTheCounterTheNextTierIsWaitingOn() {
        // What /profile shows a player: not "tier 2", but what they have to go and do.
        assertThat(AbilityProgression.nextRequirement(PRIEST, Map.of("priest.healed", 50)))
                .get()
                .isEqualTo(new TierRequirement(2, "priest.healed", 200));
    }

    @Test
    void thereIsNoNextRequirementAtTheTop() {
        Map<String, Integer> counters = Map.of(
                "priest.healed", 200, "priest.undead_felled", 100);

        assertThat(AbilityProgression.nextRequirement(PRIEST, counters)).isEmpty();
    }

    @Test
    void anAbilityWithNoTiersDefinedIsStillUsableAtTierOne() {
        // Guards a half-written ability from locking its owner out entirely.
        assertThat(AbilityProgression.unlockedTier(List.of(), Map.of())).isEqualTo(1);
        assertThat(AbilityProgression.progressToNext(List.of(), Map.of())).isEqualTo(1.0);
    }

    // ------------------------------------------------------------------------- accelerant

    @Test
    void residueConvertsIntoProgress() {
        // The universal accelerant. It exists because an ability gated behind something its
        // owner rarely does would otherwise leave them permanently stuck.
        assertThat(AbilityProgression.progressFromResidue(4, 25)).isEqualTo(100);
    }

    @Test
    void spendingNoResidueBuysNoProgress() {
        assertThat(AbilityProgression.progressFromResidue(0, 25)).isZero();
    }

    @Test
    void residueProgressIsNeverNegative() {
        assertThat(AbilityProgression.progressFromResidue(-5, 25)).isZero();
    }
}
