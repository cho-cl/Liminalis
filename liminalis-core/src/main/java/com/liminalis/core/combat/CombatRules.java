package com.liminalis.core.combat;

/**
 * Applies the world's combat and healing tuning.
 *
 * <p>Pure arithmetic over an already-classified source. Working out <em>which</em> player
 * caused a piece of damage is the server's job; deciding what that means is this one's.
 */
public final class CombatRules {

    private CombatRules() {
    }

    /**
     * Scales damage one player caused another, leaving everything else untouched.
     *
     * @param damage the incoming damage, before armour and other reductions
     */
    public static double adjustPlayerDamage(double damage,
                                            PlayerDamageSource source,
                                            CombatSettings settings) {
        if (!countsAsPlayerDamage(source, settings)) {
            return damage;
        }
        return atLeastZero(damage * settings.pvpDamageMultiplier());
    }

    /** Scales healing according to where it came from. */
    public static double adjustHealing(double amount,
                                       HealingKind kind,
                                       CombatSettings settings) {
        double multiplier = switch (kind) {
            case FOOD -> settings.foodHealingMultiplier();
            case REGENERATION -> settings.regenerationMultiplier();
            case OTHER -> 1.0;
        };
        return atLeastZero(amount * multiplier);
    }

    /**
     * Whether this damage should be treated as one player hurting another.
     *
     * <p>Indirect sources are configurable because they are genuinely arguable - a wolf that
     * defends its owner is not quite the same as its owner swinging an axe - but they default
     * to counting, since excluding them would leave an obvious way around the rule.
     */
    private static boolean countsAsPlayerDamage(PlayerDamageSource source,
                                                CombatSettings settings) {
        return switch (source) {
            case DIRECT -> true;
            case PROJECTILE -> settings.includeProjectiles();
            case PET -> settings.includePets();
            case EXPLOSIVE -> settings.includeExplosives();
            case NONE -> false;
        };
    }

    /**
     * Negative damage heals in Bukkit and negative healing hurts, so a misconfigured
     * multiplier must never be passed straight through.
     */
    private static double atLeastZero(double value) {
        return Math.max(0.0, value);
    }
}
