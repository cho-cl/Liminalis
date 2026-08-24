package com.liminalis.core.injury;

import java.util.Objects;
import java.util.Random;

/**
 * Decides whether a blow left a mark, and how bad it was.
 *
 * <p>Severity is judged on damage after armour, as a fraction of the victim's own maximum
 * health. Both halves of that carry weight. Post-armour is what makes the brief's example
 * work - a netherite sword through weak armour costs an arm, and the same blow through full
 * protection does not. Proportional to max health is what keeps it fair for a player carrying
 * Ironblood, whose extra hearts should not make them harder to wound relative to what they
 * can actually survive.
 */
public final class InjuryRules {

    private InjuryRules() {
    }

    public static InjurySeverity classify(DamageDescriptor damage,
                                          InjurySettings settings,
                                          Random random) {
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(random, "random");

        // Guards a divide-by-zero that would otherwise turn every scratch into a lost arm.
        if (damage.maxHealth() <= 0 || damage.finalDamage() <= 0) {
            return InjurySeverity.NONE;
        }

        double severity = damage.finalDamage() / damage.maxHealth();

        if (severity >= settings.mortalThreshold()
                && random.nextDouble() < settings.mortalChance()) {
            return InjurySeverity.MORTAL_WOUND;
        }

        // A massive blow that failed its mortal roll still counts as a large one. Without
        // this, the most violent hits in the game would be the least likely to leave any
        // mark at all, because missing the mortal roll would mean nothing happened.
        if (severity >= settings.injuryThreshold()
                && random.nextDouble() < settings.injuryChance()) {
            return InjurySeverity.INJURY;
        }

        return InjurySeverity.NONE;
    }
}
