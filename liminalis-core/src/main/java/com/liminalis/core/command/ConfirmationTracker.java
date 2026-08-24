package com.liminalis.core.command;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Requires destructive commands to be issued twice within a short window.
 *
 * <p>Guards against the single most likely admin accident on this server: tab-completing onto
 * the wrong name and erasing somebody's identity in one keystroke. Everything here is keyed by
 * both operator and the exact command text, so a confirmation can only ever approve the thing
 * it was given for.
 *
 * <p>Takes its clock as a parameter so the expiry behaviour is testable without sleeping.
 */
public final class ConfirmationTracker {

    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public ConfirmationTracker(long windowMillis, LongSupplier clock) {
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /**
     * Records an attempt to run a destructive command.
     *
     * @param operator who is running it
     * @param command  a stable description of exactly what will happen, including the target
     * @return true if this attempt confirms a matching one made inside the window, in which
     *         case the caller should go ahead; false if the caller should stop and ask the
     *         operator to repeat the command
     */
    public boolean submit(String operator, String command) {
        long now = clock.getAsLong();
        Pending existing = pending.get(operator);

        boolean confirms = existing != null
                && existing.command.equals(command)
                && now <= existing.expiresAt;

        if (confirms) {
            // Consume it. Leaving it armed would let a third, accidental run go straight
            // through with no prompt at all.
            pending.remove(operator);
            return true;
        }

        pending.put(operator, new Pending(command, now + windowMillis));
        return false;
    }

    /** How long an operator has to repeat the command, for use in the prompt text. */
    public long windowSeconds() {
        return windowMillis / 1000L;
    }

    private record Pending(String command, long expiresAt) {
    }
}
