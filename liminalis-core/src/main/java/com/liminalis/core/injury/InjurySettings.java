package com.liminalis.core.injury;

/**
 * When a blow wounds, and how likely it is to.
 *
 * <p>Thresholds are fractions of the victim's maximum health, not flat damage.
 *
 * @param injuryThreshold fraction of max health at or above which a blow may injure
 * @param injuryChance    the chance it does
 * @param mortalThreshold fraction of max health at or above which a blow may maim
 * @param mortalChance    the chance it does
 */
public record InjurySettings(double injuryThreshold,
                             double injuryChance,
                             double mortalThreshold,
                             double mortalChance) {

    public static final InjurySettings DEFAULTS = new InjurySettings(0.25, 0.40, 0.60, 0.35);
}
