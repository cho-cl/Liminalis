package com.liminalis.plugin.boon;

import com.liminalis.plugin.modifier.ModifierType;
import com.liminalis.plugin.modifier.capability.AttributeContribution;
import com.liminalis.plugin.modifier.capability.AttributeSource;
import com.liminalis.plugin.modifier.capability.HealingRule;
import com.liminalis.plugin.modifier.capability.Restriction;
import com.liminalis.plugin.modifier.capability.Ticking;
import com.liminalis.plugin.trait.TraitTuning;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Objects;

/**
 * Curses: a larger gift than any blessing, paid for with something you would rather keep.
 *
 * <p>The brief was explicit that curses carry the better upside, and that is what makes the
 * roll interesting - being cursed is not straightforwardly worse than being blessed, it is a
 * trade somebody else made on your behalf.
 *
 * <p>Like the blessings, these stopped being numbers. A curse that gave +4 attack and -6
 * armour was a legible trade and a completely forgettable one; a curse that lets you walk into
 * the land of the dead and takes away your bed is a thing a player builds an entire season
 * around. Every one of these rewrites a rule rather than a stat: how you heal, where you can
 * go, what daylight does to you, what hunts you.
 *
 * <p>Hollow is the exception and is kept deliberately. It came from the brief verbatim, and
 * its identity was never the three hearts - it was the armour you find and cannot wear.
 */
public final class Curses {

    private Curses() {
    }

    public static List<Boon> all(TraitTuning tuning) {
        return List.of(
                new Hollow(tuning),
                new Untethered(tuning),
                new Sunless(tuning),
                new Bloodhungry(tuning),
                new Gluttonous(tuning),
                new Marked(tuning));
    }

    /** Shared plumbing: every curse agrees on its type. */
    private abstract static class SimpleCurse implements Boon {

        @Override
        public ModifierType type() {
            return ModifierType.CURSE;
        }
    }

    /**
     * Three extra hearts, and heavy protection will not stay on you.
     *
     * <p>The canonical example from the brief, and the one curse kept from the first roster.
     * The gift is large and permanent; the cost is paid over and over, every time they find a
     * better chestplate and cannot wear it.
     */
    public static final class Hollow extends SimpleCurse implements AttributeSource, Restriction {

        private final TraitTuning tuning;

        Hollow(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "hollow";
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

    /**
     * You can cross into Limbo whenever you like, and you can never go home.
     *
     * <p>The gift is the mirror of what the dead already have: they walk among the living for
     * five minutes at a time, and you walk among them. It makes one person on the server able
     * to check on the dead without spending a Threshold Stone - and able to attempt a rescue
     * on a whim, with the same real chance of being stranded there for good.
     *
     * <p>The cost is that no bed will ever hold you. You always wake at world spawn, however
     * far away you died, because half of you is somewhere else and nowhere is home. Enforced
     * in {@code LimboService}; declared here so the profile screen can explain it.
     */
    public static final class Untethered extends SimpleCurse {

        private final TraitTuning tuning;

        Untethered(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "untethered";
        }

        /** Seconds between crossings, so this cannot be used to ferry the dead out at will. */
        public long cooldownSeconds() {
            return (long) tuning.get("untethered.crossing-cooldown-seconds", 900.0);
        }
    }

    /**
     * You see in the dark and nothing in it looks for you. The sun sets you alight.
     *
     * <p>The largest reshaping of how a player lives in the roster. Permanent night vision and
     * hostile mobs losing their hold on you in darkness makes the night - the dangerous half
     * of Minecraft - yours outright. In exchange the day, which is everybody else's safe half,
     * is lethal.
     *
     * <p>Deliberately survivable: shade, water, a roof and a boat all work, and the fire is
     * short. It is meant to make somebody nocturnal, not to kill them at dawn.
     */
    public static final class Sunless extends SimpleCurse implements Ticking {

        private final TraitTuning tuning;

        Sunless(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "sunless";
        }

        @Override
        public void tick(Player player) {
            // Ambient rather than shown in the HUD: the effect is who they are, not something
            // they drank, and a permanently ticking icon would say otherwise.
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION, 400, 0, true, false, false));

            if (Blessings.Sunfed.inDaylight(player)) {
                burn(player);
            } else {
                hide(player);
            }
        }

        private void burn(Player player) {
            if (player.isInWater() || player.getWorld().hasStorm()) {
                return;
            }
            player.setFireTicks(Math.max(player.getFireTicks(),
                    (int) tuning.get("sunless.burn-ticks", 60.0)));
        }

        private void hide(Player player) {
            double range = tuning.get("sunless.unseen-range", 16.0);
            Location at = player.getLocation();
            if (at.getBlock().getLightLevel() > tuning.get("sunless.dark-below-light", 8.0)) {
                return;
            }
            for (var nearby : player.getNearbyEntities(range, range, range)) {
                if (nearby instanceof Mob mob && player.equals(mob.getTarget())) {
                    mob.setTarget(null);
                }
            }
        }
    }

    /**
     * Killing heals you. Nothing else does.
     *
     * <p>A total rewiring of survival rather than a modifier to it. Food restores no health,
     * Regeneration restores no health, a golden apple restores no health - and every living
     * thing you cut down gives you back a piece of yourself. It turns retreating into the
     * worst possible move and makes a player who is losing look for something to kill.
     *
     * <p>Hunger still works normally, so they can still sprint and still starve; it simply
     * stops being how they get better.
     *
     * <p>A Priest can still heal them, and that is deliberate rather than an oversight. What
     * this refuses is what the <em>world</em> offers - a meal, a potion, time. Another player
     * kneeling down and deciding to spend a cooldown on you is not the world offering
     * anything, and a curse that made its holder impossible to help would be pulling against
     * everything else on this server.
     */
    public static final class Bloodhungry extends SimpleCurse
            implements HealingRule, com.liminalis.plugin.modifier.capability.Slayer {

        private final TraitTuning tuning;

        Bloodhungry(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "bloodhungry";
        }

        /** Every source of healing the world offers, refused. There are no exceptions. */
        @Override
        public double adjustHealing(Player player, RegainReason reason, double amount) {
            return 0.0;
        }

        /**
         * The only thing that mends them.
         *
         * <p>Applied with {@code setHealth} rather than as a heal event on purpose. Going
         * through the normal path would meet this curse's own refusal above and be cancelled,
         * and carving out an exception by reason would leave a hole any other plugin healing
         * with the same reason could walk through.
         */
        @Override
        public void onKill(Player killer, org.bukkit.entity.LivingEntity victim) {
            double max = killer.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0
                    : killer.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (killer.getHealth() >= max) {
                return;
            }
            killer.setHealth(Math.min(max,
                    killer.getHealth() + tuning.get("bloodhungry.heal-per-kill", 4.0)));
            killer.getWorld().spawnParticle(Particle.DUST,
                    killer.getLocation().add(0, 1.0, 0), 8, 0.3, 0.5, 0.3,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 10, 10), 1.2f));
        }
    }

    /**
     * A meal is worth four times what it should be, and you are always hungry.
     *
     * <p>The exact inverse of Bloodhungry, and the pair is deliberate. Where that one takes
     * food away as a way of healing, this makes it the best one there is - four times the
     * halved rate this server hands everyone else, which is twice what an unhalved meal would
     * do. The cost is that the bar it comes out of empties four times as fast, so they are
     * permanently farming, permanently carrying stacks of it, and in real trouble the moment
     * they run out.
     */
    public static final class Gluttonous extends SimpleCurse implements HealingRule, Ticking {

        private final TraitTuning tuning;

        Gluttonous(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "gluttonous";
        }

        @Override
        public double adjustHealing(Player player, RegainReason reason, double amount) {
            return reason == RegainReason.SATIATED || reason == RegainReason.EATING
                    ? amount * tuning.get("gluttonous.food-healing-multiplier", 4.0)
                    : amount;
        }

        /**
         * Burns through the hunger bar.
         *
         * <p>Adds exhaustion rather than removing food directly: exhaustion is the counter
         * vanilla itself drains hunger from, so this accelerates the existing mechanism
         * instead of fighting it, and saturation still buffers a good meal the way it should.
         */
        @Override
        public void tick(Player player) {
            if (player.getFoodLevel() <= 0) {
                return;
            }
            player.setExhaustion(player.getExhaustion()
                    + (float) tuning.get("gluttonous.exhaustion-per-tick", 0.35));
        }
    }

    /**
     * The Singularity gives up its books to you, and comes looking for you.
     *
     * <p>The lore books are the only source of knowledge about anything on this server - how
     * revival works, what the grey is, what the creatures are - and they drop three times in
     * four. For the Marked they drop every single time, which makes one player the reason the
     * server understands anything at all.
     *
     * <p>What it costs is that the waves aim at them. Being Marked means the thing that
     * arrives every half hour arrives near <em>you</em>, far more often than chance, for as
     * long as you play. Read by {@code SingularityService}; declared here so the numbers live
     * with the curse that owns them.
     */
    public static final class Marked extends SimpleCurse {

        private final TraitTuning tuning;

        Marked(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "marked";
        }

        /** How much likelier a wave is to pick this player over anyone else. */
        public double spawnWeight() {
            return tuning.get("marked.spawn-weight", 4.0);
        }
    }
}
