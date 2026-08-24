package com.liminalis.core.injury;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The roster must be able to answer for every kind of harm.
 *
 * <p>The bug this guards is silent by construction: the classifier decides severity from the
 * damage alone, so a category with nothing behind it produces a wound verdict that the roster
 * cannot fill, and the code returns having done nothing at all. Nobody sees an error - the
 * player simply takes the worst hit of their life and walks away unmarked.
 */
class InjuryCoverageTest {

    private static InjuryCoverage.Entry entry(String id,
                                              InjurySeverity severity,
                                              DamageCategory... causes) {
        return new InjuryCoverage.Entry(id, Set.of(causes), severity);
    }

    /** A roster with both severities behind every category. */
    private static List<InjuryCoverage.Entry> whole() {
        List<InjuryCoverage.Entry> roster = new java.util.ArrayList<>();
        for (DamageCategory category : DamageCategory.values()) {
            roster.add(entry("hurt_" + category, InjurySeverity.INJURY, category));
            roster.add(entry("maimed_" + category, InjurySeverity.MORTAL_WOUND, category));
        }
        return roster;
    }

    @Test
    void aWholeRosterHasNoGaps() {
        assertThat(InjuryCoverage.gaps(whole())).isEmpty();
    }

    @Test
    void aMissingMortalWoundIsAGap() {
        List<InjuryCoverage.Entry> roster = whole();
        roster.removeIf(e -> e.id().equals("maimed_" + DamageCategory.PIERCING));

        assertThat(InjuryCoverage.gaps(roster))
                .singleElement().asString()
                .contains("PIERCING")
                .contains("MORTAL_WOUND");
    }

    @Test
    void aMissingOrdinaryWoundIsAGap() {
        List<InjuryCoverage.Entry> roster = whole();
        roster.removeIf(e -> e.id().equals("hurt_" + DamageCategory.FROST));

        assertThat(InjuryCoverage.gaps(roster))
                .singleElement().asString()
                .contains("FROST")
                .contains("INJURY");
    }

    @Test
    void anEmptyRosterIsNothingButGaps() {
        // Two per category: one ordinary, one mortal.
        assertThat(InjuryCoverage.gaps(List.of()))
                .hasSize(DamageCategory.values().length * 2);
    }

    @Test
    void everyGapIsReportedAtOnceSoOneRestartFixesThemAll() {
        List<InjuryCoverage.Entry> roster = whole();
        roster.removeIf(e -> e.id().startsWith("maimed_"));

        assertThat(InjuryCoverage.gaps(roster))
                .hasSize(DamageCategory.values().length);
    }

    @Test
    void aWoundThatCoversSeveralCausesCountsForAllOfThem() {
        List<InjuryCoverage.Entry> roster = List.of(
                entry("bleeding", InjurySeverity.INJURY,
                        DamageCategory.SLASHING, DamageCategory.PIERCING),
                entry("lost_arm", InjurySeverity.MORTAL_WOUND,
                        DamageCategory.SLASHING, DamageCategory.PIERCING));

        assertThat(InjuryCoverage.gaps(roster))
                .noneMatch(gap -> gap.startsWith("SLASHING"))
                .noneMatch(gap -> gap.startsWith("PIERCING"));
    }

    @Test
    void requireRefusesAnIncompleteRoster() {
        assertThatThrownBy(() -> InjuryCoverage.require(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gap");
    }

    @Test
    void requireAcceptsAWholeOne() {
        InjuryCoverage.require(whole());
    }
}
