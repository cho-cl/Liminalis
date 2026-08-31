package com.liminalis.plugin.ability;

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
 * <p><strong>Levelling is not one of the things it asks for.</strong> Abilities used to
 * declare their own unlock conditions - the Priest counted healing and felled undead, and
 * every ability after it would have invented two counters of its own. It was tailored, and
 * the tailoring is what made it complicated to write, to explain and to answer questions
 * about. Every ability now climbs the same ladder: you level by using it, and level N means
 * powers one through N are yours. Writing an ability is now nothing but writing five powers.
 */
public interface Ability extends Modifier {

    /**
     * The five things this ability can do, in slot order.
     *
     * <p>Slot number and level are the same thing, so a player who knows they are level 3
     * knows that /ability 1, 2 and 3 work without having to look anything up.
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

    /**
     * How high this ability goes.
     *
     * <p>The highest level any of its powers waits for, rather than how many powers it has -
     * an ability with a power that starts unlocked has one more power than it has levels,
     * and counting them would advertise a level nothing is behind.
     */
    default int maxLevel() {
        return powers().stream().mapToInt(Power::unlockedAt).max().orElse(1);
    }

    /** messages.yml key describing what a given level grants. */
    default String levelKey(int level) {
        return "ability." + id() + ".level" + level;
    }
}
