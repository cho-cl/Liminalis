package com.liminalis.core.injury;

/**
 * A blow, described in the only terms that decide what it leaves behind.
 *
 * @param category   what kind of harm it was, which picks the pool of possible wounds
 * @param finalDamage damage actually taken, <em>after</em> armour and enchantments. This is
 *                    what makes the brief's example work: a netherite sword through weak
 *                    armour maims, and the same blow through full protection does not
 * @param maxHealth  the victim's own maximum health, so severity is judged in proportion to
 *                   what they can survive rather than as a flat number
 */
public record DamageDescriptor(DamageCategory category, double finalDamage, double maxHealth) {
}
