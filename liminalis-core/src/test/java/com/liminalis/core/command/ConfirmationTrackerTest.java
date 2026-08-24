package com.liminalis.core.command;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards destructive admin commands behind a repeat within a short window.
 *
 * <p>The failure this prevents is mundane and entirely realistic: tab-completing
 * {@code /liminalis trait reroll} onto the wrong name and erasing somebody's identity in a
 * single keystroke.
 */
class ConfirmationTrackerTest {

    private static final long WINDOW_MILLIS = 10_000L;

    private long now = 1_000_000L;

    private ConfirmationTracker tracker() {
        return new ConfirmationTracker(WINDOW_MILLIS, () -> now);
    }

    @Test
    void theFirstAttemptIsHeldForConfirmation() {
        ConfirmationTracker tracker = tracker();

        assertThat(tracker.submit("AERO", "trait reroll Bob")).isFalse();
    }

    @Test
    void repeatingTheSameCommandInsideTheWindowGoesThrough() {
        ConfirmationTracker tracker = tracker();
        tracker.submit("AERO", "trait reroll Bob");

        now += 3_000L;

        assertThat(tracker.submit("AERO", "trait reroll Bob")).isTrue();
    }

    @Test
    void repeatingAfterTheWindowHasToStartAgain() {
        ConfirmationTracker tracker = tracker();
        tracker.submit("AERO", "trait reroll Bob");

        now += WINDOW_MILLIS + 1;

        assertThat(tracker.submit("AERO", "trait reroll Bob")).isFalse();
    }

    @Test
    void confirmingConsumesTheApprovalSoTheNextRunAsksAgain() {
        // Otherwise one confirmation would arm the command indefinitely, and running it a
        // third time by accident would go straight through.
        ConfirmationTracker tracker = tracker();
        tracker.submit("AERO", "trait reroll Bob");
        assertThat(tracker.submit("AERO", "trait reroll Bob")).isTrue();

        assertThat(tracker.submit("AERO", "trait reroll Bob")).isFalse();
    }

    @Test
    void adifferentCommandDoesNotInheritThePendingConfirmation() {
        // Confirming a reroll on Bob must never confirm a reroll on someone else.
        ConfirmationTracker tracker = tracker();
        tracker.submit("AERO", "trait reroll Bob");

        assertThat(tracker.submit("AERO", "trait reroll Alice")).isFalse();
    }

    @Test
    void oneAdminCannotConfirmAnotherAdminsCommand() {
        ConfirmationTracker tracker = tracker();
        tracker.submit("AERO", "trait reroll Bob");

        assertThat(tracker.submit("SomeoneElse", "trait reroll Bob")).isFalse();
    }

    @Test
    void switchingCommandsReplacesThePendingOneRatherThanQueueing() {
        ConfirmationTracker tracker = tracker();
        tracker.submit("AERO", "trait reroll Bob");
        tracker.submit("AERO", "trait reroll Alice");

        // Bob's is gone; only the most recent command is armed.
        assertThat(tracker.submit("AERO", "trait reroll Bob")).isFalse();
        assertThat(tracker.submit("AERO", "trait reroll Bob")).isTrue();
    }

    @Test
    void reportsHowLongTheOperatorHasToConfirm() {
        assertThat(tracker().windowSeconds()).isEqualTo(10);
    }
}
