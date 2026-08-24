package com.liminalis.core.roll;

/**
 * One entry in a roll table.
 *
 * <p>Deliberately just an id and a number rather than the trait itself, so the roll logic
 * stays in core and knows nothing about what a trait actually does.
 *
 * @param id     the modifier id to grant if drawn
 * @param tier   which pool this belongs to
 * @param weight relative likelihood within its tier; 2.0 is twice as likely as 1.0
 */
public record RollCandidate(String id, TraitTier tier, double weight) {

    public RollCandidate {
        if (weight < 0) {
            throw new IllegalArgumentException("weight must not be negative: " + id);
        }
    }
}
