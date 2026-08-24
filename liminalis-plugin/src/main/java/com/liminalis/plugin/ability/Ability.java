package com.liminalis.plugin.ability;

import com.liminalis.core.ability.TierRequirement;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierType;

import java.util.List;

/**
 * One player's ability - the thing the Creator writes for them by hand, on request.
 *
 * <p>Unlike every other modifier, an ability is not rolled and not earned by accident. It is
 * commissioned: a player asks for something, and it gets built. That means this interface is
 * going to be implemented dozens of times over a season by someone working from a Discord
 * message, so it asks for as little as possible.
 *
 * <p>Tiers open through conditions the ability defines for itself. A generic ladder would be
 * cheaper and would feel arbitrary bolted onto a healer, so each ability counts the things it
 * is actually about and says how many of them are enough.
 */
public interface Ability extends Modifier {

    /**
     * What opens each tier.
     *
     * <p>Tier 1 should cost nothing - being handed an ability you cannot use yet reads as a
     * rejection rather than a gift.
     */
    List<TierRequirement> tiers();

    /**
     * The five things this ability can do, in slot order.
     *
     * <p>Slot number and tier are the same thing, so a player who knows they are tier 3 knows
     * that /ability 1, 2 and 3 work without having to look anything up.
     *
     * <p>Five is a deliberate floor as well as a ceiling. An ability with two powers is a
     * perk, and the point of these is that each one is somebody's whole character.
     */
    List<Power> powers();

    /** The power in a given slot, or empty if this ability has nothing there. */
    default java.util.Optional<Power> power(int slot) {
        return powers().stream().filter(p -> p.slot() == slot).findFirst();
    }

    @Override
    default ModifierType type() {
        return ModifierType.ABILITY;
    }

    default int maxTier() {
        return tiers().stream().mapToInt(TierRequirement::tier).max().orElse(1);
    }

    /** messages.yml key describing what a given tier grants. */
    default String tierKey(int tier) {
        return "ability." + id() + ".tier" + tier;
    }
}
