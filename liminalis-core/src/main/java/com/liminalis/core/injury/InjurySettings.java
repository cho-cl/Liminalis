package com.liminalis.core.injury;

/**
 * When a blow wounds, and how likely it is to.
 *
 * <p>Thresholds are fractions of the victim's maximum health, not flat damage.
 *
 * <p><strong>On the defaults.</strong> Wounds are meant to be an ordinary part of surviving,
 * not a rare event - walk out of a real fight and you should be carrying something. So the
 * ordinary threshold sits at roughly a heart and a quarter of damage <em>after</em> armour,
 * and most blows past it leave a mark. Mortal wounds are the opposite: they are permanent
 * until a life is spent, so they stay pinned to blows that took more than half of everything
 * you had in one hit. That gap between the two thresholds is the whole design - being hurt
 * often, and being maimed almost never.
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
            new InjurySettings(0.12, 0.70, 0.60, 0.30, 1.0);
}
