package com.liminalis.core.roll;

/**
 * One id and how likely it is relative to its neighbours.
 *
 * @param id     the modifier id to grant if drawn
 * @param weight relative likelihood; 2.0 is twice as likely as 1.0, and 0 is never
 */
public record WeightedEntry(String id, double weight) {

    public WeightedEntry {
        if (weight < 0) {
            throw new IllegalArgumentException("weight must not be negative: " + id);
        }
    }
}
