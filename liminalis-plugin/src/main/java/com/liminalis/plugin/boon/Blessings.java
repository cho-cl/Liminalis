package com.liminalis.plugin.boon;

import com.liminalis.plugin.modifier.ModifierType;
import com.liminalis.plugin.modifier.capability.DeathBehaviour;
import com.liminalis.plugin.modifier.capability.DamageTaker;
import com.liminalis.plugin.modifier.capability.MortalWard;
import com.liminalis.plugin.modifier.capability.Ticking;
import com.liminalis.plugin.trait.TraitTuning;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Objects;

/**
 * Blessings: a gift with nothing asked in return.
 *
 * <p>These used to be five attribute bonuses - more hearts, more speed, more reach - and the
 * verdict on them was exactly right: they were better traits. The traits already own that
 * space and own eighteen slots of it, so a blessing that is also a number is a rarer version
 * of something the player could have rolled anyway, and rolling one felt like nothing.
 *
 * <p>So none of these are numbers. Every one of them changes what a player <em>can do</em>:
 * walk through fire, die and keep your pack, be maimed and not stay maimed, have a fourth
 * life at all. They are harder to write than a stat and every one of them needed a capability
 * that did not exist - which is the point. A blessing should be the thing you tell people
 * about, not the thing you notice on your armour bar.
 */
public final class Blessings {

    private Blessings() {
    }

    public static List<Boon> all(TraitTuning tuning) {
        return List.of(
                new Emberborn(tuning),
                new ThriceBorn(tuning),
                new Unbroken(),
                new Soulbound(),
                new Sunfed(tuning),
                new Hearthbound(tuning));
    }

    /** Shared plumbing: every blessing agrees on its type. */
    private abstract static class SimpleBlessing implements Boon {

        @Override
        public ModifierType type() {
            return ModifierType.BLESSING;
        }
    }

    /**
     * Fire cannot hurt you.
     *
     * <p>Not resistance - immunity, and to the whole family at once: flames, lava, magma
     * blocks, campfires, a blaze. The blow is cancelled outright rather than reduced to zero,
     * so there is no knockback, no red flash and no wound either; the fire simply does not
     * apply to you.
     *
     * <p>Worth far more than the damage it saves. It is a licence to swim in lava for
     * ancient debris, to walk into a burning build, and to stop carrying fire resistance
     * potions forever.
     */
    public static final class Emberborn extends SimpleBlessing implements DamageTaker, Ticking {

        private final TraitTuning tuning;

        Emberborn(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "emberborn";
        }

        @Override
        public double adjustIncoming(Player player, EntityDamageEvent event, double damage) {
            return switch (event.getCause()) {
                case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE, MELTING -> 0.0;
                default -> damage;
            };
        }

        /**
         * Puts them out.
         *
         * <p>Refusing the damage is not enough on its own: a player who has caught fire still
         * burns visibly, still cannot sleep, and still loses the arrows and food they are
         * carrying to the flames. Being Emberborn should mean fire does not take hold.
         */
        @Override
        public void tick(Player player) {
            if (player.getFireTicks() <= 0) {
                return;
            }
            player.setFireTicks(0);
            if (tuning.get("emberborn.show-embers", 1.0) > 0) {
                player.getWorld().spawnParticle(Particle.SMALL_FLAME,
                        player.getLocation().add(0, 1.0, 0), 6, 0.3, 0.5, 0.3, 0.01);
            }
        }
    }

    /**
     * A fourth life.
     *
     * <p>The only boon in the plugin that is a one-time change to a saved number rather than
     * an effect recomputed while you are online, because lives are a counter that goes down.
     * It is granted where the blessing is assigned - see {@code BoonAssignment} - and never on
     * attach, which happens on every login and would hand out a life per reconnect.
     *
     * <p>On a server built around having exactly three, a fourth is the largest single thing
     * anyone can be given.
     */
    public static final class ThriceBorn extends SimpleBlessing implements LifeGranting {

        private final TraitTuning tuning;

        ThriceBorn(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "thrice_born";
        }

        @Override
        public int extraLives() {
            return (int) tuning.get("thrice_born.extra-lives", 1.0);
        }
    }

    /**
     * You do not stay maimed.
     *
     * <p>A blow that would take an arm leaves an ordinary wound instead - one that fades on
     * its own like any other. Mortal wounds are the only permanent harm in the game and the
     * only thing a player would spend a life to be rid of, so being exempt from them is worth
     * more than any stat could be.
     */
    public static final class Unbroken extends SimpleBlessing implements MortalWard {

        @Override
        public String id() {
            return "unbroken";
        }
    }

    /**
     * You keep what you were carrying.
     *
     * <p>Death already costs a life, which is the expensive part. Losing everything you had on
     * you on top of it is a second penalty entirely - and the one that actually stops people
     * going anywhere interesting. This removes it.
     */
    public static final class Soulbound extends SimpleBlessing implements DeathBehaviour {

        @Override
        public String id() {
            return "soulbound";
        }

        @Override
        public boolean keepsInventory(Player player) {
            return true;
        }
    }

    /**
     * Daylight feeds you.
     *
     * <p>Under open sky in daylight your hunger does not drop at all. Worth having precisely
     * because this server halved what food gives back: hunger is a real resource here, and
     * not spending it is not a small thing.
     *
     * <p>Open sky specifically, so it is a reason to be out in the world rather than a passive
     * bonus. Mining all day gets you nothing.
     */
    public static final class Sunfed extends SimpleBlessing implements Ticking {

        private final TraitTuning tuning;

        Sunfed(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "sunfed";
        }

        @Override
        public void tick(Player player) {
            if (!inDaylight(player)) {
                return;
            }
            // Exhaustion is the counter that eventually drops a haunch. Clearing it stops
            // hunger falling without ever adding food they did not eat, so this can never
            // become a way to survive on sunlight alone.
            player.setExhaustion(0f);
            player.setSaturation(Math.max(player.getSaturation(),
                    (float) tuning.get("sunfed.minimum-saturation", 5.0)));
        }

        static boolean inDaylight(Player player) {
            long time = player.getWorld().getTime();
            boolean day = time < 12_300 || time > 23_850;
            return day && player.getWorld().getEnvironment()
                        == org.bukkit.World.Environment.NORMAL
                    && player.getLocation().getBlock().getLightFromSky() >= 15;
        }
    }

    /**
     * People mend near you.
     *
     * <p>The only boon that does nothing for the person carrying it. Everyone else within
     * range slowly heals, and they do not have to know why - which on a server whose whole
     * design pushes toward cooperation is the most on-brief gift in the roster.
     *
     * <p>Deliberately slow. It is a reason to camp together and to bring the wounded person
     * with you, not a replacement for the Priest.
     */
    public static final class Hearthbound extends SimpleBlessing implements Ticking {

        /** Shared-loop intervals between pulses. Four is two seconds. */
        private static final int INTERVALS_BETWEEN_PULSES = 4;

        private final TraitTuning tuning;
        private final java.util.Map<java.util.UUID, Integer> sincePulse =
                new java.util.concurrent.ConcurrentHashMap<>();

        Hearthbound(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "hearthbound";
        }

        @Override
        public void tick(Player player) {
            int waited = sincePulse.merge(player.getUniqueId(), 1, Integer::sum);
            if (waited < INTERVALS_BETWEEN_PULSES) {
                return;
            }
            sincePulse.put(player.getUniqueId(), 0);

            double range = tuning.get("hearthbound.range", 10.0);
            int duration = (int) tuning.get("hearthbound.effect-ticks", 60.0);
            boolean helped = false;

            for (Player nearby : player.getWorld().getPlayers()) {
                if (nearby.equals(player)
                        || nearby.getLocation().distanceSquared(player.getLocation())
                            > range * range) {
                    continue;
                }
                // Applied as a short Regeneration rather than as raw health, so it obeys
                // every other rule about healing on this server instead of going around them.
                nearby.addPotionEffect(new PotionEffect(
                        PotionEffectType.REGENERATION, duration, 0, true, false, false));
                helped = true;
            }
            if (helped) {
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                        player.getLocation().add(0, 1.0, 0), 3, 0.4, 0.5, 0.4, 0.0);
            }
        }

        @Override
        public void onDetach(Player player) {
            sincePulse.remove(player.getUniqueId());
        }
    }
}
