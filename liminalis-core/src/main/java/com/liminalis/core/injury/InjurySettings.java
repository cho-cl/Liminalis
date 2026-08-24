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
 * @param regenerationSpeedup how much faster wounds fade while a Regeneration effect is
 *                            active, as a multiple of normal time. 1.0 means a regenerating
 *                            player heals at double speed - normal time plus this much again
 */
public record InjurySettings(double injuryThreshold,
                             double injuryChance,
                             double mortalThreshold,
                             double mortalChance,
                             double regenerationSpeedup) {

    public static final InjurySettings DEFAULTS =
            new InjurySettings(0.25, 0.40, 0.60, 0.35, 1.0);
}
