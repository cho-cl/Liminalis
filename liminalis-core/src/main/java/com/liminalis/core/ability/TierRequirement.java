package com.liminalis.core.ability;

/**
 * What a player has to do to reach one tier of their ability.
 *
 * <p>Deliberately just a counter and a number. Every ability defines its own counters and
 * increments them from its own code, so the Priest counts healing and felled undead while
 * something else counts depth reached or nights survived - and this arithmetic does not have
 * to know the difference.
 *
 * @param tier      the tier this opens, counting from 1
 * @param counterKey the counter it watches, namespaced by ability
 * @param required   the value that opens it; 0 for a tier granted with the ability itself
 */
public record TierRequirement(int tier, String counterKey, int required) {
}
