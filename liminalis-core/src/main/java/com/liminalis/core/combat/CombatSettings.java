package com.liminalis.core.combat;

/**
 * The tuning knobs for Phase 1's world rules.
 *
 * @param pvpDamageMultiplier     applied to damage one player causes another; 0.5 halves it
 * @param foodHealingMultiplier   applied to natural regeneration and health from eating
 * @param regenerationMultiplier  applied to the Regeneration effect; above 1.0 buffs it
 * @param includeProjectiles      whether arrows and thrown things count as player damage
 * @param includePets             whether a tamed animal attacking counts as its owner's doing
 * @param includeExplosives       whether TNT a player set off counts as their doing
 */
public record CombatSettings(
        double pvpDamageMultiplier,
        double foodHealingMultiplier,
        double regenerationMultiplier,
        boolean includeProjectiles,
        boolean includePets,
        boolean includeExplosives) {

    public static final CombatSettings DEFAULTS =
            new CombatSettings(0.5, 0.5, 1.25, true, true, true);
}
