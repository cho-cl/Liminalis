package com.liminalis.plugin.boon;

import com.liminalis.plugin.modifier.ModifierType;
import com.liminalis.plugin.modifier.capability.AttributeContribution;
import com.liminalis.plugin.modifier.capability.AttributeSource;
import com.liminalis.plugin.modifier.capability.Restriction;
import com.liminalis.plugin.trait.TraitTuning;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Objects;

/**
 * Curses: a larger gift than any blessing, paid for with something you would rather keep.
 *
 * <p>The brief was explicit that curses should carry the better upside, and that is what
 * makes the roll interesting - being cursed is not straightforwardly worse than being
 * blessed, it is a trade somebody else made on your behalf.
 *
 * <p>Two shapes of cost appear here. Most are paid in attributes, which is simple and
 * legible. Hollow and Unshod are paid in {@link Restriction}, which takes away something the
 * player can see in their own inventory - and that is a sharper kind of cost, because they
 * have to keep choosing not to wear it.
 */
public final class Curses {

    private Curses() {
    }

    public static List<Boon> all(TraitTuning tuning) {
        return List.of(
                new Hollow(tuning),
                new Unshod(tuning),
                new Brittle(tuning),
                new Swiftbane(tuning),
                new ShallowLungs(tuning));
    }

    /**
     * Three extra hearts, and heavy protection will not stay on you.
     *
     * <p>The canonical example from the brief. The gift is large and permanent; the cost is
     * paid over and over, every time they find a better chestplate and cannot wear it.
     */
    public static final class Hollow implements Boon, AttributeSource, Restriction {

        private final TraitTuning tuning;

        Hollow(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "hollow";
        }

        @Override
        public ModifierType type() {
            return ModifierType.CURSE;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            return List.of(AttributeContribution.add(
                    Attribute.MAX_HEALTH, tuning.get("hollow.health", 6.0)));
        }

        @Override
        public boolean forbidsWearing(Player player, ItemStack armour) {
            int limit = (int) tuning.get("hollow.max-protection-level", 2.0);
            return protectionLevel(armour) > limit;
        }

        private static int protectionLevel(ItemStack armour) {
            ItemMeta meta = armour.getItemMeta();
            if (meta == null) {
                return 0;
            }
            return meta.getEnchantLevel(Enchantment.PROTECTION);
        }
    }

    /** Fast, and barefoot forever. */
    public static final class Unshod implements Boon, AttributeSource, Restriction {

        private final TraitTuning tuning;

        Unshod(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "unshod";
        }

        @Override
        public ModifierType type() {
            return ModifierType.CURSE;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            return List.of(AttributeContribution.scale(
                    Attribute.MOVEMENT_SPEED, tuning.get("unshod.speed", 0.22)));
        }

        @Override
        public boolean forbidsWearing(Player player, ItemStack armour) {
            return armour.getType().name().endsWith("_BOOTS");
        }
    }

    /** Hits far harder than anyone should, and folds far more easily. */
    public static final class Brittle implements Boon, AttributeSource {

        private final TraitTuning tuning;

        Brittle(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "brittle";
        }

        @Override
        public ModifierType type() {
            return ModifierType.CURSE;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            return List.of(
                    AttributeContribution.add(Attribute.ATTACK_DAMAGE,
                            tuning.get("brittle.attack", 4.0)),
                    AttributeContribution.add(Attribute.ARMOR,
                            -tuning.get("brittle.armor-penalty", 6.0)));
        }
    }

    /** Almost impossible to hurt, and almost impossible to get anywhere. */
    public static final class Swiftbane implements Boon, AttributeSource {

        private final TraitTuning tuning;

        Swiftbane(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "swiftbane";
        }

        @Override
        public ModifierType type() {
            return ModifierType.CURSE;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            return List.of(
                    AttributeContribution.add(Attribute.ARMOR,
                            tuning.get("swiftbane.armor", 8.0)),
                    AttributeContribution.scale(Attribute.MOVEMENT_SPEED,
                            -tuning.get("swiftbane.speed-penalty", 0.25)));
        }
    }

    /** Strong, and drowns almost at once. */
    public static final class ShallowLungs implements Boon, AttributeSource {

        private final TraitTuning tuning;

        ShallowLungs(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "shallow_lungs";
        }

        @Override
        public ModifierType type() {
            return ModifierType.CURSE;
        }

        @Override
        public List<AttributeContribution> attributeContributions(Player player) {
            return List.of(
                    AttributeContribution.add(Attribute.ATTACK_DAMAGE,
                            tuning.get("shallow_lungs.attack", 3.0)),
                    AttributeContribution.add(Attribute.OXYGEN_BONUS,
                            -tuning.get("shallow_lungs.oxygen-penalty", 10.0)));
        }
    }
}
