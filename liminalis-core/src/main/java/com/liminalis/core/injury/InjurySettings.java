package com.liminalis.core.injury;

/**
 * When a blow wounds, how likely it is to, and what mends it afterwards.
 *
 * <p>Thresholds are fractions of the victim's maximum health, not flat damage.
 *
 * <p><strong>On the defaults.</strong> Wounds are meant to be an ordinary part of surviving -
 * walk out of a real fight and you should be carrying something - without being constant. So
 * the ordinary threshold sits at roughly a heart and a half of damage <em>after</em> armour,
 * and rather more than half of the blows past it leave a mark. Mortal wounds are the
 * opposite: they are permanent until a life is spent, so they stay pinned to blows that took
 * more than half of everything you had in one hit. That gap between the two thresholds is the
 * whole design - hurt often, maimed almost never.
 *
 * <p>The two healing figures are what keeps that survivable. Wounds still fade on their own,
 * but a player who does not want to wait has something to spend: a potion of Healing mends
 * one outright, and sitting under Regeneration works through them steadily. Neither touches a
 * mortal wound - those still cost a life, or a Priest.
 *
 * @param injuryThreshold fraction of max health at or above which a blow may injure
 * @param injuryChance    the chance it does
 * @param mortalThreshold fraction of max health at or above which a blow may maim
 * @param mortalChance    the chance it does
 * @param instantHealthCures  ordinary wounds mended by a potion of Healing, whatever its
 *                            strength. Healing II restores more health and mends no more
 * @param regenerationCureSeconds seconds of an active Regeneration effect per ordinary wound
 *                                mended. Zero disables it
 */
public record InjurySettings(double injuryThreshold,
                             double injuryChance,
                             double mortalThreshold,
                             double mortalChance,
                             int instantHealthCures,
                             double regenerationCureSeconds) {

    public static final InjurySettings DEFAULTS =
            new InjurySettings(0.15, 0.55, 0.60, 0.30, 1, 10.0);
}
