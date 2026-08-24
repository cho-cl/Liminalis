package com.liminalis.core.roll;

/**
 * How generous the first-join roll is.
 *
 * @param secondTraitChance the chance a player gets a second trait as well as their first
 * @param singularityChance the chance that any individual trait slot draws from the
 *                          Singularity pool instead of the ordinary one
 */
public record TraitRollSettings(double secondTraitChance, double singularityChance) {

    public static final TraitRollSettings DEFAULTS = new TraitRollSettings(0.25, 0.03);
}
