package com.liminalis.core.ability;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One ladder for every ability: use it, level up, unlock the next power.
 *
 * <p>The rule is meant to be sayable in a sentence, so these tests are mostly about the
 * boundaries - the places where an off-by-one costs somebody a power they earned.
 */
class AbilityLevelsTest {

    /** The shipped ladder. */
    private static final List<Integer> LADDER = List.of(25, 75, 150, 300);

    @Test
    void everybodyStartsAtLevelOne() {
        assertThat(AbilityLevels.levelFor(0, LADDER)).isEqualTo(1);
    }

    @Test
    void aLevelOpensExactlyOnItsThreshold() {
        // Off by one here silently costs somebody the power they just earned.
        assertThat(AbilityLevels.levelFor(24, LADDER)).isEqualTo(1);
        assertThat(AbilityLevels.levelFor(25, LADDER)).isEqualTo(2);
        assertThat(AbilityLevels.levelFor(74, LADDER)).isEqualTo(2);
        assertThat(AbilityLevels.levelFor(75, LADDER)).isEqualTo(3);
        assertThat(AbilityLevels.levelFor(150, LADDER)).isEqualTo(4);
        assertThat(AbilityLevels.levelFor(300, LADDER)).isEqualTo(5);
    }

    @Test
    void theLadderStopsAtTheTop() {
        assertThat(AbilityLevels.levelFor(10_000, LADDER)).isEqualTo(5);
        assertThat(AbilityLevels.maxLevel(LADDER)).isEqualTo(5);
    }

    @Test
    void levelsCannotBeSkipped() {
        // Walks in order, so every level between one and the current one is also open. This
        // is the whole promise of "level N means powers one through N".
        for (int uses = 0; uses <= 400; uses++) {
            int level = AbilityLevels.levelFor(uses, LADDER);
            assertThat(level).isBetween(1, 5);
            assertThat(level).isGreaterThanOrEqualTo(AbilityLevels.levelFor(uses - 1, LADDER));
        }
    }

    @Test
    void negativeUsesStillLeaveYouUsable() {
        assertThat(AbilityLevels.levelFor(-50, LADDER)).isEqualTo(1);
    }

    @Test
    void anEmptyLadderPinsEveryoneAtOne() {
        assertThat(AbilityLevels.levelFor(9_999, List.of())).isEqualTo(1);
        assertThat(AbilityLevels.maxLevel(List.of())).isEqualTo(1);
        assertThat(AbilityLevels.usesToNext(9_999, List.of())).isZero();
    }

    // ------------------------------------------------------------------- what to show

    @Test
    void tellsYouHowManyMoreRatherThanWhereYouAre() {
        assertThat(AbilityLevels.usesToNext(0, LADDER)).isEqualTo(25);
        assertThat(AbilityLevels.usesToNext(20, LADDER)).isEqualTo(5);
        assertThat(AbilityLevels.usesToNext(25, LADDER)).isEqualTo(50);
    }

    @Test
    void thereIsNothingLeftToDoAtTheTop() {
        assertThat(AbilityLevels.usesToNext(300, LADDER)).isZero();
        assertThat(AbilityLevels.progressToNext(300, LADDER)).isEqualTo(1.0);
    }

    @Test
    void progressFillsAcrossEachLevelRatherThanFromZero() {
        // Halfway from 25 to 75 is halfway through level two - not 50/75 of the way to
        // level three, which would make every bar after the first crawl.
        assertThat(AbilityLevels.progressToNext(50, LADDER)).isEqualTo(0.5);
        assertThat(AbilityLevels.progressToNext(25, LADDER)).isZero();
    }

    // --------------------------------------------------- an admin setting a level

    @Test
    void settingALevelGivesTheUsesThatEarnIt() {
        // Otherwise a player is level four by decree and level one by arithmetic, and the
        // next use drops them back.
        for (int level = 1; level <= 5; level++) {
            int uses = AbilityLevels.usesForLevel(level, LADDER);
            assertThat(AbilityLevels.levelFor(uses, LADDER))
                    .as("level %d round-trips", level)
                    .isEqualTo(level);
        }
    }

    @Test
    void settingALevelOffTheLadderIsClamped() {
        assertThat(AbilityLevels.usesForLevel(99, LADDER)).isEqualTo(300);
        assertThat(AbilityLevels.usesForLevel(-4, LADDER)).isZero();
    }

    // --------------------------------------------------- spending what the Singularity drops

    @Test
    void spendsOnlyWhatTheNextLevelNeeds() {
        // 25 uses off the next level, a shard is worth 25, and the player is holding sixty.
        // One shard. Burning the other fifty-nine is not generosity.
        assertThat(AbilityLevels.shardsToSpend(25, 25, 60)).isEqualTo(1);
        assertThat(AbilityLevels.shardsToSpend(50, 25, 60)).isEqualTo(2);
    }

    @Test
    void roundsUpBecauseAPartialShardBuysNothing() {
        assertThat(AbilityLevels.shardsToSpend(1, 25, 60)).isEqualTo(1);
        assertThat(AbilityLevels.shardsToSpend(26, 25, 60)).isEqualTo(2);
        assertThat(AbilityLevels.shardsToSpend(49, 25, 60)).isEqualTo(2);
    }

    @Test
    void neverSpendsMoreThanIsHeld() {
        assertThat(AbilityLevels.shardsToSpend(300, 25, 3)).isEqualTo(3);
    }

    @Test
    void spendsNothingWhenThereIsNothingToBuy() {
        assertThat(AbilityLevels.shardsToSpend(0, 25, 60)).isZero();
        assertThat(AbilityLevels.shardsToSpend(-5, 25, 60)).isZero();
        assertThat(AbilityLevels.shardsToSpend(50, 25, 0)).isZero();
    }

    @Test
    void aWorthlessShardSpendsNothingRatherThanEverything() {
        // Guards a config of 0: dividing by it would throw, and treating it as "spend the
        // stack" would silently eat every shard the player had for no progress at all.
        assertThat(AbilityLevels.shardsToSpend(50, 0, 60)).isZero();
    }

    @Test
    void spendingNeverOvershootsTheTopOfTheLadder() {
        // At 290 of 300 uses, ten short, one shard finishes it and the rest stay in the bag.
        int toNext = AbilityLevels.usesToNext(290, LADDER);
        assertThat(AbilityLevels.shardsToSpend(toNext, 25, 40)).isEqualTo(1);
    }

    @Test
    void theCounterIsSharedRatherThanNamespaced() {
        // The old system namespaced a counter per ability. There is nothing to namespace
        // now, and that is the simplification.
        assertThat(AbilityLevels.USES).doesNotContain("priest");
    }
}
