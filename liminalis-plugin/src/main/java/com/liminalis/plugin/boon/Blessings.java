package com.liminalis.plugin.boon;

import com.liminalis.plugin.modifier.ModifierType;
import com.liminalis.plugin.modifier.capability.AttributeContribution;
import com.liminalis.plugin.modifier.capability.AttributeSource;
import com.liminalis.plugin.trait.TraitTuning;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.function.DoubleFunction;

/**
 * Blessings: a straight upside with nothing asked in return.
 *
 * <p>Deliberately modest. A blessing is the consolation prize of the roster - the thing that
 * makes fifteen percent of players quietly better off. The memorable gifts belong to the
 * curses, which have to be worth paying for.
 *
 * <p>Each of these is a single attribute change, so they are declared as data rather than
 * written out as a class apiece. Anything needing real behaviour would get its own class,
 * the way the traits do.
 */
public final class Blessings {

    private Blessings() {
    }

    public static List<Boon> all(TraitTuning tuning) {
        return List.of(
                new SimpleBlessing("ironblood", tuning, "ironblood.health", 6.0,
                        amount -> AttributeContribution.add(Attribute.MAX_HEALTH, amount)),
                new SimpleBlessing("far_wanderer", tuning, "far_wanderer.speed", 0.12,
                        amount -> AttributeContribution.scale(Attribute.MOVEMENT_SPEED, amount)),
                new SimpleBlessing("steady_hand", tuning, "steady_hand.attack", 1.5,
                        amount -> AttributeContribution.add(Attribute.ATTACK_DAMAGE, amount)),
                new SimpleBlessing("thickskinned", tuning, "thickskinned.toughness", 3.0,
                        amount -> AttributeContribution.add(Attribute.ARMOR_TOUGHNESS, amount)),
                new SimpleBlessing("long_arms", tuning, "long_arms.reach", 1.0,
                        amount -> AttributeContribution.add(
                                Attribute.ENTITY_INTERACTION_RANGE, amount)));
    }

    /** A blessing that is one attribute change and nothing else. */
    private record SimpleBlessing(String id,
                                  TraitTuning tuning,
                                  String tuningKey,
                                  double fallback,
                                  DoubleFunction<AttributeContribution> shape)
            implements Boon, AttributeSource {

        private SimpleBlessing {
            Objects.requireNonNull(tuning, "tuning");
            Objects.requireNonNull(shape, "shape");
        }

        @Override
        public ModifierType type() {
            return ModifierType.BLESSING;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            // Read at the point of use, so /liminalis reload rebalances a live server.
            return List.of(shape.apply(tuning.get(tuningKey, fallback)));
        }
    }
}
