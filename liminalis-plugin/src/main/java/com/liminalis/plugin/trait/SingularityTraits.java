package com.liminalis.plugin.trait;

import com.liminalis.core.roll.TraitTier;
import com.liminalis.plugin.limbo.LimboService;
import com.liminalis.plugin.modifier.capability.Ticking;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Singularity tier: much, much different from the rest.
 *
 * <p>These are not bigger numbers. They change what a player can perceive, or how they have
 * to play, which is what makes them worth being rare.
 */
public final class SingularityTraits {

    private SingularityTraits() {
    }

    public static List<Trait> all(TraitTuning tuning, LimboService limbo) {
        return List.of(new Deathsight(tuning, limbo), new Stillness(tuning));
    }

    /**
     * Sees the dead.
     *
     * <p>Ghosts visit the living world as spectators, which the client renders to nobody.
     * This draws them back into view: soul particles at their position, sent to this player
     * alone. The holder becomes the only person on the server who can tell where someone in
     * Limbo is standing, which makes them the one worth finding when somebody needs rescuing.
     *
     * <p>Deliberately distinct from the Mark of Return, which gives only a sense that a ghost
     * is near. Sight is the rare thing; sensing is what you get for having been there.
     */
    public static final class Deathsight implements Trait, Ticking {

        private final LimboService limbo;
        private final TraitTuning tuning;

        Deathsight(TraitTuning tuning, LimboService limbo) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
            this.limbo = Objects.requireNonNull(limbo, "limbo");
        }

        @Override
        public String id() {
            return "deathsight";
        }

        @Override
        public TraitTier tier() {
            return TraitTier.SINGULARITY;
        }

        @Override
        public void tick(Player player) {
            double range = tuning.get("deathsight.range", 48.0);
            double rangeSquared = range * range;

            for (Player other : player.getServer().getOnlinePlayers()) {
                if (other.getUniqueId().equals(player.getUniqueId())
                        || !limbo.isGhosting(other.getUniqueId())
                        || !other.getWorld().equals(player.getWorld())
                        || other.getLocation().distanceSquared(player.getLocation()) > rangeSquared) {
                    continue;
                }
                Location at = other.getLocation();
                // Sent to this player only - everyone else still sees nothing at all.
                player.spawnParticle(Particle.SOUL, at.clone().add(0, 1.0, 0), 6,
                        0.22, 0.6, 0.22, 0.0);
            }
        }
    }

    /**
     * Heals, but only while perfectly still.
     *
     * <p>Changes how its holder plays rather than what their numbers are: a fight becomes
     * something to disengage from and wait out, and standing motionless in the open becomes a
     * real decision rather than an idle moment.
     */
    public static final class Stillness implements Trait, Ticking {

        /** Shared-loop intervals of stillness before healing begins. Six is three seconds. */
        private static final int INTERVALS_BEFORE_HEALING = 6;

        private final TraitTuning tuning;
        private final Map<UUID, StillnessState> states = new ConcurrentHashMap<>();

        Stillness(TraitTuning tuning) {
            this.tuning = Objects.requireNonNull(tuning, "tuning");
        }

        @Override
        public String id() {
            return "stillness";
        }

        @Override
        public TraitTier tier() {
            return TraitTier.SINGULARITY;
        }

        @Override
        public void tick(Player player) {
            StillnessState state = states.computeIfAbsent(
                    player.getUniqueId(), id -> new StillnessState());
            Location now = player.getLocation();

            if (!state.isSamePlaceAs(now)) {
                state.reset(now);
                return;
            }

            state.intervals++;
            if (state.intervals < INTERVALS_BEFORE_HEALING) {
                return;
            }

            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            double max = maxHealth == null ? 20.0 : maxHealth.getValue();
            if (player.getHealth() >= max) {
                return;
            }

            double heal = tuning.get("stillness.heal-per-half-second", 0.35);
            player.setHealth(Math.min(max, player.getHealth() + heal));
            player.spawnParticle(Particle.SOUL_FIRE_FLAME,
                    now.clone().add(0, 1.0, 0), 2, 0.25, 0.4, 0.25, 0.0);
        }

        @Override
        public void onDetach(Player player) {
            states.remove(player.getUniqueId());
        }

        /** Per-player runtime state. Transient by design - stillness is not worth persisting. */
        private static final class StillnessState {

            private int intervals;
            private int blockX;
            private int blockY;
            private int blockZ;
            private boolean seeded;

            private boolean isSamePlaceAs(Location location) {
                return seeded
                        && blockX == location.getBlockX()
                        && blockY == location.getBlockY()
                        && blockZ == location.getBlockZ();
            }

            private void reset(Location location) {
                seeded = true;
                intervals = 0;
                blockX = location.getBlockX();
                blockY = location.getBlockY();
                blockZ = location.getBlockZ();
            }
        }
    }
}
