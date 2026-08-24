package com.liminalis.plugin.modifier.capability;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;

/**
 * One attribute change requested by a modifier.
 *
 * <p>Declared as data rather than applied directly so that {@link AttributeSource}
 * implementations never touch a player's attributes themselves. That is what makes it
 * possible to recompute everything from scratch and be certain nothing was left behind.
 *
 * @param attribute the attribute to change
 * @param amount    how much, interpreted according to {@code operation}
 * @param operation add a flat amount, or scale by a fraction
 */
public record AttributeContribution(
        Attribute attribute,
        double amount,
        AttributeModifier.Operation operation) {

    /** A flat change, e.g. +6 max health for three extra hearts. */
    public static AttributeContribution add(Attribute attribute, double amount) {
        return new AttributeContribution(attribute, amount, AttributeModifier.Operation.ADD_NUMBER);
    }

    /**
     * A proportional change, e.g. {@code scale(BLOCK_BREAK_SPEED, 0.30)} for "mines 30%
     * faster" or {@code scale(SCALE, -0.10)} for "10% shorter".
     */
    public static AttributeContribution scale(Attribute attribute, double fraction) {
        return new AttributeContribution(attribute, fraction,
                AttributeModifier.Operation.ADD_SCALAR);
    }
}
